# Tasks (do in order, one at a time)

T1. Project skeleton — DONE
- Create the Android project, dependencies, package structure and manifest permissions per CLAUDE.md.
- Done when: app builds, launches to an empty MainScreen, and a placeholder unit test passes.

T2. Pure logic with tests
- Implement NumberNormalizer, MessageFormatter, loop check, idempotency key, time-window filter, retry/status transitions. (R2, R4, R5, R7)
- Done when: unit tests for each pass, including edge cases (emoji, empty body, numbers with spaces and +91).

T3. Walking skeleton: receive and forward
- Implement SmsReceiver, SmsSender, and a minimal ForwardWorker with a hard-coded target number taken from a debug setting. (R1, R2)
- Done when: injecting an SMS on the emulator causes the worker to call the sender with the right text (verified by a fake in tests), and on a real phone a received SMS reaches the target number.

T4. Room log
- Implement ForwardLogEntity, Dao, repository, status updates and the retention worker. (R5, R6)
- Done when: Room tests pass; rows move PENDING -> SENT / FAILED correctly; rows older than 7 days are purged.

T5. Settings
- Implement SettingsRepository and SettingsScreen with number validation and delay selector. Wire the delay into scheduling. (R3, R8)
- Done when: invalid numbers are rejected, settings persist across app restarts, and delayed sends are scheduled with the chosen delay (verified by a worker test).

T6. Main screen
- Implement header, enable switch, window selector, live log table with single-line ellipsis rows, empty state, ticking clock. (R7)
- Done when: new rows appear live, rows leave the 5-minute window without interaction, and the layout is correct on a small and a large screen.

T7. Permissions and onboarding
- Implement OnboardingScreen, permission handling, status line, deep link to app settings, restricted-settings guidance. (R9)
- Done when: denying or revoking a permission is reflected in the status line and nothing crashes.

T8. Reliability
- Implement HealthChecker, the battery-optimization prompt and status, and reboot-safe retries. (R5, R10)
- Done when: after a reboot with a pending retry, the message is still sent; the status line reflects battery-exemption state.

T9. Hardening and privacy
- Audit against CLAUDE.md Hard rules: no body logging, snippet-only storage, no INTERNET permission, allowBackup=false, Clear log action. (R11)
- Done when: a written audit checklist in the final report shows each rule with the file and line that satisfies it.

T10. End-to-end verification
- Produce a release APK and a short test report against the real-phone checklist.
- Done when: every checklist item has a pass/fail recorded.
