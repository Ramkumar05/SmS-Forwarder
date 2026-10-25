# T11 verification report

Date: 2026-10-25. Version: 1.1 (versionCode 2).

- PASS — 35 local unit tests and 19 Android tests on a fresh temporary Android 17 emulator, with zero failures.
- PASS — A test upgraded a version 1 Room database with an existing log and pending body to version 2. Both old rows survived, and the new upload outbox was available.
- PASS — After a successful SMS send, the full original body moves from pending storage to the upload outbox. A successful API response deletes it; server and authorization failures preserve it without changing the SMS send status.
- PASS — The app, debug APK, and release APK build. The release APK is signed with the existing local release key and verifies with Android APK signing schemes v2 and v3.
- PASS — The separate Cloudflare API was reported working by the user. The Android tests use a fake uploader and do not send a real SMS body to Cloudflare.

Still to check on the phone: install the version 1.1 APK signed with the same key as the existing app, enter the Cloudflare archive `WRITE_TOKEN` in Settings, send a test SMS to the phone, and confirm the forwarding log shows SENT and the message appears in the web archive. This also checks phone-specific network and SMS behavior that the emulator tests cannot prove.
