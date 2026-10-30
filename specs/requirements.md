# Requirements

## Goal
When an SMS arrives on this phone, independently decide whether to forward it to a configured number and whether to send it to the private web archive. Show forwarding activity in the app.

## Version scope

R1–R11 describe the V1 baseline and the V1.1 section records the first cloud archive behavior. The V1.2 requirements below are authoritative for the next release wherever they replace earlier forwarding, archive, UI, or storage behavior. Existing completed releases and their tests remain historical.

## Non-goals
- No Google Play compliance work, no default-SMS-app features, no reading old messages.
- No cloud backend, accounts, or network features in V1; V1.1 adds the personal Cloudflare archive below.
- No MMS, RCS, or media forwarding.
- No tablet or landscape-specific layouts in V1.

## Requirements (each has testable acceptance criteria)

R1. Receive SMS
- Given the app has RECEIVE_SMS, when an SMS arrives (even with the app closed), then the app captures sender, full body and receive time.
- Given a long message arrives as multiple parts, then the parts are combined into one message before forwarding.

R2. Forward SMS
- Given forwarding is enabled and a valid target number is saved, when a message is captured, then the same message is sent to the target number.
- DEFAULT format: "FWD from <original sender>: <body>". Bodies longer than one SMS segment are split with SmsManager.divideMessage and sent as multipart.
- DEFAULT: forward all messages (no filtering in V1). Keep a ForwardPolicy interface so a sender allowlist can be added later without restructuring.

R3. Send delay setting
- Settings offer Instant (DEFAULT), 15 seconds, 30 seconds.
- Given a delay is chosen, when a message is captured, then sending is scheduled after that delay. Instant means as soon as possible.

R4. Loop prevention
- Given a captured message whose sender matches the target number (after normalization), then it is NOT forwarded and is logged as SKIPPED_LOOP.

R5. Retry
- Given a send fails, then it is retried up to 3 times with exponential backoff starting at 30 seconds.
- Given all attempts fail, then the log status becomes FAILED. Given a send succeeds, status becomes SENT.
- Duplicate delivery of the same incoming SMS must not schedule a second forward (idempotent by normalized sender + SMS timestamp + body hash). If the phone dies after sending but before recording SENT, a retry may send it again; this rare duplicate is accepted so the message is not lost.
- After the user explicitly clears the log, prior deduplication history is erased. If Android redelivers that same SMS afterward, it may be forwarded again.

R6. Forward log
- Each handled message creates one log row: id, receivedAt, sender, snippet (first 100 characters), status (PENDING, SENT, FAILED, SKIPPED_LOOP), attempts, lastUpdatedAt.
- DEFAULT retention: rows older than 7 days are deleted automatically.

R7. Main screen
- Header shows: the forwarding target number (or "Not configured"), an enable/disable switch, and a status line showing permission and battery-optimization health.
- Below the header, a table with columns Time, From, Message, Status.
- The Message column shows a single line truncated with an ellipsis, so it adapts to screen width.
- DEFAULT time window is the last 5 minutes. A selector lets me switch to 1 hour or 24 hours.
- Empty state text when there are no rows in the window.
- The table updates live when new rows arrive.

R8. Settings
- I can enter the target number, with validation (digits, optional leading +, 10 to 15 digits). DEFAULT: a number without a country code is treated as India (+91).
- I can choose the send delay (R3).
- Saving an invalid number is blocked with an inline error.

R9. Permissions and onboarding
- On first launch, the app explains why it needs SMS permissions, then requests RECEIVE_SMS, SEND_SMS and POST_NOTIFICATIONS.
- If a permission is denied or restricted, the main screen status line says so and offers a button to open this app's system settings page.
- Include short on-screen guidance for the Android "restricted settings" step needed for sideloaded apps.

R10. Reliability
- The app prompts me to exempt it from battery optimization, and the status line shows whether it is exempt.
- Pending retries survive a phone reboot.
- DEFAULT: no foreground service in V1. Add one only if real-device testing shows missed messages.

R11. Privacy
- Satisfy every Hard rule in CLAUDE.md. A "Clear log" action deletes all log rows, cancels pending forwards, and deletes their stored bodies.

