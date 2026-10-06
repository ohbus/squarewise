# ERRC-21: Migrate Notifications definitions and failures

## Context & Parent Milestone

- **Parent Workstream**: [`ERRC-01: Design six-digit domain error-code migration`](ERRC-01.md)
- **Phase**: Phase 4 — Bounded-context migration
- **Implementation Strategy**: [`docs/architecture/error-code-refactoring.md`](../../architecture/error-code-refactoring.md)

## Objective

Migrate all production error throw sites, notification delivery adapters, RabbitMQ event consumers, and SMTP email dispatch routines in the Notifications service (`app/notifications`) to use strongly-typed definitions from `NotificationErrors`. Eliminate all 7 legacy `ApplicationException(ErrorCode...)` call sites, eliminate unsafe broad `catch (Throwable)` blocks, and enforce fail-closed delivery policies.

## Dependencies

- Preceding: [`ERRC-15`](ERRC-15.md), [`ERRC-18`](ERRC-18.md)
- Notifications guide: [`docs/architecture/errors/notifications.md`](../../architecture/errors/notifications.md)

## Owned Paths

- `docs/tasks/details/ERRC-21.md`
- `app/notifications/src/main/kotlin/com/subhrodip/squarewise/notifications/`
- `app/notifications/src/test/kotlin/com/subhrodip/squarewise/notifications/`
- `tools/qa/error_hygiene_allowlist.yaml` (prune Notifications entries)

## Architecture & Design Patterns

- **Domain-Driven Design (Notification Bounded Context)**: Domain 3 errors strictly cover Notifications: `31xxxx` Inbox, `32xxxx` Delivery, `33xxxx` Preferences, `34xxxx` Email.
- **Idempotent Consumer & Poison-Pill Isolation**: Wrap RabbitMQ message listeners in `AsyncExecutionTemplate` from `ERRC-18`, ensuring fatal JVM errors crash the container cleanly, transient SMTP timeouts retry with backoff, and malformed JSON payloads dead-letter without crashing queues.
- **Eliminate Broad `catch (Throwable)`**: Replace all 4 audited `catch (Throwable)` blocks in notification consumer adapters with targeted non-fatal exception handling.
- **Strict SOLID File Separation**: Every new exception, consumer error handler, and SMTP delivery adapter is placed in its own dedicated file.

## Common Libraries & Framework Integration

- **`libs/errors`**: `NotificationErrors`, `SquarewiseException`, `AsyncExecutionTemplate`.
- **`libs/observability`**: Outbox message correlation and dead-letter telemetry.
- **Spring AMQP & JavaMail**: RabbitMQ consumer error routing and Mailpit/SMTP dispatch.

## Technical Requirements & Deliverables

1. **Replace 7 Production `ApplicationException` Usages**:
   - Inbox: `NOTIFICATION_NOT_FOUND` (`313201`), `NOTIFICATION_ALREADY_READ` (`314401`).
   - Delivery: `DELIVERY_JOB_FAILED` (`325701`), `CHANNEL_DISABLED` (`325501`).
   - Preferences: `PREFERENCE_UPDATE_INVALID` (`331101`).
   - Email: `EMAIL_SMTP_DISPATCH_FAILED` (`345701`), `TEMPLATE_RENDER_FAILED` (`341101`).
2. **Refactor Message Consumer Error Handling**:
   - Refactor `AuthEmailConsumer` and `GroupNotificationConsumer` to remove raw `catch (Throwable)` blocks.
   - Use `AsyncExecutionTemplate` to handle retries and dead-letter queue routing.
3. **Prune Error Hygiene Allowlist**:
   - Remove all Notifications entries from `tools/qa/error_hygiene_allowlist.yaml`.
4. **Service Test Suite Updates**:
   - Add crash/fatality integration tests asserting that fatal errors trigger process restart and poison messages trigger dead-letter routing.

## Acceptance Criteria

1. Zero references to legacy `ErrorCode` or generic `ApplicationException` remain in `app/notifications/src/main/`.
2. All 4 broad `catch (Throwable)` sites are replaced with fatal-safe async templates.
3. Transient SMTP failures retry up to 3 times before dead-lettering.
4. Notifications test suite passes cleanly with 100% test success.
5. All verification commands execute cleanly.

## Validation Commands

```powershell
./gradlew.bat :app:notifications:test :app:notifications:jacocoTestReport --rerun-tasks --no-daemon
uv run python tools/qa/check_error_hygiene.py
uv run python tools/contracts/validate.py
git diff --check
```

## Evidence Expectations

- JaCoCo test report showing complete coverage across Notifications service.
- Verification log from `check_error_hygiene.py` confirming zero residual Notifications debt.

## Rollout & Rollback Strategy

- Deployed to Notifications service.
- Fully compatible with existing event envelopes and REST inbox endpoints.
- Rollback: Standard Git revert of service branch if regressions occur.
