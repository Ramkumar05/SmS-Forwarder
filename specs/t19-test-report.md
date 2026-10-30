# T19 — Optional app source metadata (V1.4)

Status: V1.4 installed and opened on the Moto and realme phones after separate user approval. The complete tested source is included in this repository; Cloudflare migration/deployment are complete via SMS RK commit 5ddd315; a new real-message metadata acceptance test remains pending.

- 57 JVM tests passed, including optional field validation and source-aware upload identity.
- 49 disposable-emulator tests passed with zero failures/errors/skips. They cover old database upgrade chains through schema 5, preservation of pending forwarding data, blank source defaults, Settings persistence/recreation/clearing, optional field input, receive-time snapshots, actual HTTP JSON serialization and optional omission, route combinations, existing SMS retries, web one-attempt semantics, privacy and cleanup. The two existing opt-in reboot scenarios return early and were not explicitly exercised.
- Android lint and release build passed. Full lint surfaced pre-existing locale/telephony declarations; the locale now observes configuration and the SMS app explicitly declares its telephony requirement. No phone-number permission was added.
- The local activity/work key stays unchanged so SMS/Web statuses still merge correctly. Only the API idempotency key incorporates configured source values; both blank preserves the old key.
- Signed APK uses the existing release keystore. Certificate matches the V1.3 report; signature and 16 KiB alignment checks passed.
- Cloudflare schema/contract migration must be deployed before installing this forwarding release. No production service or physical phone was modified during these checks.

Artifact: [V1.4 signed APK](../app/build/outputs/apk/release/app-release-v1.4-signed.apk).

APK SHA-256: `defffbd1e97339c4f59f9627aee6feb2d8e1505fc1c9bd158e9ebc6e4b362b15`.

Final validation includes disabling Web archive with incomplete optional source drafts: switching Off remains allowed, while enabling validates any supplied values. Full unit/emulator/lint/build checks passed again after this adjustment.

## Approved Moto installation

After separate user authorization, the connected Motorola edge 20 fusion was identified. APK checksum and matching installed signing certificate were verified. The in-place update returned Success; version 1.4 / code 5 was confirmed and MainActivity opened with Status: ok. The app was not uninstalled and its data was not cleared. Optional app source values can be entered in Settings → Web archive; they will be stored online after the Cloudflare migration/Worker deployment. No live message contents or tokens were inspected.

## Approved realme installation

After USB debugging was enabled, the authorized realme RMX3312 was identified. The V1.4 APK checksum and existing installation certificate matched the tested release. Update-in-place returned Success; version 1.4 / code 5 was confirmed and MainActivity opened with Status: ok. No uninstall, data clearing, credential access or SMS inspection was performed.

## Backend deployment completed

After separate user approval, GitHub workflow 41082648089 applied migration 0002 and deployed the updated Cloudflare API/viewer. Live schema/asset/authentication checks passed and all 35 existing messages were preserved. Optional source metadata can now be captured for new uploads.

## Source publication

The source snapshot includes the independent route/activity work needed by V1.4, source metadata, tests and specs. Signing keys, local credentials, SDK paths, caches and APK outputs are excluded.
