# Design

The flow and components immediately below describe the implemented V1/V1.1 design. The V1.2 section at the end supersedes conflicting behavior for the next release.

## Flow
Incoming SMS -> SmsReceiver (manifest-registered, SMS_RECEIVED) -> parse and stitch multipart parts -> compute dedupeKey -> ONE Room transaction: insert forward_log row (PENDING, or SKIPPED_LOOP) and, for PENDING, a pending_forward row with the full body -> if the forward_log insert reported a duplicate, stop -> enqueue unique ForwardWorker with ONLY the dedupeKey as input -> worker loads the body from pending_forward, marks SENDING, sends via SmsManager with a sentIntent -> on RESULT_OK: mark SENT and delete the pending_forward row; on failure: retry, then FAILED and delete the pending_forward row.

## Components
- SmsReceiver: BroadcastReceiver. Uses Telephony.Sms.Intents.getMessagesFromIntent, groups parts by sender, builds one body. Uses goAsync() to finish the database transaction, then enqueues work and returns. No other work happens here.
- DedupeKey: SHA-256(normalizedSender + "|" + SmsMessage.getTimestampMillis() + "|" + body). Never uses local arrival time.
- ForwardPolicy: interface decide(message, settings) -> Forward | Skip(reason). V1 implementation: forward all except the loop case.
- ForwardWorker (CoroutineWorker): input Data contains only the dedupeKey, never the body. Loads the body from pending_forward. If the log row is already SENT, returns success without sending. Otherwise marks SENDING, sends, and updates status. Returns retry() on a reported failure until attempts are exhausted, then marks FAILED. A row found stuck in SENDING after a restart is retried (at-least-once delivery).
- Scheduling: enqueueUniqueWork("fwd-" + dedupeKey, ExistingWorkPolicy.KEEP, request). Instant mode uses expedited work with a normal fallback. Delayed modes use a normal OneTimeWorkRequest with initialDelay (expedited work cannot be delayed). Backoff is exponential, 30 seconds base.
- SmsSender: wraps SmsManager (use the context-based getSystemService form on newer Android) behind an interface so it can be faked in tests. Handles divideMessage and sendMultipartTextMessage, with a sentIntent. Reports success only on RESULT_OK.
- NumberNormalizer: strips spaces and punctuation, applies the +91 default, compares numbers by their last 10 digits for the loop check.
- MessageFormatter: builds the "FWD from <sender>: <body>" text.
- ForwardLogDao / ForwardLogEntity (Room): table forward_log with the fields in R6 plus dedupeKey (UNIQUE index) and status (PENDING, SENDING, SENT, FAILED, SKIPPED_LOOP). Inserts use OnConflictStrategy.IGNORE; a -1 result means duplicate. UI query: rows where receivedAt >= now - window, newest first, exposed as Flow.
- PendingForwardDao / PendingForwardEntity (Room): table pending_forward(dedupeKey PRIMARY KEY, sender, smsTimestamp, body, createdAt). Holds the full body only until a terminal status.
- SettingsRepository (DataStore): targetNumber, forwardingEnabled, sendDelay.
- RetentionWorker: periodic daily work that deletes forward_log rows older than 7 days and pending_forward rows older than 24 hours.
- HealthChecker: reports permission state and battery-optimization exemption for the status line.

## UI (Compose)
- MainScreen: header (target, switch, status line), window selector, log table (LazyColumn rows, one line per message, TextOverflow.Ellipsis), empty state.
- SettingsScreen: target number field with validation, delay selector, Clear log button.
- OnboardingScreen: explanation plus permission requests, restricted-settings guidance.
- Time window is recomputed on a ticking clock (every 15 seconds) so old rows drop out of the 5-minute view without new events.

