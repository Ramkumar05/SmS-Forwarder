# Auto SMS Forwarder

<img src="assets/autosms-launcher-source.png" alt="Auto SMS Forwarder icon" width="96">

A personal Android app that forwards new incoming SMS messages to one saved number and shows a recent forwarding log. Version 1.1 can also save successfully forwarded messages to a private Cloudflare archive.

## What it does

- Forwards messages as `FWD from <sender>: <body>`, including long messages that need multiple SMS segments.
- Offers Instant, 15-second, and 30-second send delays, an enable switch, and a live log with 5-minute, 1-hour, and 24-hour views.
- Skips messages from the forwarding target to prevent a loop. Retries failed sends up to three times and restores pending work after a reboot.
- Keeps log rows for seven days. **Clear log** also cancels pending forwards and deletes their stored message bodies.
- Optionally uploads the original SMS body to the private archive after the SMS send succeeds. Uploads wait for a network connection and are retried independently of SMS sending.

It uses the system's default SMS subscription. MMS, RCS, old messages, and dual-SIM selection are outside version 1.

## Build and run

1. Install Android Studio and the Android SDK platform for API 37. Open this repository in Android Studio and let Gradle sync using the included wrapper.
2. Build a debug APK with `./gradlew :app:assembleDebug`. The output is `app/build/outputs/apk/debug/app-debug.apk`.
3. Install it on an Android device running API 26 or newer with `adb install app/build/outputs/apk/debug/app-debug.apk`, or run the `app` configuration from Android Studio.
4. Open the app, grant the requested SMS and notification permissions, save a forwarding target in Settings, and review the battery optimization prompt. Sideloaded apps may require enabling restricted settings on the device.

To enable cloud archiving, open Settings and enter the archive's `WRITE_TOKEN` in **Archive write token**, then save. Use the token configured for the separate [SMS RK](https://github.com/Ramkumar05/sms-rk) Cloudflare Worker, not your Cloudflare API token or the archive read token. Leave this field blank to disable uploads for future sends. Settings shows how many uploads are waiting. The app sends data to `https://sms-archive.arj-ramkumar.workers.dev/api/messages`.

An APK signed with a different key cannot update an existing installation in place. Uninstalling the existing app removes its local settings and log. The local `signing/` directory is ignored by Git; keep a secure backup of any release key because future release updates need the same key. `assembleRelease` builds the release variant, but a fresh clone has no release signing configuration.

For this checkout, the version 1.1 APKs are `app/build/outputs/apk/debug/app-debug.apk` (debug) and `app/build/outputs/apk/release/app-release-signed.apk` (signed with the existing local release key). Install the one matching the signature of the app already on your phone to retain settings and logs. The release APK can update an earlier release signed with the same local key; it cannot update a debug-signed installation.

## Tests and verification

```sh
./gradlew :app:testDebugUnitTest
ANDROID_SERIAL=emulator-5554 ./gradlew :app:connectedDebugAndroidTest
./gradlew :app:assembleRelease
```

Replace `emulator-5554` with the emulator serial shown by `adb devices` if it differs. Run connected Android tests on a disposable emulator: they include a Clear log test that changes app data. The [version 1.1 verification report](specs/t11-test-report.md) records the current build and tests; the [version 1 report](specs/t10-test-report.md) records earlier real-phone checks.

## Privacy and project details

The app disables Android backup. Its forwarding log stores at most the first 100 characters of each message. Full text is held in an app-private pending-forward table during SMS sending. When archiving is enabled and the SMS send succeeds, the full body moves to an app-private upload outbox until Cloudflare accepts it. Failed sends and loop-skipped messages are never uploaded. The write token stays in app-private settings and must never be committed to Git. Message contents and phone numbers are not written to Logcat, and full message bodies are not placed in notifications. **Clear log and waiting uploads** removes local data; it does not erase messages already in Cloudflare.

See [requirements](specs/requirements.md), [design](specs/design.md), and [tasks](specs/tasks.md) for the version 1 specification. The app is built with Kotlin, Jetpack Compose, Room, DataStore, and WorkManager.
