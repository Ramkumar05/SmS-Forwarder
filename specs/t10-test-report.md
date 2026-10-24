# T10 verification report

Date: 2026-10-23–24. Phone: realme GT2 (RMX3312), Android API 35. The referenced “section 7” kit is absent from this workspace, so this checklist uses the acceptance criteria in R1–R11. `FAIL` means the criterion has not been verified on the phone yet; it does not by itself establish a product defect. No phone numbers or SMS text are recorded here.

## Build and automated checks

- PASS — 35 debug unit tests and the emulator Android test suite completed with no failures. The suite lists 14 tests; its two reboot cases require a separate opt-in run, which was also completed below.
- PASS — Release variant built and lint vital checks passed.
- PASS — Signed release APK verifies with APK Signature Schemes v2 and v3.
- PASS — The icon-updated signed release APK installed as an in-place update and launched on the Android 17 emulator.
- PASS — The realme GT2 accepted the debug build and granted RECEIVE_SMS, SEND_SMS, and POST_NOTIFICATIONS.

## Additional isolated emulator checks (2026-10-24)

- PASS — On a fresh Android 17 emulator, all Android instrumentation tests passed. Room, WorkManager, settings persistence, health state, and Clear log were exercised without touching the connected phone.
- PASS — A separate two-stage test seeded a pending retry, rebooted the emulator, and verified the queued work reached SENT with the pending body removed.
- PASS — Two injected synthetic SMS messages, one short and one over one segment, each reached SENT. The emulator's return message from the forwarding target was logged as SKIPPED_LOOP. No pending bodies remained; long-message log snippets were limited to 100 characters.
- PASS — The release-signed APK was verified, installed over the prior release-signed APK, and launched successfully on the emulator.

These checks support a version 1 repository freeze. They do not replace the unverified real-phone cases below, especially carrier multipart delivery, all delay modes, permission revocation, and battery behavior across devices.

## Real-phone checklist

| Requirement | Result | Evidence or remaining check |
|---|---|---|
| R1: capture a new SMS | PASS | User reported one successful new-message forward on the realme GT2. |
| R1: capture while app closed and screen locked | PASS | User confirmed delivery without reopening the app. Android recorded receive and send activity while the screen was dozing. |
| R1: combine multipart SMS | FAIL | Not yet tested on phone. |
| R2: forward to saved target | PASS | User reported that the simple forward reached the target. |
| R2: use the specified `FWD from` format | PASS | User confirmed the forwarded SMS showed the sender and original text. |
| R2: send a long multipart forward | FAIL | Not yet tested on phone. |
| R3: 15-second scheduling | PASS | Android recorded a send attempt about 15 seconds after receipt during the locked-screen check. |
| R3: Instant and 30-second scheduling | FAIL | These modes were not timed on phone. |
| R4: skip a message sent from target number | FAIL | Not yet tested on phone. |
| R5: retry up to three attempts and reach SENT or FAILED | FAIL | Reboot retry passed on emulator; phone retry sequence not yet tested. |
| R5: deduplicate repeated incoming SMS | FAIL | Repository and worker tests pass; phone duplicate broadcast not yet tested. |
| R6: show a log row and retain it for seven days | FAIL | Log behavior has automated coverage; phone retention cannot yet be observed. |
| R7: header, switch, status, windows, live rows | FAIL | Screen opened on phone; full behavior not yet checked there. |
| R8: save a valid target and reject an invalid one | FAIL | A target was configured on phone; invalid-input check not yet run there. |
| R9: onboarding and permission status | FAIL | SMS and notification permissions are granted; denial and revocation not yet checked on phone. |
| R10: battery exemption prompt and status | FAIL | Not yet checked on phone. |
| R10: pending retry survives reboot | FAIL | Passed on emulator; not yet tested on phone. |
| R11: privacy rules and Clear log | FAIL | Static audit and emulator tests pass; Clear log not yet checked on phone. |

## Release artifact

- APK: `app/build/outputs/apk/release/app-release.apk`
- SHA-256: `9bb8e941ec4664424bcfcbb4e4328a108396462df89ad1383974f535b7b21008`
- Signing key and password are stored only in the ignored local `signing/` directory. Back up that directory securely; future updates to this release APK require the same key.
- The phone currently has a debug-signed build. Installing this release build on it requires uninstalling that build first, which deletes the app's local settings and log.
