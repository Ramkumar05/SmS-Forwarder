# T20 V1.5 offline web upload queue: verification

Branch `v1.5-offline-web-queue`.

## Automated tests written
- JVM: `WebQueuePolicyTest` (7-day expiry boundary, newest-50 cap, expired entries excluded from the cap, deterministic tie-break).
- Android: `WebQueueRepositoryTest` (queue/dedupe, oldest first, requeue, cap 50, 7-day expiry, startup keeps queue, Off deletes queue, Clear log keeps queue, 100-character projection) and `WebQueueWorkerTest` (network-constrained request with no input data, ten messages oldest first, `IOException` keeps queue and retries, HTTP error dropped without blocking later messages, Off deletes queue, blank token leaves queue, expiry, app-source snapshot). Existing route, migration, coordinator and source-upload tests were updated for the new constructors.

## Status
The machine that authored this change has no JDK or Android SDK, so **nothing was compiled or run**: no `./gradlew :app:testDebugUnitTest`, no `connectedDebugAndroidTest`, no APK. Run both before release:

```sh
./gradlew :app:testDebugUnitTest
ANDROID_SERIAL=emulator-5554 ./gradlew :app:connectedDebugAndroidTest
```

Real-phone checks still to do: airplane mode, receive several SMS, confirm the Queue tab, restore network and confirm all arrive oldest first; reboot with a queue; switch Web Off and confirm the queue empties.
