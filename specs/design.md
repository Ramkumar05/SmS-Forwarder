# Design

## Flow
Incoming SMS -> SmsReceiver (manifest-registered, SMS_RECEIVED) -> parse + stitch multipart parts -> insert PENDING log row (or SKIPPED_LOOP) -> enqueue ForwardWorker via WorkManager -> worker sends via SmsManager -> update log row (SENT, or retry, or FAILED).

## Components
- SmsReceiver: BroadcastReceiver. Uses Telephony.Sms.Intents.getMessagesFromIntent, groups parts by sender, builds one body. Uses goAsync() only if needed to finish the database insert. Enqueues work and returns.
- ForwardPolicy: interface decide(message, settings) -> Forward | Skip(reason). V1 implementation: forward all except loop case.
- ForwardWorker (CoroutineWorker): loads the log row, sends the SMS, updates status. Returns retry() on failure until attempts are exhausted, then marks FAILED.
- Scheduling: unique work name derived from the idempotency key (sender + receivedAt + body hash) so duplicates are impossible. Instant mode uses expedited work with a normal fallback. Delayed modes use a normal OneTimeWorkRequest with initialDelay (expedited work cannot be delayed). Backoff is exponential, 30 seconds base.
- SmsSender: wraps SmsManager (use the context-based getSystemService form on newer Android) behind an interface so it can be faked in tests. Handles divideMessage and sendMultipartTextMessage.
- NumberNormalizer: strips spaces and punctuation, applies the +91 default, compares numbers by their last 10 digits for the loop check.
- MessageFormatter: builds the "FWD from <sender>: <body>" text.
- ForwardLogDao / ForwardLogEntity (Room): table forward_log with the fields in R6, indexed by receivedAt. Query for the UI: rows where receivedAt >= now - window, ordered newest first, exposed as Flow.
- SettingsRepository (DataStore): targetNumber, forwardingEnabled, sendDelay.
- RetentionWorker: periodic daily work that deletes rows older than 7 days.
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
- Phone reboot with pending work.

## Testing strategy
- Unit tests: multipart stitching, NumberNormalizer, loop check, MessageFormatter, retry/status transitions, time-window filtering, idempotency key.
- Room tests with an in-memory database.
- Worker tests with TestListenableWorkerBuilder and a fake SmsSender.
- Manual: emulator SMS injection with `adb emu sms send <sender> <text>`, then real-device verification per section 7 of the kit.
