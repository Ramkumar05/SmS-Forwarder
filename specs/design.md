# Design

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
