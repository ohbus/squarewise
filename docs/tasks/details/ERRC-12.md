# ERRC-12: Implement governed exception and typed-diagnostics contracts

## Context & Parent Milestone

- **Parent Workstream**: [`ERRC-01: Design six-digit domain error-code migration`](ERRC-01.md)
- **Phase**: Phase 2 — Static error core, no runtime discovery
- **Implementation Strategy**: [`docs/architecture/error-code-refactoring.md`](../../architecture/error-code-refactoring.md)

## Objective

Implement the minimal governed base exception `SquarewiseException`, bounded diagnostics contracts, sealed override families, and fatal throwable classifiers in `libs/errors`. Replace arbitrary string-message exception throwing with strongly-typed, compile-time checked domain exceptions. Strictly enforce SOLID file separation, placing every exception, diagnostic model, and classifier in its own file.

## Dependencies

- Preceding: [`ERRC-10`](ERRC-10.md), [`ERRC-11`](ERRC-11.md)

## Owned Paths

- `docs/tasks/details/ERRC-12.md`
- `libs/errors/src/main/kotlin/com/subhrodip/squarewise/errors/exceptions/`
- `libs/errors/src/main/kotlin/com/subhrodip/squarewise/errors/diagnostics/`
- `libs/errors/src/test/kotlin/com/subhrodip/squarewise/errors/exceptions/`

## Architecture & Design Patterns

- **Exception Shielding Pattern**: Base exception ensures raw error messages and nested cause texts are shielded from public mappers. `Throwable.message` is populated exclusively for internal log context; public Problem Details read strictly from `ErrorDefinition.title` and `safeDetail`.
- **Constrained Strategy / Sealed Family Override**: Useful leaf exceptions have fixed, baked-in default definitions. Exceptions allowing override require sealed definition families, preventing callers from injecting arbitrary, uncataloged error definitions.
- **Fail-Fast Fatal Classifier Pattern**: Distinguishes recoverable domain exceptions from fatal JVM errors (`VirtualMachineError`, `ThreadDeath`, `LinkageError`, `CancellationException`, `InterruptedException`), ensuring fatal conditions are re-thrown rather than caught.
- **Bounded Diagnostic Value Objects**: Structured diagnostic payloads (e.g. `FieldViolation`, `ResourceIdentifier`) implement strict string length limits and redaction markers.

## Common Libraries & Framework Integration

- **`libs/errors`**: Core exception hierarchy and fatal error classifier.
- **`libs/observability`**: Diagnostics formatted for structured log enrichment without PII leakage.

## Technical Requirements & Deliverables

1. **`SquarewiseException.kt`**:
   - Governed abstract base class:
     ```kotlin
     package com.subhrodip.squarewise.errors.exceptions

     import com.subhrodip.squarewise.errors.code.ErrorDefinition
     import com.subhrodip.squarewise.errors.diagnostics.ErrorDiagnostics

     /**
      * Governed base exception for all domain and platform failures across Squarewise.
      *
      * @property definition Authoritative error definition from the domain catalog.
      * @property diagnostics Bounded structured diagnostic context.
      * @param cause Underlying root cause throwable, shielded from public responses.
      */
     abstract class SquarewiseException protected constructor(
         val definition: ErrorDefinition,
         val diagnostics: ErrorDiagnostics = ErrorDiagnostics.EMPTY,
         cause: Throwable? = null,
     ) : RuntimeException(definition.errorName, cause)
     ```
2. **`ErrorDiagnostics.kt` & Implementations (Dedicated Files)**:
   - `ErrorDiagnostics.kt`: Marker interface for safe diagnostic key-value pairs.
   - `EmptyDiagnostics.kt`: Zero-allocation singleton for empty diagnostics.
   - `MapDiagnostics.kt`: Bounded diagnostic map with redaction and max-entry bounds (max 10 entries).
3. **Fatal Throwable Classifier (`FatalErrorClassifier.kt`)**:
   - Utility identifying non-recoverable throwables:
     ```kotlin
     object FatalErrorClassifier {
         fun isFatal(throwable: Throwable): Boolean = when (throwable) {
             is VirtualMachineError,
             is ThreadDeath,
             is LinkageError,
             is InterruptedException -> true
             else -> throwable.javaClass.name == "kotlinx.coroutines.CancellationException"
         }
     }
     ```
4. **Governed Leaf Exceptions (Dedicated Files)**:
   - `DomainValidationException.kt`: Generic validation failure with list of field violations.
   - `EntityNotFoundException.kt`: Resource missing failure with anti-enumeration protection.
   - `ConcurrencyConflictException.kt`: Version conflict / optimistic locking failure.
5. **Unit & Boundary Tests (`SquarewiseExceptionTest.kt`, `FatalClassifierTest.kt`)**:
   - Assert fatal classifier correctly flags JVM errors.
   - Assert `SquarewiseException` preserves cause while exposing immutable definition.

## Acceptance Criteria

1. Every exception and diagnostic class resides in its own isolated file.
2. `SquarewiseException` cannot be instantiated with null or arbitrary uncataloged error definitions.
3. Fatal JVM errors, thread interrupts, and coroutine cancellations are never classified as recoverable application exceptions.
4. Diagnostics enforce bounded string lengths and reject sensitive field names (`password`, `token`, `secret`).
5. Unit tests pass with 100% coverage on exception hierarchy and classifiers.
6. Validation commands execute cleanly.

## Validation Commands

```powershell
./gradlew.bat :libs:errors:test :libs:errors:jacocoTestReport --rerun-tasks --no-daemon
uv run python tools/contracts/validate.py
git diff --check
```

## Evidence Expectations

- JaCoCo test report showing 100% coverage of `com.subhrodip.squarewise.errors.exceptions.*`.
- Unit test verification that `Throwable.message` is never exposed on the public definition interface.

## Rollout & Rollback Strategy

- Additive library implementation in `libs/errors`.
- Unused by production controllers until Phase 3 and Phase 4.
- Rollback: Revert exception classes if API adjustments are needed.
