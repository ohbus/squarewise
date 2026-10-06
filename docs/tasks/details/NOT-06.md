# NOT-06: SMTP email dispatch adapter in Notifications service

Implement real SMTP email dispatch adapter in `app/notifications` connecting to local Mailpit (`localhost:1025`).

## Acceptance criteria

- `EmailDispatcher` component sends notification emails via Spring `JavaMailSender` to configured host and port.
- Handles transient network/socket failures with retryable exception, while marking invalid recipient/poison pill messages as permanent failures.
- Includes integration/unit tests with mock `JavaMailSender` verifying recipient, subject, text, and exception handling.

## Implementation details

- **Configuration (`EmailProperties.kt`)**:
  - `host: "localhost"`, `port: 1025` (Mailpit default SMTP), `fromAddress: "notifications@squarewise.local"`, `enabled: true`.
- **Client & Dispatcher (`EmailDispatcher.kt`, `JavaMailSender.kt`)**:
  - `SimpleMailMessage` and `JavaMailSender` contract with socket-based SMTP client.
  - `EmailDispatcher`: validates email format, retries transient socket/network errors up to 3 attempts, marks validation failures as `PERMANENT_FAILURE`, and returns `DELIVERED` on success.
- **Tests (`EmailDispatcherTest.kt`)**:
  - Tests successful dispatch, transient retry and eventual failure, transient recovery, disabled skipped dispatch, and permanent failure for malformed emails.

## Verification evidence

- `./gradlew :app:notifications:test --no-daemon`: 29 tests passed.
- `git diff --check`: passed cleanly.
