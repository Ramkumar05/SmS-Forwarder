# T17 V1.2 verification report

Date: 2026-10-25. Version: 1.2 (`versionCode` 3). Scope: local automated verification and a signed release artifact. No app was installed on a personal phone, and no real SMS or Cloudflare request was sent during this task.

## Automated results

- **PASS** — `:app:testDebugUnitTest`: 47 unit tests, zero reported failures, errors, or skips.
- **PASS** — `:app:connectedDebugAndroidTest` on a disposable Android 17 emulator: 41 reported tests, zero failures or errors. The opt-in reboot scenarios returned early because they were not explicitly enabled in this run.
- **PASS** — The route-matrix Android tests cover all four switch combinations and separate keyword modes. They check body-only matching, case-insensitive matches, a non-Latin keyword, and that a web-only message creates no forwarding log.
- **PASS** — Android tests cover route switch-off cleanup, privacy of queued work data, V1 and V1.1 database upgrades, one-attempt delivery, offline handling, missing token, and server/transport failure using a fake uploader. The V1.1 upgrade test confirms that pending forwarding data survives while old upload bodies are dropped.
- **PASS** — `:app:assembleRelease` and `:app:lintVitalRelease` completed. Static review found no SMS body or token in WorkManager input and no app Logcat logging of them. The signed manifest requests `RECEIVE_SMS`, `SEND_SMS`, and `INTERNET`, but not `READ_SMS`.

The emulator tests use a fake web uploader; they do not prove behavior against the live Cloudflare Worker or a mobile carrier. The four route combinations were tested at the route-processor and database level, rather than by delivering carrier SMS to the emulator.

## Release artifact

- File: `app/build/outputs/apk/release/app-release-v1.2-signed.apk` (local build output)
- Package: `com.autosmsforwarder`; minimum Android API 26; target API 37
- SHA-256: `96f240f7bf14122819acbd06fa6db00f30aa7383049fc093b55ca644fbacfade`
- Signed with the existing local `signing/autosms-release.jks` alias `autosms-release`. `zipalign -c` and `apksigner verify` passed; APK signing schemes v2 and v3 verify.
- Signer certificate SHA-256: `59066573c51ae924a8bc30ecebba3f053634d58ed23659f8362b1b25f1556737`

The prior signed APK was not available after the release build, so this run did not directly compare its certificate with V1.2. Check the installed app certificate before an in-place phone upgrade. The release APK has not been installed, committed, pushed, or deployed.

## Real-phone checks before use

These checks remain **pending** and require separate approval for installation:

1. Confirm the currently installed app is signed by the same certificate, then install V1.2 in place and verify existing forwarding settings and log survive. Do not uninstall the old app merely to make the upgrade work, because that removes local data.
2. Review the two Settings sections, set a test destination and the archive write token, and confirm each route can be enabled and configured independently.
3. Send synthetic SMS messages covering forward only, web only, both, and neither; check the forwarding phone/log and the Cloudflare archive for the expected result. Check keyword matching with a mixed-case SMS body and a keyword only in the sender.
4. With the web route enabled, disconnect data before a synthetic test message, then reconnect and verify the missed message is not uploaded later. Try a deliberately invalid write token and verify forwarding still behaves independently.
5. With a forwarding delay selected, disable forwarding before the delay expires and verify the queued send is cancelled. If a web attempt is still pending, switch the web route Off and confirm it does not upload later. When both routes are Off, confirm new test SMS produces no app log entry.
6. Check delivery while the phone is locked and after reboot, plus carrier behavior for long or multipart SMS and the device's default SMS subscription. The opt-in reboot tests were not run in this T17 pass.

Record any phone-specific findings before treating V1.2 as fully field verified.