## SIM handling
DEFAULT: send using the system's default SMS subscription. Dual-SIM selection is out of scope for V1 but must not be blocked by the design.

## V1.1: Personal cloud archive (historical; superseded by V1.2 below)

- After Android reports a forwarded SMS as sent, queue its sender, complete original body, SMS timestamp, and dedupe key for upload to the private Cloudflare archive. Do not upload failed sends or loop-skipped messages.
- The archive is optional: a blank write token leaves V1 forwarding unchanged. The write token is entered in Settings and is never committed to source control.
- Keep queued bodies in app-private Room storage until the API returns success. Retry network and server failures with WorkManager network constraints. Re-enqueue stored uploads after app restart and through a periodic sweep.
- The API uses the existing dedupe key to make retries idempotent. SMS forwarding must not be retried merely because the cloud upload failed.
- Clear log deletes all local pending upload bodies and cancels their work. It does not delete messages already stored in Cloudflare.

## V1.2: Independent SMS routes

R12. Two route configurations
- SMS forwarding and the web archive each have their own on/off switch, **All SMS** or **Matching keywords** mode, and comma-separated keyword field. The forwarding route also has a destination and send delay; the web route has a masked, saved archive `WRITE_TOKEN`.
- Save the two configurations together. An enabled forwarding route requires a valid destination; an enabled web route requires a nonblank write token. Either enabled route in Matching keywords mode requires at least one nonblank keyword. Show errors beside the relevant field and do not save an invalid enabled configuration. Keep a disabled route's saved configuration for later use.
- An upgrade preserves the existing forwarding settings and archive token, defaults both modes to All SMS, and defaults the new web switch to Off until explicitly enabled. Enabling a route handles only SMS received after it is enabled. Do not backfill old messages.

R13. Body keyword matching
- Split each route's keyword field on commas, trim surrounding whitespace, and ignore empty entries. A phrase containing spaces is one keyword; a literal comma cannot be part of one keyword.
- In Matching keywords mode, match when the original SMS body contains **any one** keyword as a case-insensitive substring. Do not inspect the sender for keyword matches. An empty body does not match. All SMS mode does not require keywords and includes empty-body messages.
- Test mixed case, phrases, repeated separators and spaces, empty entries, non-Latin text, and a sender that contains a keyword while the body does not.

R14. Independent routing and forwarding
- Evaluate the two routes independently for each newly received SMS. A message can match neither, either, or both routes. A web match must not depend on forwarding being enabled, matching, scheduled, sent, or successful.
- A message matching the forwarding route follows the existing format, delay, loop prevention, deduplication, three-attempt SMS retry, and forwarding log rules. A message not matching that route creates no forwarding work or log row, even if it matches the web route. Loop prevention applies only to forwarding; a matching message from the forwarding target remains eligible for the web route.
- When forwarding is saved as Off, cancel queued delayed sends and retries, delete their pending full bodies, and show those not-yet-sent log rows as CANCELLED. An SMS send already in progress may finish; it cannot be recalled. Do not start new forwarding work while Off. Past SENT rows and seven-day log retention remain unchanged.
- When both routes are Off, ignore newly received SMS without persisting a message or creating a log row. Past forwarding log rows remain until Clear log or normal retention.

R15. One-attempt web delivery
- For an SMS matching the web route, send the original sender, full body, receive time, and deterministic idempotency key to the existing private Cloudflare `POST /api/messages` endpoint with the saved `WRITE_TOKEN`. Make this one best-effort attempt independently of the SMS forwarding route.
- If the phone has no connectivity when the SMS arrives, skip web delivery without storing its body for later. If connectivity disappears before the attempt, or the request returns an error or fails, discard that web delivery. Do not retry it, wait for connectivity, sweep it after restart, or backfill it. API success or failure must not alter the SMS forwarding status. The API's idempotency key protects the server if Android delivers the same SMS more than once.
- A web-only message creates no forwarding log row. The existing Cloudflare API contract and separate repository remain unchanged.

