# Auto SMS Forwarder: project rules

## What this is
A personal Android app that forwards every incoming SMS to one configured phone number, and shows a log of recent forwards. It is sideloaded (not for Google Play).

## Stack (do not substitute)
- Kotlin, Jetpack Compose (Material 3), single-activity
- Room for the forward log, DataStore (Preferences) for settings
- WorkManager for sending and retries
- Coroutines and Flow; manual dependency wiring or Hilt, keep it simple
- minSdk 26, targetSdk = latest stable
- Tests: JUnit, kotlinx-coroutines-test, Room in-memory tests, WorkManager test helpers

## Architecture rules
- Layers: receiver -> worker -> repository -> Room. UI reads state from ViewModels only.
- The BroadcastReceiver must do no real work. It parses the SMS, enqueues a worker, and returns within milliseconds.
- Put all pure logic (multipart stitching, number normalization, loop check, message formatting, retry policy, time-window filter) in plain Kotlin classes with unit tests.

## Hard rules
- Never log SMS bodies or phone numbers to Logcat or crash reports.
- Store only the first 100 characters of a message body in the database, never the full text.
- No INTERNET permission. No analytics, no network calls, no third-party SDKs that phone home.
- Set android:allowBackup="false".
- Declare only RECEIVE_SMS, SEND_SMS, POST_NOTIFICATIONS, and REQUEST_IGNORE_BATTERY_OPTIMIZATIONS in the app manifest. WorkManager's merged WAKE_LOCK, ACCESS_NETWORK_STATE, RECEIVE_BOOT_COMPLETED, FOREGROUND_SERVICE, and private receiver permission are allowed. Do not request READ_SMS.
- Do not add features that are not in specs/requirements.md. If something seems missing, ask.

## Working agreement
- Work on one task from specs/tasks.md at a time.
- Write tests for pure logic before or alongside the code.
- Run the build and tests before declaring a task done.
- If the spec conflicts with reality (API behavior, library limits), stop and ask. Do not choose silently.
- Keep commits small, one per task, with the task ID in the message.
