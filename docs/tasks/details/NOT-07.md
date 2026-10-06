# NOT-07: Connect notification consumer to email dispatch

## Objective

Connect `NotificationConsumerService` with `EmailDispatcher` so that incoming notification events trigger email delivery when enabled in recipient preferences.

## Acceptance criteria

- `NotificationConsumerService` checks recipient's email notification preferences.
- If email is enabled and an email address is available, dispatches an email via `EmailDispatcher`.
- Dispatch failure does not roll back inbox insertion (transient email retries handled, delivery outcome recorded or logged).
- Full unit test coverage in `NotificationConsumerServiceTest.kt`.

## Implementation details

- **Consumer & Dispatcher Wiring (`NotificationEventConsumer.kt`, `NotificationConsumerService.kt`)**:
  - Defined `NotificationConsumerService` and `NotificationPreferenceStore` type aliases resolving to `NotificationEventConsumer` and `PreferenceStore` in `NotificationConsumerService.kt`.
  - Injected `PreferenceStore` and `EmailDispatcher` into `NotificationEventConsumer`.
  - Enriched `NotificationEvent` with convenience accessors `recipientId`, `title`, `body`, and optional `recipientEmail`.
  - When an event is applied (`NotificationConsumptionOutcome.APPLIED`), checks recipient preference via `preferenceStore.get(recipientId)`. If preferences are absent/null or `emailEnabled` is true, proceeds to dispatch.
  - Resolves email address from `event.recipientEmail`, or `event.subject` if it contains `@`, or defaults to `${recipientId}@squarewise.local`.
  - Formats email subject as `Notification: ${event.title}` and body as `${event.body}`.
  - Calls `emailDispatcher.send(recipientEmail, subject, body)` outside the transactional boundary.
  - Wraps preference lookup and email dispatch in comprehensive try-catch blocks and checks `EmailDeliveryOutcome` so that neither unexpected runtime exceptions nor delivery failures can cause the database transaction or inbox item storage to roll back.
- **Unit and Integration Tests (`NotificationConsumerServiceTest.kt`)**:
  - Verified email dispatch when preferences have `emailEnabled = true` with default `${recipientId}@squarewise.local` address.
  - Verified email dispatch using explicit `recipientEmail` and email-formatted subject.
  - Verified email dispatch is skipped when `emailEnabled = false`.
  - Verified default dispatch behavior when preferences return null or preference store throws an exception.
  - Verified email dispatch failure (exception or permanent/retryable failure outcome) does not fail inbox consumption or throw unhandled exceptions.
  - Verified duplicate events do not trigger email dispatch.

## Owned paths

- `app/notifications/src/main/kotlin/com/subhrodip/squarewise/notifications/`
- `app/notifications/src/test/kotlin/com/subhrodip/squarewise/notifications/`
- `docs/tasks/details/NOT-07.md`

## Verification evidence

- `./gradlew :app:notifications:test --no-daemon`: 38 tests completed successfully (including all 9 new unit tests in `NotificationConsumerServiceTest` and existing integration tests).
- `git diff --check`: passed with zero whitespace or formatting errors.
