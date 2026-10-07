# ERRC-08: Define messaging and background error records

## Context & Parent Milestone

- **Parent Workstream**: [`ERRC-01: Design six-digit domain error-code migration`](ERRC-01.md)
- **Phase**: Phase 1 — Contract-first compatibility
- **Implementation Strategy**: [`docs/architecture/error-code-refactoring.md`](../../architecture/error-code-refactoring.md)

## Objective

Define machine-readable error contracts and terminal state schemas for asynchronous messaging (RabbitMQ), transactional outbox processing, background schedulers, and application startup routines. Ensure that event failures never mutate immutable domain events, that dead-letter / poison messages are accurately diagnosed, and that scheduled recurring tasks have explicit typed error lifecycle boundaries.

## Dependencies

- Preceding: [`ERRC-04: Allocate and review the complete error catalog`](ERRC-04.md)
- Notifications & Platform guides: [`docs/architecture/errors/notifications.md`](../../architecture/errors/notifications.md), [`docs/architecture/errors/platform-libraries.md`](../../architecture/errors/platform-libraries.md)

## Owned Paths

- `docs/tasks/details/ERRC-08.md`
- `contracts/events/error-envelope.schema.json`
- `contracts/events/dead-letter.schema.json`
- `docs/architecture/errors/messaging-background.md`

## Architecture & Design Patterns

- **Transactional Outbox Pattern**: Errors during outbox publication or event consumption must be recorded in distinct tracking/audit records without altering or corrupting the outbox table or source event envelopes.
- **Idempotent Consumer & Poison-Pill Isolation Pattern**: Distinguishes transient failures (retryable with backoff) from poison messages (fatal serialization/validation failure). Poison messages are dead-lettered immediately to prevent head-of-line blocking.
- **Bulkhead Pattern**: Isolate background workers and scheduled batch jobs (e.g. recurring expense runner, cleanup tasks) so individual item failures do not crash the batch or process.
- **Template Method Pattern**: Provide a governed execution template for message listener and scheduler invocations that enforces consistent try-catch, MDC propagation, telemetry, and terminal logging.

## Common Libraries & Framework Integration

- **`libs/errors`**: Supplies background error codes (`28xxxx` for Outbox, `32xxxx` for Delivery, `94xxxx` for Messaging).
- **`libs/observability`**: Correlation tokens and error counter metrics for RabbitMQ consumers.
- **`libs/db`**: Database transaction boundary handling during asynchronous message processing.

## Technical Requirements & Deliverables

1. **Dead-Letter & Failure Envelope Schema (`contracts/events/dead-letter.schema.json`)**:
   - Schema for messages routed to dead-letter exchanges (`dlx.squarewise`):
     ```json
     {
       "originalQueue": "notifications.email.dispatch",
       "failedAt": "2026-10-06T21:00:00Z",
       "attemptCount": 3,
       "numericCode": "345701",
       "errorName": "EMAIL_SMTP_DISPATCH_FAILED",
       "diagnosticReason": "Connection timeout to mail transport agent",
       "messagePayloadBase64": "..."
     }
     ```
2. **Scheduler Execution Lifecycle Error Contract**:
   - Define status records for background recurring runners (`COMPLETED`, `FAILED_RETRYABLE`, `FAILED_TERMINAL`).
   - Define error codes for scheduler lock contention (`254301`), occurrence calculation overflow (`253101`), and tenant isolation failures.
3. **Startup & Shutdown Failure Specifications**:
   - Formalize exit codes and fatal error records for database migration failure (`938101`), missing secret/keystore (`928101`), and broker connection failure (`948101`).

## Acceptance Criteria

1. `contracts/events/dead-letter.schema.json` is created, typed, and passes JSON schema validation.
2. Async error tracking explicitly prohibits mutating original business domain event schemas.
3. RabbitMQ retry, dead-letter, and poison-message thresholds are codified with bounded retry limits (max 3 retries with exponential backoff).
4. Schedulers and background workers have defined error outcomes without broad unhandled exception bubbling.
5. All verification commands execute cleanly.

## Validation Commands

```powershell
uv run python tools/contracts/validate.py
git diff --check
```

## Evidence Expectations

- Schema validation pass report for all event and background error schemas.
- Documented state transition diagram for event retry, failure, and dead-letter routing.

## Implementation Notes and Verification

- Added `error-envelope.schema.json` for separate, bounded failure metadata and
  `dead-letter.schema.json` for terminal event diagnosis/replay records.
- Added `docs/architecture/errors/messaging-background.md` defining immutable
  event handling, poison-message isolation, maximum three retries with bounded
  exponential backoff, scheduler outcomes, and fatal JVM propagation.
- `uv run --with jsonschema` validated both new schemas and representative valid
  records; `uv run python tools/contracts/validate.py` passed; `git diff --check`
  passed.

## Rollout & Rollback Strategy

- Contract specification milestone.
- Zero production code impact.
- Rollback: Revert JSON schemas if messaging topology changes are requested.
