# Auto SMS Forwarder

<img src="assets/autosms-launcher-source.png" alt="Auto SMS Forwarder icon" width="96">

A personal Android app with two independent routes for newly received SMS: forward to one phone number, send the original message to a private Cloudflare archive, or do both.

## What it does

- Each route has its own switch and **All SMS** or **Matching keywords** mode. Enter comma-separated keywords; any one case-insensitive match in the SMS body qualifies. The sender is not checked for keywords.
- SMS forwarding keeps the existing `FWD from <sender>: <body>` format, Instant/15-second/30-second delay, loop prevention, deduplication, and up to three send attempts. Its log shows forwarding activity only.
- Web archiving uses the original sender, body, and receive time. It makes one best-effort API attempt independently of forwarding. An offline or failed delivery is dropped without a queue, retry, or backfill.
- Switching forwarding Off cancels queued sends and deletes their pending full bodies. Switching web archiving Off deletes pending web bodies. When both routes are Off, new SMS are ignored without saving them.
- The home **Message activity** list shows separate SMS and Web statuses, including web-only messages. Web **Queued** means the immediate attempt has not started; an offline or failed attempt is never retried. Settings shows a visible confirmation after a successful save.

It uses the system's default SMS subscription. MMS, RCS, old messages, and dual-SIM selection are outside version 1.4.

## Configure and run

1. Install Android Studio and the Android SDK platform for API 37. Open this repository in Android Studio and let Gradle sync using the included wrapper.
2. Build a debug APK with `./gradlew :app:assembleDebug`. The output is `app/build/outputs/apk/debug/app-debug.apk`.
3. Install it on an Android device running API 26 or newer, or run the `app` configuration from Android Studio. Open the app and grant the requested SMS and notification permissions. A sideloaded app may require enabling restricted settings in Android's app settings.
4. In **Settings**, configure the SMS forwarding and Web archive sections separately, then tap **Save settings**. An enabled forwarding route needs a valid destination. An enabled web route needs the archive `WRITE_TOKEN`. Matching keywords mode needs at least one keyword for its route.

The archive write token belongs to the separate [SMS RK](https://github.com/Ramkumar05/sms-rk) Cloudflare Worker. It is not the Cloudflare account API token or the archive read token. The app sends matching messages to `https://sms-archive.arj-ramkumar.workers.dev/api/messages`.

An APK signed with a different key cannot update an existing installation in place. Uninstalling an existing installation deletes its local settings and log. The local `signing/` directory is ignored by Git; keep a secure backup of the release key because future release updates need that same key. A fresh clone has no release signing configuration.

## Tests and release

```sh
./gradlew :app:testDebugUnitTest
ANDROID_SERIAL=emulator-5554 ./gradlew :app:connectedDebugAndroidTest
./gradlew :app:assembleRelease
```

Run Android tests on a disposable emulator: some tests change app data. `assembleRelease` creates an unsigned APK. This checkout has a signed V1.3 review APK documented in the [V1.3 report](specs/t18-test-report.md). The [V1.2 report](specs/t17-test-report.md), [V1.1 report](specs/t11-test-report.md), and [V1 report](specs/t10-test-report.md) remain as release history.

## Privacy

Android backup is disabled. The SMS and Web activity logs store only the first 100 characters of each SMS body. A full body remains in app-private Room storage only while a forward is pending or until its one web attempt begins; a web body is discarded if the attempt has not begun within five minutes. Full bodies and tokens are never placed in WorkManager input, Logcat, or notifications. **Clear log** deletes local logs and pending bodies; it does not delete messages already stored in Cloudflare.

See [requirements](specs/requirements.md), [design](specs/design.md), and [tasks](specs/tasks.md) for the current specification. The app uses Kotlin, Jetpack Compose, Room, DataStore, and WorkManager.

## V1.4 app source metadata

Settings → Web archive now includes independently optional App source phone number and App source name fields. Enter them manually; blank is valid, no automatic detection or new permission is required. Provided phone values use 10–15 digits with an optional +, and names use up to 100 characters without control characters. Values are captured for each new web delivery and sent as appSourcePhoneNumber/appSourceName, separately from the original SMS sender. Local combined activity identity and one-attempt semantics are preserved. See [T19 verification](specs/t19-test-report.md). Deploy Cloudflare migration 0002 and its updated Worker before installing V1.4.

## Latest source snapshot

This repository contains the complete tested V1.4 app, including the previously unpushed independent routes and combined activity changes. Verification: 57 JVM tests and 49 emulator tests passed; the two legacy opt-in reboot scenarios were not explicitly run. V1.4 is installed on the Moto and realme phones, and the Cloudflare metadata migration/API/viewer deployment is complete. Signed APKs and signing credentials remain local and are excluded from Git. See [T19 verification](specs/t19-test-report.md).