## Edge cases the implementation must handle
- Forwarding disabled or target missing when an SMS arrives: log nothing, forward nothing.
- Permission revoked at send time: mark FAILED with reason, show it in the status line.
- Empty or very long bodies, emoji and non-Latin text (Unicode SMS uses shorter segments).
- The same SMS delivered twice by the system: the idempotency key prevents a double forward.
- Clear log removes the log and pending body rows together and cancels their queued work. It also erases deduplication history, so a later redelivery of the same SMS may forward again.
- Phone reboot with pending work.
- Delivery guarantee: duplicate broadcasts are deduped, but a phone crash between the SMS being sent and SENT being recorded leaves a SENDING row. That row is retried after restart (at-least-once); a rare second send is accepted over a lost message.
- Very long message bodies must never reach WorkManager Data (size limit and privacy rule).

## Testing strategy
- Unit tests: multipart stitching, NumberNormalizer, loop check, MessageFormatter, retry/status transitions, time-window filtering, idempotency key.
- Room tests with an in-memory database.
- Worker tests with TestListenableWorkerBuilder and a fake SmsSender.
- Manual: emulator SMS injection with `adb emu sms send <sender> <text>`, then real-device verification per section 7 of the kit.
- Dedupe and privacy tests: same broadcast twice -> one forward; two identical bodies with different SMS timestamps -> two forwards; worker re-run after SENT -> no second send; concurrent inserts of the same key -> one row; pending_forward row deleted after SENT and after final FAILED; body never present in WorkManager input Data; stale pending_forward rows purged after 24 hours; forward_log never holds more than 100 characters of body.

## V1.1 cloud archive (historical)

After a confirmed SMS send, `ForwardRepository.complete` changes the log to SENT, copies the original body to `upload_outbox`, and removes `pending_forward` in one Room transaction. `ArchiveWorker` sends only the dedupe key through WorkManager data, loads the body from Room, and posts to the private Cloudflare API with the user-entered write token. A 200 or 201 response deletes the outbox row. Network errors, timeouts, rate limits, and server errors retry; client errors leave the row for inspection and the periodic sweep. The API's idempotency key makes repeated posts safe. The upload result does not change the SMS send status.

`MIGRATION_1_2` adds the outbox table without changing V1 log or pending rows. A periodic sweep and a startup sweep re-enqueue stored uploads after process death or reboot. Settings shows the number of waiting uploads and lets the user clear all local rows. Clearing the log cancels pending forward and upload work for those rows. The write token is saved in app-private DataStore; Android backup remains disabled.

## V1.2 independent routes

### Receive and route

On each newly received SMS, parse multipart content once and compute the existing deterministic idempotency key. Read one saved settings snapshot and evaluate two independent decisions: `forwardingEnabled && matches(forwardMode, forwardKeywords, body)` and `webEnabled && matches(webMode, webKeywords, body)`. Keyword matching uses a comma-separated list, trimmed nonempty entries, case-insensitive body substring comparison, and **any** matching entry. The sender never participates in keyword matching. If both decisions are false, return without persisting the message. Keep the receiver's work short: it selects routes, makes a cheap connectivity check for a web match, persists only work that is eligible now, and enqueues workers. It does not perform the HTTP call.

| Forwarding decision | Web decision | Action |
|---|---|---|
| No | No | Ignore without a new local row. |
| Yes | No | Use the existing forward work and forwarding log. |
| No | Yes | Attempt one API call; do not create a forwarding log row. |
| Yes | Yes | Schedule both independently; neither waits for the other. |

The forwarding loop check occurs inside the forwarding path only. A message from the target number may be archived if the web path matches. The web path may start before a delayed forward and may succeed even if SMS sending later fails. It posts the **original** SMS body, not the formatted `FWD from ...` text.

### Forwarding path

Keep the existing pending-forward transaction, unique work, delay, three-attempt SMS retry, and seven-day log retention for messages matching the forwarding route. Messages outside that route create no forwarding log or work. When Settings saves forwarding as Off, cancel queued forward work and retries, remove their pending bodies, and change unsent log rows to `CANCELLED`. Work already actively sending may finish; cancellation cannot retract an SMS submitted to the carrier. Do not alter past SENT rows. The main log can display CANCELLED alongside other forwarding statuses.

