# Tasks (do in order, one at a time)

T1–T11 record completed V1/V1.1 work. T20 (V1.5) was approved by the user to run end-to-end on a feature branch without pauses. For V1.2, complete one task at a time and obtain the user's approval for its plan before coding. V1.2 requirements supersede conflicting historical acceptance criteria.

T1. Project skeleton — DONE
- Create the Android project, dependencies, package structure and manifest permissions per CLAUDE.md.
- Done when: app builds, launches to an empty MainScreen, and a placeholder unit test passes.

T2. Pure logic with tests — DONE
- Implement NumberNormalizer, MessageFormatter, loop check, idempotency key, time-window filter, retry/status transitions. (R2, R4, R5, R7)
- Done when: unit tests for each pass, including edge cases (emoji, empty body, numbers with spaces and +91).

T3. Walking skeleton: receive and forward — DONE
- Implement SmsReceiver, SmsSender, and a minimal ForwardWorker with a hard-coded target number taken from a debug setting. (R1, R2)
- Done when: injecting an SMS on the emulator causes the worker to call the sender with the right text (verified by a fake in tests), and on a real phone a received SMS reaches the target number.

T4. Room log — DONE
- Implement ForwardLogEntity, Dao, repository, status updates and the retention worker. (R5, R6)
- Done when: Room tests pass; rows move PENDING -> SENT / FAILED correctly; rows older than 7 days are purged.

T5. Settings — DONE
- Implement SettingsRepository and SettingsScreen with number validation and delay selector. Wire the delay into scheduling. (R3, R8)
- Done when: invalid numbers are rejected, settings persist across app restarts, and delayed sends are scheduled with the chosen delay (verified by a worker test).

T6. Main screen — DONE
- Implement header, enable switch, window selector, live log table with single-line ellipsis rows, empty state, ticking clock. (R7)
- Done when: new rows appear live, rows leave the 5-minute window without interaction, and the layout is correct on a small and a large screen.

T7. Permissions and onboarding — DONE
- Implement OnboardingScreen, permission handling, status line, deep link to app settings, restricted-settings guidance. (R9)
- Done when: denying or revoking a permission is reflected in the status line and nothing crashes.

T8. Reliability — DONE
- Implement HealthChecker, the battery-optimization prompt and status, and reboot-safe retries. (R5, R10)
- Done when: after a reboot with a pending retry, the message is still sent; the status line reflects battery-exemption state.

T9. Hardening and privacy — DONE
- Audit against CLAUDE.md Hard rules: no body logging, snippet-only storage, no INTERNET permission, allowBackup=false, Clear log action. (R11)
- Done when: a written audit checklist in the final report shows each rule with the file and line that satisfies it.

T10. End-to-end verification — DONE
- Produce a release APK and a short test report against the real-phone checklist.
- Done when: every checklist item has a pass/fail recorded.

T11. V1.1 cloud archive upload — DONE
- Move the full body into an upload outbox after a confirmed SMS send, add WorkManager network retries, and connect Settings to the private Cloudflare Worker.
- Done when Room migration preserves V1 data, forwarding remains independent of upload failures, duplicate uploads are safe, queued bodies survive reboot, Clear log removes local queued bodies, and tests/build pass.

T12. V1.2 specification and UI decisions — DONE (documentation only)
- Record the approved main-screen status rows, Settings-only route switches, separate keyword lists, independent routes, one-attempt web delivery, and cancellation of queued forwards when forwarding is switched Off. Update requirements, design, task plan, and project rules; make V1/V1.1 conflicting descriptions historical.
- Done when the four canonical documents agree and are shown to the user for review. Do not change app code, build an APK, commit, or push as part of T12.

T13. Route settings and UI — DONE (intermediate build; not released)
- Persist each route's switch, All SMS/Matching keywords mode, and comma-separated keywords. Keep destination/delay for forwarding and masked write token for web archiving. Build the two Settings sections and main-screen status rows, with validation and one Save action. (R12, R17)
- Done when valid configurations survive restart, invalid enabled routes cannot be saved, and the main screen labels its log Forwarding activity. Do not change receive routing yet; do not publish a partially implemented build.

T14. Pure route matching — DONE (unit tested; not yet connected to receiving)
- Implement and test comma parsing, body-only case-insensitive any-keyword matching, and independent forwarding/web decisions. (R13, R14)
- Done when unit tests cover all route combinations, phrases, whitespace/empty entries, mixed case, empty bodies, non-Latin text, and sender-only keyword occurrences.

T15. SMS forwarding route — DONE (unit and emulator tested; not released)
- Apply the forwarding decision to newly received SMS while preserving formatting, delay, retry, loop prevention, deduplication, and log behavior. Cancel queued sends/retries and delete pending bodies when forwarding is saved as Off; show unsent rows as CANCELLED. (R14, R16)
- Done when matched SMS follow the existing send path, unmatched/web-only SMS create no forwarding work or log row, and queued forward cancellation is verified without altering completed SENT rows.

T16. Independent one-attempt web route — DONE (unit and emulator tested; not released)
- Decouple Cloudflare POST from successful forwarding. For web matches, use the original SMS and one immediate best-effort attempt; skip when offline and discard all errors. Remove V1.1 network constraints, retry queue, sweep, and startup replay. Delete transient web bodies when disabled or orphaned; migrate V1.1 queued uploads away while preserving forwarding data. (R15, R16)
- Done when web-only and loop-skipped-forward messages can reach the API, forwarding outcomes do not affect web delivery, offline/errors cause no retry, and migration/cleanup tests pass.

T17. V1.2 end-to-end verification and release — DONE (automated verification and signed artifact; real-phone checks pending)
- Test route combinations, keyword filtering, switch-off behavior, privacy, upgrade, and API failure on a disposable emulator. Build and verify a release APK with the existing local signing key and document real-phone checks. (R17)
- Done when automated checks and a versioned verification report are complete. Show the result to the user; do not install, commit, push, or deploy without approval for those actions.

T18. Visible save feedback and combined route activity — DONE (automated verification and signed review APK; real-phone checks pending)
- Make Settings save, validation, and failure feedback visible without scrolling. Show one activity row for either selected route with separate SMS and Web statuses. Keep one-attempt web behavior, snippet-only seven-day metadata, and a data-preserving Room migration. (R18, R19)
- Done when unit and emulator tests verify feedback, route status transitions, web-only rows, privacy, retention, and V1.2-to-V1.3 migration; build a reviewable APK. Do not install, commit, or push without separate approval.

T19. V1.4 optional app source metadata — DONE (unit/emulator tests and signed review APK; rollout pending)
- Add optional manual source phone/name settings, validation and persistence; snapshot values in transient web work, send supplied values in JSON, and distinguish configured sources in web idempotency keys. Add a preserving Room migration and verify old migrations, settings, routing, payloads and one-attempt semantics.
- Complete unit/emulator testing and a signed review build before moving to the reader project. Do not install, deploy, commit or push as part of this local change.

T20. V1.5 offline web upload queue and Queue tab — IN PROGRESS
- Update CLAUDE.md and specs first. Persist every web-route match; drain oldest first through a network-constrained unique worker with the existing API and idempotency key; keep the newest 50 for 7 days; survive reboot; delete the queue when Web is Off; leave it alone on Clear log; add Home/Queue/Settings bottom tabs and a read-only Queue list. (R21–R23)
- Done when unit and Android tests are updated, the project builds, JVM tests pass, androidTest sources compile (emulator run if one is available), and the branch is pushed. Do not merge, release or deploy.
