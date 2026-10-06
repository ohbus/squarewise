# ERRC-14: Implement logging, metrics, and trace adapter

## Context & Parent Milestone

- **Parent Workstream**: [`ERRC-01: Design six-digit domain error-code migration`](ERRC-01.md)
- **Phase**: Phase 2 — Static error core, no runtime discovery
- **Implementation Strategy**: [`docs/architecture/error-code-refactoring.md`](../../architecture/error-code-refactoring.md)

## Objective

Implement the structured observability adapter in `libs/observability` and `libs/errors` for logging, Prometheus metrics, and OpenTelemetry tracing. Ensure that predictable 4xx errors log concisely without stack traces, unexpected 5xx errors log once with sanitized stacks and correlation context, and metric dimensions maintain strictly bounded cardinality. Follow strict SOLID file separation.

## Dependencies

- Preceding: [`ERRC-11`](ERRC-11.md), [`ERRC-12`](ERRC-12.md)

## Owned Paths

- `docs/tasks/details/ERRC-14.md`
- `libs/observability/src/main/kotlin/com/subhrodip/squarewise/observability/errors/`
- `libs/observability/src/test/kotlin/com/subhrodip/squarewise/observability/errors/`
- `libs/errors/src/main/kotlin/com/subhrodip/squarewise/errors/observability/`

## Architecture & Design Patterns

- **Decorator & Adapter Pattern**: Adapts `ErrorDefinition` and `SquarewiseException` instances into structured SLF4J MDC attributes, Micrometer meters, and OpenTelemetry span attributes.
- **Log-Once Pattern**: Flags processed exceptions to guarantee an error is logged exactly once at its final owning boundary, eliminating duplicate log spam across layered catch-rethrow blocks.
- **Cardinality Control Pattern**: Metric tag values are strictly constrained to catalog enums (`numericCode`, `errorName`, `category`, `domain`, `status`). Never emit user IDs, group IDs, URLs, or exception messages as metric tag dimensions.
- **Privacy by Design & Sanitization**: Structured logs redact sensitive fields and scrub stack frames of potential secrets.

## Common Libraries & Framework Integration

- **`libs/observability`**: Micrometer `MeterRegistry`, OpenTelemetry tracing, Logback JSON encoder.
- **`libs/errors`**: Exception boundary and definition metadata.

## Technical Requirements & Deliverables

1. **`ErrorLogger.kt`**:
   - Centralized logging utility enforcing differential log levels and stack emission:
     ```kotlin
     object ErrorLogger {
         private val log = LoggerFactory.getLogger(ErrorLogger::class.java)

         fun logError(exception: Throwable, definition: ErrorDefinition, requestId: String) {
             when (definition.severity) {
                 ErrorSeverity.INFO -> log.info("Client error [{}] {}: {}", definition.numericCode.value, definition.errorName, definition.safeDetail)
                 ErrorSeverity.WARN -> log.warn("Expected domain warning [{}] {}: {}", definition.numericCode.value, definition.errorName, definition.safeDetail)
                 ErrorSeverity.ERROR, ErrorSeverity.CRITICAL -> log.error("Internal failure [{}] {} (requestId={})", definition.numericCode.value, definition.errorName, requestId, exception)
             }
         }
     }
     ```
2. **`ErrorMetricsRecorder.kt`**:
   - Micrometer metrics recorder maintaining bounded error counters:
     - Metric: `squarewise_errors_total`
     - Tags: `domain`, `category`, `code` (numeric), `status` (HTTP), `error_name`.
     - Hard guarantee: Total unique tag permutations bounded by catalog size (< 1,000 combinations).
3. **`ErrorTraceEnricher.kt`**:
   - Enriches active OpenTelemetry Span with standard attributes:
     - `error.code` = `213201`
     - `error.name` = `GROUP_NOT_FOUND`
     - `error.type` = `MISSING`
     - Sets span status to `ERROR` only for 5xx / unexpected failures.
4. **Unit Tests (`ErrorLoggerTest.kt`, `ErrorMetricsRecorderTest.kt`)**:
   - Test that 4xx errors log no stack traces.
   - Test that 5xx errors log with stack trace and request ID.
   - Test that metric registry enforces tag cardinality and registers counts accurately.

## Acceptance Criteria

1. Every class and utility resides in its own dedicated file under `com.subhrodip.squarewise.observability.errors`.
2. Expected client errors (4xx) produce concise one-line log entries without stack traces.
3. Unexpected server errors (5xx) log full root-cause stack traces with correlation request ID.
4. Metric dimensions are provably finite and bounded; no dynamic entity IDs or URLs enter meter tags.
5. MDC context is cleanly managed and purged after request lifecycle completes.
6. Validation commands execute cleanly.

## Validation Commands

```powershell
./gradlew.bat :libs:observability:test :libs:observability:jacocoTestReport --rerun-tasks --no-daemon
./gradlew.bat :libs:errors:test :libs:errors:jacocoTestReport --rerun-tasks --no-daemon
uv run python tools/contracts/validate.py
git diff --check
```

## Evidence Expectations

- Test logs verifying absence of stack traces for 4xx conditions and presence for 5xx.
- Mock MeterRegistry verification demonstrating exact tag dimensions.

## Rollout & Rollback Strategy

- Additive observability adapter.
- Zero production impact until wired into web mappers in Phase 3.
- Rollback: Revert library classes if metrics or logging behavior needs recalibration.