### Web path

Keep the Cloudflare endpoint and bearer write-token contract. Use the deterministic key so duplicate Android delivery does not duplicate a server row. If connectivity is absent when the SMS arrives, create no web work or stored body. Otherwise, hold the matched body only in app-private transient storage and enqueue immediate work containing **only** its key. The worker rechecks that the web route is still On, drops work older than five minutes or work that now lacks connectivity, and atomically claims its one allowed attempt before opening the connection. A restarted worker must not send a claimed attempt again; losing a message during a crash is acceptable under this best-effort policy. HTTP 200/201 completes it; any other response or transport failure discards it. Delete the transient body after success, failure, cancellation, or startup cleanup. Do not apply a network constraint, WorkManager retry, periodic sweep, or reboot replay to web work. A worker that has already begun an HTTP request may finish after the switch is turned Off; the server cannot retract a request already sent. No web-only result is inserted in the forwarding log.

On V1.1-to-V1.2 upgrade, delete queued V1.1 upload rows and their work without POSTing them. Keep forwarding log and pending SMS sends. Startup cleanup removes orphaned web transient rows; it never re-enqueues them. Saving web archiving as Off cancels pending web work and deletes transient bodies. Messages already in Cloudflare remain there. Web failure never changes the SMS-forward status.

### Settings and main screen

Store both route switches, modes, and separate raw keyword fields in DataStore, together with the existing forwarding target/delay and web write token. Default new match modes to All SMS; preserve the old forwarding switch and token, but default the new web switch to Off. Validate enabled routes before saving and apply both route configurations in one settings update. A disabled route retains its details so it can be re-enabled later. Settings has two separate sections, each with a switch, All SMS/Matching keywords choice, and a keyword field visible only in Matching keywords mode. The forwarding section also has target/delay; the web section has a masked token and a one-attempt delivery note. The only route switches are in Settings.

The main screen shows two status rows such as `SMS forwarding: On · Matching keywords` and `Web archive: On · All SMS`, with an action to open Settings. It labels the existing list **Forwarding activity**. It does not show V1.1's `Uploads waiting` counter. When both routes are Off, a newly received SMS adds no rows; historical forwarding rows remain visible until normal retention or Clear log.

### V1.2 verification

Test body-only, any-keyword, case-insensitive matching and all four route combinations. Test forwarding loop handling independently of the web route. Test web-only messages, delayed/failed forwards with web success, absent connectivity, HTTP failure, disabling during pending work, five-minute expiry, no startup replay, and cancellation of pending SMS sends. Test V1.1-to-V1.2 Room/settings migration without losing forwarding history or pending SMS work. Check that no full body or token reaches WorkManager data, logs, or notifications, and that neither web-only nor filter-rejected messages enter the forwarding log.

## V1.3: Activity and save feedback

The V1.2 `Forwarding activity` description above is historical. Settings shows a Material snackbar after a successful save, a failed save, or validation errors; it is anchored outside the scrollable form. Success is emitted only after the repository and route cleanup finish.

Add `web_log` in Room version 4. It holds the deterministic key, receive time, sender, a 100-character snippet, status, and update time; it never holds a full body or token. `MIGRATION_3_4` creates the table without changing forwarding or transient delivery data. `WebDeliveryRepository` inserts a `QUEUED` log alongside transient work in one transaction, or `SKIPPED` metadata without a transient body when offline. Claiming work changes status to `UPLOADING` and removes the body before the HTTP call. A 2xx response becomes `COMPLETED`; a non-2xx response or transport exception becomes `FAILED`; interrupted attempts become `UNCONFIRMED`. Pending work discarded on disable, expiry, or startup becomes `SKIPPED`. Startup marks interrupted `UPLOADING` entries `UNCONFIRMED` without replay. Clear log and seven-day retention include `web_log`.

