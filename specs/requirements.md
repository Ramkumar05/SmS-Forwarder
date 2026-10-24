# Requirements

## Goal
When an SMS arrives on this phone, forward it to a configured number within seconds, and let me see what was forwarded.

## Non-goals
- No Google Play compliance work, no default-SMS-app features, no reading old messages.
- No cloud backend, no accounts, no network features.
- No MMS, RCS, or media forwarding.
- No tablet or landscape-specific layouts in V1.

## Requirements (each has testable acceptance criteria)

R1. Receive SMS
- Given the app has RECEIVE_SMS, when an SMS arrives (even with the app closed), then the app captures sender, full body and receive time.
- Given a long message arrives as multiple parts, then the parts are combined into one message before forwarding.

R2. Forward SMS
- Given forwarding is enabled and a valid target number is saved, when a message is captured, then the same message is sent to the target number.
- DEFAULT format: "FWD from <original sender>: <body>". Bodies longer than one SMS segment are split with SmsManager.divideMessage and sent as multipart.
- DEFAULT: forward all messages (no filtering in V1). Keep a ForwardPolicy interface so a sender allowlist can be added later without restructuring.

R3. Send delay setting
- Settings offer Instant (DEFAULT), 15 seconds, 30 seconds.
- Given a delay is chosen, when a message is captured, then sending is scheduled after that delay. Instant means as soon as possible.

R4. Loop prevention
- Given a captured message whose sender matches the target number (after normalization), then it is NOT forwarded and is logged as SKIPPED_LOOP.

R5. Retry
- Given a send fails, then it is retried up to 3 times with exponential backoff starting at 30 seconds.
- Given all attempts fail, then the log status becomes FAILED. Given a send succeeds, status becomes SENT.
- Duplicate delivery of the same incoming SMS must not schedule a second forward (idempotent by normalized sender + SMS timestamp + body hash). If the phone dies after sending but before recording SENT, a retry may send it again; this rare duplicate is accepted so the message is not lost.
- After the user explicitly clears the log, prior deduplication history is erased. If Android redelivers that same SMS afterward, it may be forwarded again.

R6. Forward log
- Each handled message creates one log row: id, receivedAt, sender, snippet (first 100 characters), status (PENDING, SENT, FAILED, SKIPPED_LOOP), attempts, lastUpdatedAt.
- DEFAULT retention: rows older than 7 days are deleted automatically.

R7. Main screen
- Header shows: the forwarding target number (or "Not configured"), an enable/disable switch, and a status line showing permission and battery-optimization health.
- Below the header, a table with columns Time, From, Message, Status.
- The Message column shows a single line truncated with an ellipsis, so it adapts to screen width.
- DEFAULT time window is the last 5 minutes. A selector lets me switch to 1 hour or 24 hours.
- Empty state text when there are no rows in the window.
- The table updates live when new rows arrive.

R8. Settings
- I can enter the target number, with validation (digits, optional leading +, 10 to 15 digits). DEFAULT: a number without a country code is treated as India (+91).
- I can choose the send delay (R3).
- Saving an invalid number is blocked with an inline error.

R9. Permissions and onboarding
- On first launch, the app explains why it needs SMS permissions, then requests RECEIVE_SMS, SEND_SMS and POST_NOTIFICATIONS.
- If a permission is denied or restricted, the main screen status line says so and offers a button to open this app's system settings page.
- Include short on-screen guidance for the Android "restricted settings" step needed for sideloaded apps.

R10. Reliability
- The app prompts me to exempt it from battery optimization, and the status line shows whether it is exempt.
- Pending retries survive a phone reboot.
- DEFAULT: no foreground service in V1. Add one only if real-device testing shows missed messages.

R11. Privacy
- Satisfy every Hard rule in CLAUDE.md. A "Clear log" action deletes all log rows, cancels pending forwards, and deletes their stored bodies.

## SIM handling
DEFAULT: send using the system's default SMS subscription. Dual-SIM selection is out of scope for V1 but must not be blocked by the design.
