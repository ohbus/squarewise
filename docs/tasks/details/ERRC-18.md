# ERRC-18: Implement messaging/background boundary toolkit

## Context & Parent Milestone

- **Parent Workstream**: [`ERRC-01: Design six-digit domain error-code migration`](ERRC-01.md)
- **Phase**: Phase 3 — Transport boundaries
- **Implementation Strategy**: [`docs/architecture/error-code-refactoring.md`](../../architecture/error-code-refactoring.md)

## Objective

Implement the messaging error boundary toolkit in `libs/errors` and shared messaging configurations for RabbitMQ consumers, Transactional Outbox relays, and scheduled background workers. Provide fatal-safe error handling templates that distinguish transient retries from poison dead-letters, eliminate raw `catch (Throwable)` blocks, and record terminal failures with correlation context.

## Dependencies

- Preceding: [`ERRC-08`](ERRC-08.md), [`ERRC-12`](ERRC-12.md), [`ERRC-14`](ERRC-14.md)

## Owned Paths

- `docs/tasks/details/ERRC-18.md`
- `libs/errors/src/main/kotlin/com/subhrodip/squarewise/errors/async/`
- `libs/errors/src/test/kotlin/com/subhrodip/squarewise/errors/async/`

## Architecture & Design Patterns

- **Template Method Pattern (`AsyncOperationTemplate`)**: Enforces a rigid lifecycle skeleton for asynchronous execution: start span -> bind MDC correlation -> execute operation -> handle failure -> clean up context.
- **Strategy Pattern for Retry & Dead-Letter Disposition**: Decouples failure classification from message acknowledgement decisions (`ACK`, `NACK_REQUEUE`, `DEAD_LETTER`).
- **Poison-Pill Isolation Pattern**: Non-retryable serialization or invariant errors are routed immediately to the dead-letter exchange, avoiding infinite poison loops.
- **Fatal Re-throw Pattern**: Intercepts `FatalErrorClassifier.isFatal(throwable)` and unconditionally re-throws to allow the JVM/container to restart without acknowledging the corrupted message.

## Common Libraries & Framework Integration

- **`libs/errors`**: Async execution template, disposition strategy, and dead-letter records.
- **`libs/observability`**: Message correlation logging and retry counter metrics.
- **Spring AMQP / RabbitMQ**: Rabbit listener error handlers and custom fatal exception evaluators.

## Technical Requirements & Deliverables

1. **`AsyncExecutionTemplate.kt`**:
   - Reusable execution wrapper:
     ```kotlin
     class AsyncExecutionTemplate(
         private val metricsRecorder: ErrorMetricsRecorder,
         private val fatalClassifier: FatalErrorClassifier = FatalErrorClassifier,
     ) {
         fun <T> execute(context: AsyncContext, block: () -> T): AsyncResult<T> {
             // binds MDC, catches non-fatal exceptions, classifies retry, records metrics
         }
     }
     ```
2. **`MessageDispositionStrategy.kt`**:
   - Determines consumer action based on `ErrorDefinition.retryPolicy` and attempt count:
     - `RETRY_AFTER` -> Exponential backoff requeue (up to max 3 attempts).
     - `NEVER` / Poison pill -> Reject without requeue (moves to dead-letter exchange).
     - Fatal JVM error -> Re-throw unchecked; do not ACK or DLQ.
3. **`DeadLetterRecordBuilder.kt`**:
   - Constructs structured JSON failure payloads conforming to `contracts/events/dead-letter.schema.json`.
4. **Integration & Fault Tests (`AsyncExecutionTemplateTest.kt`)**:
   - Test that fatal JVM errors (`OutOfMemoryError`) are re-thrown and never caught.
   - Test that transient errors increment retry counts and trigger requeue.
   - Test that poison payloads trigger dead-letter records.

## Acceptance Criteria

1. Every template, strategy, and builder class resides in its own single file under `com.subhrodip.squarewise.errors.async`.
2. Fatal throwables are never swallowed, logged as handled, or acknowledged.
3. Poison messages are dead-lettered with structured failure diagnostics after exactly 1 attempt.
4. Transient failures retry with bounded exponential backoff up to 3 times before dead-lettering.
5. Unit and integration tests achieve 100% branch coverage across all execution paths.
6. Validation commands execute cleanly.

## Validation Commands

```powershell
./gradlew.bat :libs:errors:test :libs:errors:jacocoTestReport --rerun-tasks --no-daemon
uv run python tools/contracts/validate.py
git diff --check
```

## Evidence Expectations

- JaCoCo test report showing 100% coverage on async error boundary classes.
- Test logs verifying fatal error propagation and poison message isolation.

## Rollout & Rollback Strategy

- Shared library utility in `libs/errors`.
- Consumed by Notifications, Expense Core outbox, and recurring schedulers in Phase 4.
- Rollback: Revert library additions if consumer integration issues arise.