R16. Storage and switching Off
- Never put full SMS bodies into WorkManager input, Logcat, notifications, crash reports, or the snippet-only forwarding log. A web body may be held briefly in app-private transient storage for its single attempt. Discard transient web work that has not started within five minutes, and delete any orphaned web body after interruption without replaying it.
- Saving web archiving as Off cancels pending web work and deletes its transient bodies. Messages already stored in Cloudflare remain there. Do not maintain a web upload queue or show an `Uploads waiting` counter. The forwarding route may retain a pending body only for its existing SMS send and retry behavior.
- An upgrade from V1.1 preserves forwarding history and pending SMS sends, but deletes V1.1 queued web uploads rather than sending them later. Clear log deletes local forwarding history and pending bodies; it does not delete messages already in Cloudflare.

R17. UI and verification
- The main screen shows the state and match mode of **both** routes as status rows, plus Settings access; it has no quick route switches. Label the log **Forwarding activity** so web-only messages are not presented as missing log entries.
- Settings has separate SMS forwarding and Web archive sections. Put the on/off switch, match mode, and conditional comma-separated keyword field in each section; put the destination and delay in the forwarding section and the masked write token in the web section. Explain that offline or failed web requests are dropped. Changes take effect when **Save settings** succeeds.
- Verify all route combinations, filtering, switching Off, migration, one-attempt API failure, and privacy behavior with unit and emulator tests before a V1.2 release. Real-phone verification remains a separate step.

## V1.3: Save feedback and combined activity

R18. Visible settings feedback
- After **Save settings** finishes persisting a valid configuration, show a brief confirmation that is visible without scrolling. An invalid configuration keeps errors beside the relevant fields and shows a visible prompt to fix them. A storage failure shows a visible error and must not claim success.

R19. Independent activity statuses
- Rename the home list **Message activity**. Show one row per newly received SMS selected by either route, including web-only SMS, with separate **SMS** and **Web** status columns. Show a dash for a route that did not select the message. Messages selected by neither route create no history row. Existing forwarding behavior and its statuses remain unchanged.
- For the web route, show **Queued** only while immediate work has not begun, **Uploading** during its single attempt, **Completed** after a successful 2xx response, **Failed** after an unsuccessful HTTP response or transport error, and **Skipped** when no attempt is made because the device is offline, settings are disabled or incomplete, pending work expires, or it is cancelled before starting. If an attempt is interrupted and its result cannot be known, show **Unconfirmed**. These statuses never schedule a retry or backfill.
- Keep web activity metadata for seven days: sender, receive time, at most 100 characters of the body, status, and update time. The full body remains subject to V1.2's transient-storage rules. Clear log removes local SMS and web activity together; it never deletes Cloudflare messages. On upgrade, preserve existing forwarding history and pending sends; old web outcomes are unknown and must not be fabricated.

## V1.4: Optional app source metadata

R20. App source configuration and uploads
- Add independently optional App source phone number and App source name fields to Settings → Web archive. Both may be blank, even while web archiving is enabled. No new Android permissions or automatic number detection.
- Trim both fields. A supplied phone accepts 10–15 digits and an optional leading +; preserve the explicit country code and do not infer +91 for this source field. A supplied name accepts up to 100 UTF-16 code units, without control characters. Validate supplied metadata when Web archive is enabled. A disabled web route can keep incomplete drafts, and switching it Off must never require fixing unused source fields. Empty values remain valid in either state.
- Save both fields with the other route settings. Snapshot the configured values into the transient web-delivery row when the SMS is received. Worker payloads use this snapshot, not later edited source settings. Preserve sender as the original SMS sender. Omit blank fields from POST JSON; use appSourcePhoneNumber/appSourceName for supplied values.
- Include configured source metadata in the deterministic web-only idempotency key so otherwise identical uploads with different sources remain distinct. Both blank keeps the legacy key. SMS-forwarding keys and behavior remain unchanged.
- Upgrade the Room schema additively, preserving existing forwarding history, pending SMS sends and web status metadata. Existing transient web rows get blank source fields and remain subject to the existing expiry/orphan rules. No retry, replay or backfill is added.
- Cloudflare's optional source fields supersede the historical R15 statement that its API contract remains unchanged. The updated service accepts older uploads. Local build/testing does not authorize deployment or phone installation.
