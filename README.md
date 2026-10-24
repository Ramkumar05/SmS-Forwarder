# Auto SMS Forwarder

<img src="assets/autosms-launcher-source.png" alt="Auto SMS Forwarder icon" width="96">

A personal Android app that forwards new incoming SMS messages to one saved number and shows a recent forwarding log. Version 1.0 is designed for sideloading; it has no accounts, cloud service, analytics, or internet access.

## What it does

- Forwards messages as `FWD from <sender>: <body>`, including long messages that need multiple SMS segments.
- Offers Instant, 15-second, and 30-second send delays, an enable switch, and a live log with 5-minute, 1-hour, and 24-hour views.
- Skips messages from the forwarding target to prevent a loop. Retries failed sends up to three times and restores pending work after a reboot.
- Keeps log rows for seven days. **Clear log** also cancels pending forwards and deletes their stored message bodies.

It uses the system's default SMS subscription. MMS, RCS, old messages, and dual-SIM selection are outside version 1.

## Build and run

1. Install Android Studio and the Android SDK platform for API 37. Open this repository in Android Studio and let Gradle sync using the included wrapper.
2. Build a debug APK with `./gradlew :app:assembleDebug`. The output is `app/build/outputs/apk/debug/app-debug.apk`.
3. Install it on an Android device running API 26 or newer with `adb install app/build/outputs/apk/debug/app-debug.apk`, or run the `app` configuration from Android Studio.
4. Open the app, grant the requested SMS and notification permissions, save a forwarding target in Settings, and review the battery optimization prompt. Sideloaded apps may require enabling restricted settings on the device.

An APK signed with a different key cannot update an existing installation in place. Uninstalling the existing app removes its local settings and log. The local `signing/` directory is ignored by Git; keep a secure backup of any release key because future release updates need the same key. `assembleRelease` builds the release variant, but a fresh clone has no release signing configuration.

## Tests and verification

```sh
./gradlew :app:testDebugUnitTest
ANDROID_SERIAL=emulator-5554 ./gradlew :app:connectedDebugAndroidTest
./gradlew :app:assembleRelease
```

Replace `emulator-5554` with the emulator serial shown by `adb devices` if it differs. Run connected Android tests on a disposable emulator: they include a Clear log test that changes app data. The [verification report](specs/t10-test-report.md) records the unit, emulator, signed APK, and basic real-phone checks, along with real-phone cases that remain unverified.

## Privacy and project details

The app has no `INTERNET` permission and disables Android backup. Its forwarding log stores at most the first 100 characters of each message. Full text is held only in an app-private pending-forward table until the send finishes or fails, with a 24-hour safety purge. Message contents and phone numbers are not written to Logcat, and full message bodies are not placed in notifications.

See [requirements](specs/requirements.md), [design](specs/design.md), and [tasks](specs/tasks.md) for the version 1 specification. The app is built with Kotlin, Jetpack Compose, Room, DataStore, and WorkManager.
