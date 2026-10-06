# ERRC-10: Implement core value and metadata types

## Context & Parent Milestone

- **Parent Workstream**: [`ERRC-01: Design six-digit domain error-code migration`](ERRC-01.md)
- **Phase**: Phase 2 — Static error core, no runtime discovery
- **Implementation Strategy**: [`docs/architecture/error-code-refactoring.md`](../../architecture/error-code-refactoring.md)

## Objective

Implement the zero-allocation value class `ErrorCode`, core domain metadata enums, and the immutable `ErrorDefinition` interface in `libs/errors`. Enforce strict SOLID file separation by placing every enum, interface, and value class in its own dedicated file under `com.subhrodip.squarewise.errors.code`. Provide structured KDoc documentation and comprehensive unit tests.

## Dependencies

- Preceding: [`ERRC-09: Upgrade contract validation and breaking-change gates`](ERRC-09.md)

## Owned Paths

- `docs/tasks/details/ERRC-10.md`
- `libs/errors/src/main/kotlin/com/subhrodip/squarewise/errors/code/`
- `libs/errors/src/test/kotlin/com/subhrodip/squarewise/errors/code/`

## Architecture & Design Patterns

- **Value Object Pattern (`@JvmInline value class ErrorCode`)**: Wraps the six-character string without heap allocation overhead, providing constant-time decomposition into domain, module, layer, category, and sequence.
- **Interface Segregation Principle (ISP)**: Split definition capabilities into focused interfaces (`ErrorDefinition`, `RestErrorDefinition`, `GraphQLErrorDefinition`) so callers depend only on what they consume.
- **Single Responsibility Principle (SRP) & File Separation**: Each class, value object, interface, and enum resides in its own isolated `.kt` file.
- **Immutability & Zero Reflection**: Pure compile-time types with direct property access. Absolutely zero runtime reflection, classpath scanning, or regex parsing during normal execution.

## Common Libraries & Framework Integration

- **`libs/errors`**: Forms the foundational types consumed by all services and other shared libraries.
- **Kotlin 2.4 / JVM 25**: Leverages value classes and sealed hierarchies for optimal inlining and type safety.

## Technical Requirements & Deliverables

1. **`ErrorCode.kt`**:
   - Inlined value class wrapping `String`:
     ```kotlin
     @JvmInline
     value class ErrorCode(val value: String) {
         init {
             require(value.length == 6) { "ErrorCode must be exactly 6 characters: $value" }
             require(value.all { it in '1'..'9' || (it == '0' && false) }) // custom validation
         }
         val domainDigit: Int get() = value[0].digitToInt()
         val moduleDigit: Int get() = value[1].digitToInt()
         val layerDigit: Int get() = value[2].digitToInt()
         val categoryDigit: Int get() = value[3].digitToInt()
         val sequence: Int get() = value.substring(4, 6).toInt()
         val displayCode: String get() = "${value.substring(0, 2)}-${value[2]}-${value[3]}-${value.substring(4, 6)}"
     }
     ```
2. **Metadata Enums (Dedicated Files)**:
   - `ErrorDomain.kt`: Enum mapping domain digits 1 (Accounts), 2 (Expense Core), 3 (Notifications), 4 (BFF), 9 (Platform).
   - `ErrorLayer.kt`: Enum for layers 1 to 9 (Interface, Application, Domain, Persistence, Messaging, Integration, Security, Infrastructure, Shared Runtime).
   - `ErrorCategory.kt`: Enum for categories 1 to 9 (Validation, Missing, Conflict, State, Business Rule, Consistency, Communication, Availability, Internal).
   - `ErrorSeverity.kt`: Enum (`INFO`, `WARN`, `ERROR`, `CRITICAL`).
   - `RetryPolicy.kt`: Enum (`NEVER`, `RETRY_AFTER`, `REAUTHENTICATE`, `REFRESH_TOKEN`, `RESYNC`, `IDEMPOTENT_RETRY`).
   - `DisclosurePolicy.kt`: Enum (`PUBLIC`, `RESOURCE_HIDDEN_WHEN_UNAUTHORIZED`, `INTERNAL_REDACTED`).
   - `TransportChannel.kt`: Enum (`REST`, `GRAPHQL`, `WEBSOCKET`, `MESSAGING`, `SCHEDULER`, `STARTUP`).
3. **`ErrorDefinition.kt`**:
   - Immutable interface with structured KDoc:
     ```kotlin
     /**
      * Authoritative definition of a governed Squarewise error condition.
      *
      * Direct property access provides constant-time error resolution without reflection.
      */
     interface ErrorDefinition {
         val numericCode: ErrorCode
         val errorName: String
         val legacyCode: String?
         val title: String
         val safeDetail: String
         val messageKey: String
         val httpStatus: Int?
         val graphqlClassification: String?
         val retryPolicy: RetryPolicy
         val severity: ErrorSeverity
         val disclosure: DisclosurePolicy
     }
     ```
4. **Unit Tests (`ErrorCodeTest.kt`, `ErrorMetadataTest.kt`)**:
   - Test digit decomposition, display code formatting, validation of invalid length/chars, and enum mappings.

## Acceptance Criteria

1. Every class, interface, and enum resides in its own single file under `com.subhrodip.squarewise.errors.code`.
2. `ErrorCode` is an inlined value class with zero allocation overhead in compiled bytecode.
3. Decomposing `213201` correctly yields domain=2, module=1, layer=3, category=2, sequence=1, displayCode="21-3-2-01".
4. All types have comprehensive KDoc documentation explaining intent, invariants, and edge cases.
5. Unit tests achieve 100% branch and line coverage for the new types.
6. Validation commands execute cleanly.

## Validation Commands

```powershell
./gradlew.bat :libs:errors:test :libs:errors:jacocoTestReport --rerun-tasks --no-daemon
uv run python tools/contracts/validate.py
git diff --check
```

## Evidence Expectations

- JaCoCo test report showing 100% coverage on `com.subhrodip.squarewise.errors.code.*`.
- Verification that no reflection or regex is used on the property access path.

## Rollout & Rollback Strategy

- Pure library addition in `libs/errors`.
- Zero disruption to existing `ErrorCode` enum in `libs/errors` until Phase 4 migration.
- Rollback: Revert library package if compilation conflicts arise.
