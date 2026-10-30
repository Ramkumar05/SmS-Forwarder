# Auto SMS Forwarder: project rules

## What this is
A sideloaded personal Android app with two independently configured routes for newly received SMS: forwarding to one phone number and sending to a private Cloudflare archive. The app shows activity for both routes. V1–V1.2 behavior remains documented as release history; V1.4 requirements govern the next release, including independently optional app source phone/name fields.

## Stack (do not substitute)
- Kotlin, Jetpack Compose (Material 3), single-activity
- Room for the forward log, DataStore (Preferences) for settings
- WorkManager for SMS sending/retries and one-attempt web delivery
- Coroutines and Flow; manual dependency wiring or Hilt, keep it simple
- minSdk 26, targetSdk = latest stable
- Tests: JUnit, kotlinx-coroutines-test, Room in-memory tests, WorkManager test helpers

## Architecture rules
- Layers: receiver -> route decision -> workers/repositories -> Room. UI reads state from ViewModels only.
- The BroadcastReceiver must do no network call or SMS send. It parses the SMS, decides both routes, persists eligible transient work, enqueues workers, and returns promptly.
- Put pure logic (multipart stitching, number normalization, body-keyword matching, independent route decisions, loop check, message formatting, retry policy, time-window filter) in plain Kotlin classes with unit tests.

## Hard rules
- Never log SMS bodies or phone numbers to Logcat or crash reports.
- Activity tables (`forward_log` and `web_log`) store only the first 100 characters of a body, never the full text.
- The full body may be persisted in app-private Room only for a pending SMS send/retry or a brief one-attempt web delivery. Purge pending-forward rows older than 24 hours. Discard web transient rows after their single attempt, after five minutes without an attempt, when web archiving is switched Off, or during orphan cleanup. Never replay web work after reboot or retain an offline upload queue.
- The full body must never go into WorkManager input Data, Logcat, crash reports, notifications, or arbitrary files outside the permitted app-private Room tables.
- INTERNET permission is only for posting incoming SMS that match the independently configured web route to the first-party Cloudflare archive. The web route does not depend on an SMS forward succeeding. When connectivity is absent or a request fails, drop web delivery without retry. No analytics or third-party SDKs that phone home.
- Each route has its own switch, All SMS/Matching keywords mode, and comma-separated body-only keyword list. Keyword mode matches any one entry case-insensitively. When both routes are Off, do not persist newly received SMS. Switching forwarding Off cancels queued delayed sends/retries and deletes their pending bodies; an in-progress send may finish. Switching web archiving Off cancels pending web work and deletes its transient bodies.
- The main screen shows both route states and combined message activity with separate SMS and Web statuses; route switches and full configuration live in Settings. Enabled routes must pass validation before settings are saved together, with visible save feedback.
- Set android:allowBackup="false".
- Declare only RECEIVE_SMS, SEND_SMS, INTERNET, POST_NOTIFICATIONS, and REQUEST_IGNORE_BATTERY_OPTIMIZATIONS in the app manifest. WorkManager's merged WAKE_LOCK, ACCESS_NETWORK_STATE, RECEIVE_BOOT_COMPLETED, FOREGROUND_SERVICE, and private receiver permission are allowed. Do not request READ_SMS.
- Do not add features that are not in specs/requirements.md. If something seems missing, ask.

## Working agreement
- Work on one task from specs/tasks.md at a time.
- Before each coding task, describe the exact plan and wait for the user's approval. Treat documentation review, code changes, commits, and pushes as separate approvals when the user scopes them separately.
- Write tests for pure logic before or alongside the code.
- Run the build and tests before declaring a task done.
- If the spec conflicts with reality (API behavior, library limits), stop and ask. Do not choose silently.
- Keep commits small, one per task, with the task ID in the message.
