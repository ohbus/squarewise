# ERRC-15: Implement servlet Problem mapper and containment

## Context & Parent Milestone

- **Parent Workstream**: [`ERRC-01: Design six-digit domain error-code migration`](ERRC-01.md)
- **Phase**: Phase 3 — Transport boundaries
- **Implementation Strategy**: [`docs/architecture/error-code-refactoring.md`](../../architecture/error-code-refactoring.md)

## Objective

Implement the additive Spring Web MVC servlet Problem Details mapper and global exception advice in `libs/errors`. Replace reflection-based or text-leaking handlers with direct, constant-time translation from `SquarewiseException` and standard Spring web exceptions to RFC 9457 `ProblemDetails` models containing legacy `code` and optional `numericCode` and `errorName`. Provide static container fallbacks for failures outside dispatcher servlets.

## Dependencies

- Preceding: [`ERRC-05`](ERRC-05.md), [`ERRC-12`](ERRC-12.md), [`ERRC-14`](ERRC-14.md)

## Owned Paths

- `docs/tasks/details/ERRC-15.md`
- `libs/errors/src/main/kotlin/com/subhrodip/squarewise/errors/web/`
- `libs/errors/src/test/kotlin/com/subhrodip/squarewise/errors/web/`

## Architecture & Design Patterns

- **Adapter Pattern (Web Exception to RFC 9457 Problem Details)**: Adapts typed domain exceptions and framework exceptions into compliant JSON Problem responses.
- **Chain of Responsibility via Explicit Handlers**: Explicit Spring `@ExceptionHandler` methods mapped to concrete exception types (e.g. `MethodArgumentNotValidException`, `HttpMessageNotReadableException`, `HttpRequestMethodNotSupportedException`), eliminating vague wildcard catches.
- **Fail-Closed Fallback Pattern**: Any unhandled non-fatal exception is caught at the root boundary, logged with correlation ID, and converted to a safe, generic 500 ProblemDetails without exposing internal messages.
- **Single Responsibility & Dedicated Files**: Separate classes for Problem DTO (`ProblemDetailsDto.kt`), Violation DTO (`ViolationDto.kt`), MVC Advice (`GlobalErrorAdvice.kt`), and Container Fallback (`StaticErrorPageFilter.kt`).

## Common Libraries & Framework Integration

- **`libs/errors`**: Houses the global web advice and problem mappers.
- **`libs/observability`**: MDC request ID extraction and error logging.
- **Spring Web MVC**: Controller advice and error attributes.

## Technical Requirements & Deliverables

1. **`ProblemDetailsDto.kt`**:
   - Strongly-typed DTO adhering to `contracts/errors/problem.schema.json`:
     ```kotlin
     data class ProblemDetailsDto(
         val type: URI,
         val title: String,
         val status: Int,
         val detail: String,
         val instance: String,
         val code: String, // legacy symbolic code
         val numericCode: String? = null,
         val errorName: String? = null,
         val timestamp: Instant = Instant.now(),
         val requestId: String,
         val source: String,
         val violations: List<ViolationDto>? = null,
     )
     ```
2. **`GlobalErrorAdvice.kt`**:
   - Centralized `@RestControllerAdvice`:
     - `@ExceptionHandler(SquarewiseException::class)`: Translates domain exception directly from preconstructed definition.
     - `@ExceptionHandler(MethodArgumentNotValidException::class)`: Maps bean validation errors to 400 ProblemDetails with bounded violation list.
     - `@ExceptionHandler(HttpMessageNotReadableException::class)`: Maps malformed JSON syntax to safe, static 400 ProblemDetails without echoing malformed payloads.
     - `@ExceptionHandler(Exception::class)`: Fallback handler returning safe 500 error (`999901` / `INTERNAL_SERVER_ERROR`).
3. **`StaticErrorPageFilter.kt`**:
   - Filter handling 404/500 errors occurring outside Spring MVC DispatcherServlet (e.g. Tomcat/Jetty routing, filter chain crashes).
4. **Integration & Adversarial Leak Tests (`GlobalErrorAdviceTest.kt`)**:
   - Test that raw SQL exceptions, class names, or stack traces never appear in JSON response.
   - Test additive fields `numericCode` and `errorName` are populated accurately.
   - Test required headers (`WWW-Authenticate`, `Allow`, `Retry-After`).

## Acceptance Criteria

1. Every DTO, filter, and advice class resides in its own single file under `com.subhrodip.squarewise.errors.web`.
2. Public problem responses strictly conform to `contracts/errors/problem.schema.json`.
3. Under no circumstance does `Throwable.message` or root-cause text leak into response `detail`.
4. Additive fields (`numericCode`, `errorName`) are emitted alongside the legacy `code`.
5. Adversarial tests confirm zero PII or SQL syntax leakage under simulated database failures.
6. Validation commands execute cleanly.

## Validation Commands

```powershell
./gradlew.bat :libs:errors:test :libs:errors:jacocoTestReport --rerun-tasks --no-daemon
uv run python tools/contracts/validate.py
git diff --check
```

## Evidence Expectations

- JaCoCo test report showing >95% coverage on web error mapping classes.
- Test assertions proving JSON output matches the additive Problem Details schema.

## Rollout & Rollback Strategy

- Replaces existing `GlobalErrorHandler` in `libs/errors` with backwards-compatible additive handler.
- Rollback: Revert advice changes if response formatting regressions are detected.
