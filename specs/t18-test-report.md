# T18 V1.3 verification report

Date: 2026-10-25. Version: 1.3 (`versionCode` 4). Scope: visible settings feedback and combined SMS/Web activity.

## Results

- **PASS** — 48 local unit tests and 45 Android tests on an isolated Android 17 emulator data directory; zero reported failures, errors, or skips. The two opt-in reboot scenarios returned early and were not explicitly exercised in this run.
- **PASS** — A Settings UI test finds the save confirmation. The confirmation is displayed in a snackbar anchored outside the scrolling form; validation and storage errors also produce visible feedback.
- **PASS** — The activity merger includes forwarding-only, web-only, and both-route messages in receive-time order. The unselected route displays a dash.
- **PASS** — Android tests verify web `COMPLETED` after HTTP 2xx, `FAILED` after server/transport failure, `SKIPPED` before an attempt when offline, disabled, expired, or cancelled, and `UNCONFIRMED` for an interrupted attempt after restart. No web retry or replay was added.
- **PASS** — The V1.2-to-V1.3 Room migration preserves forwarding history and pending SMS bodies while adding the web metadata table. Web snippets are capped at 100 characters; full web bodies remain transient. Seven-day retention and Clear log remove web metadata alongside forwarding history.
- **PASS** — Release build and `lintVitalRelease`; the APK passes alignment and Android APK signature verification.

The emulator tests use a fake uploader and do not send carrier SMS or call the live Cloudflare Worker. Real-phone behavior and the visual density of the five-column table should be checked by the user before adopting this build.

## Review APK

- File: `app/build/outputs/apk/release/app-release-v1.3-signed.apk`
- Package: `com.autosmsforwarder`; version 1.3, code 4; minimum API 26, target API 37
- APK SHA-256: `50c300271bfe958d1d711e560d78bc9929f4f9b6e8c6ab7fb105012f74304ed8`
- Signer certificate SHA-256: `59066573c51ae924a8bc30ecebba3f053634d58ed23659f8362b1b25f1556737` (same local key used for V1.2)
- APK signing schemes v2 and v3: verified

After separate user approval, the APK was installed as an in-place update on a connected realme RMX3312 phone. The installed V1.2 and new V1.3 APKs had the same signing certificate; ADB reported `Success`, and the phone reports version 1.3 (`versionCode` 4). User-led real-phone behavior checks remain pending. The changes were not committed, pushed, or deployed.