The home ViewModel combines recent `forward_log` and `web_log` flows by deterministic key and orders rows by receive time. A message selected by either route appears once, with a dash for the unselected route. Existing forwarding history shows a dash in the Web column because previous outcomes cannot be reconstructed. The table displays Time, From, Message, SMS, and Web columns. Its web statuses describe the one-attempt outcome, not a retry queue.

## V1.4 app source metadata

DataStore adds app_source_phone_number and app_source_name. Settings/ViewModel/route configuration carry both strings, with blank defaults. WebRouteProcessor passes an immutable snapshot to WebDeliveryRepository, which stores it in web_delivery. Room schema 5 adds appSourcePhoneNumber/appSourceName with empty defaults via migration 4→5. The uploader includes nonblank snapshot fields and retains the original sender/body/time. Only the server-upload identity hashes the legacy key plus length-prefixed source fields when either is supplied; local activity/work keys and SMS identity are unchanged so combined rows still join correctly. Source fields do not enter WorkManager Data or full-message logs. The backend migration/optional contract must be deployed before the forwarding update is installed.

## V1.5: Offline web upload queue

Supersedes the V1.2 web path, the V1.3 claim-before-HTTP behavior and "no network constraint / no reboot replay".

**Receive.** `WebRouteProcessor` no longer checks connectivity. For a web match, `WebDeliveryRepository.recordIfNew` inserts the `web_log` row (QUEUED) and the `web_delivery` row in one transaction, then applies `WebQueuePolicy` inside the same transaction: rows older than 7 days or beyond the newest 50 are deleted and their log rows set to SKIPPED. The receiver then enqueues the single unique `web-drain` work and returns. The work request has a `NetworkType.CONNECTED` constraint and carries no input data.

**Pure policy.** `WebQueuePolicy.evictions(entries, now)` takes `(key, createdAt)` pairs and returns the keys to drop: expired ones (age >= 7 days) and then everything beyond the newest 50 (ordered by createdAt, then key). Unit tested.

**Drain worker.** `WebDeliveryWorker` (unique work `web-drain`, ExistingWorkPolicy.KEEP, exponential backoff) first purges expired rows, then loops: if Web archive is Off, discard the whole queue and finish; if the token is blank, finish leaving rows; otherwise take the oldest row, mark its log UPLOADING, upload with the token loaded from DataStore, then: 2xx -> delete row, log COMPLETED; other HTTP code -> delete row, log FAILED; `IOException` -> log back to QUEUED, return `Result.retry()`; cancellation -> log back to QUEUED and rethrow. Other unexpected exceptions mark the message FAILED and drop it. The loop ends when the queue is empty. Because uploads can repeat after an interruption, the existing idempotency key protects the server from duplicates.

**Restart/reboot.** The queue lives in Room and the constrained work is persisted by WorkManager. `ForwardApplication.onCreate` resets interrupted UPLOADING rows to QUEUED, purges expired rows, and enqueues a drain when the queue is non-empty. There is no per-process session check any more; the `sessionId` column is kept only for schema compatibility. `WebExpiryWorker` remains as a no-op so any work enqueued by V1.4 completes harmlessly.

**Switching Off / Clear log.** `RouteSettingsCoordinator` calls `discardAll` and cancels `web-drain` when Web is saved Off. `ForwardRepository.clearLog` no longer touches `web_delivery`; it deletes finished `web_log` rows but keeps QUEUED/UPLOADING ones so the activity list and queue stay consistent.

**UI.** `MainActivity` hosts a Material 3 `NavigationBar` with Home, Queue, Settings. `QueueViewModel` observes `WebDeliveryDao.observeQueue()`, a projection of key, sender, timestamps and `substr(body,1,100)`, so the UI never receives a full body. `SettingsScreen` has no Back button when shown as a tab.

**Verification.** Unit tests for `WebQueuePolicy`. Android tests: queued while offline, ordered drain, retry on `IOException`, drop on HTTP error, 50 cap, 7-day expiry, Off deletes queue, Clear log keeps queue, snapshot of app source values, no body in WorkManager data. The test-only fake uploader replaces HTTP.
