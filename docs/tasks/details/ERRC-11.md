# ERRC-11: Generate or compile static catalogs

## Context & Parent Milestone

- **Parent Workstream**: [`ERRC-01: Design six-digit domain error-code migration`](ERRC-01.md)
- **Phase**: Phase 2 — Static error core, no runtime discovery
- **Implementation Strategy**: [`docs/architecture/error-code-refactoring.md`](../../architecture/error-code-refactoring.md)

## Objective

Create compile-time static error catalogs for each domain (Accounts, Expense Core, Notifications, BFF, Platform) derived directly from `contracts/errors/error-catalog.yaml`. Implement a deterministic code generation tool (or strictly typed immutable compiled objects) producing separate catalog files per domain, eliminating runtime YAML parsing, classpath scanning, annotations, or `ServiceLoader` lookups.

## Dependencies

- Preceding: [`ERRC-10: Implement core value and metadata types`](ERRC-10.md)
- Source catalog: [`contracts/errors/error-catalog.yaml`](../../../contracts/errors/error-catalog.yaml)

## Owned Paths

- `docs/tasks/details/ERRC-11.md`
- `tools/codegen/generate_error_catalogs.py`
- `libs/errors/src/main/kotlin/com/subhrodip/squarewise/errors/catalog/`
- `libs/errors/src/test/kotlin/com/subhrodip/squarewise/errors/catalog/`

## Architecture & Design Patterns

- **Static Catalog / Factory Pattern**: Domain catalogs provide static, preconstructed instances of `ErrorDefinition` (e.g. `ExpenseErrors.GROUP_NOT_FOUND`), making runtime lookup constant-time (`O(1)`).
- **Single Responsibility & Domain Separation**: Catalogs are divided strictly by domain into separate files:
  - `AccountsErrors.kt`
  - `ExpenseErrors.kt`
  - `NotificationErrors.kt`
  - `BffErrors.kt`
  - `PlatformErrors.kt`
- **Code Generation / Deterministic Compilation**: Generated files are reproducible bit-for-bit from the authoritative YAML source, checked in or generated via Gradle build task.
- **Fail-Fast Parity Assertion**: Automated tests assert 100% parity between `error-catalog.yaml` and compiled static catalog definitions.

## Common Libraries & Framework Integration

- **`libs/errors`**: Houses the static catalog objects and domain-specific error constant interfaces.
- **Gradle Kotlin DSL**: Integrates the catalog code generator into the build lifecycle.

## Technical Requirements & Deliverables

1. **Catalog Code Generator (`tools/codegen/generate_error_catalogs.py`)**:
   - Parses `contracts/errors/error-catalog.yaml`.
   - Generates idiomatic, formatted Kotlin objects with full KDoc documentation:
     ```kotlin
     package com.subhrodip.squarewise.errors.catalog

     import com.subhrodip.squarewise.errors.code.*

     /**
      * Governed error definitions for the Expense Core domain.
      */
     object ExpenseErrors {
         /**
          * 213201: Group not found.
          */
         val GROUP_NOT_FOUND: ErrorDefinition = SimpleErrorDefinition(
             numericCode = ErrorCode("213201"),
             errorName = "GROUP_NOT_FOUND",
             legacyCode = "NOT_FOUND",
             title = "Group not found",
             safeDetail = "Group not found",
             messageKey = "errors.group.notFound",
             httpStatus = 404,
             graphqlClassification = "NOT_FOUND",
             retryPolicy = RetryPolicy.NEVER,
             severity = ErrorSeverity.INFO,
             disclosure = DisclosurePolicy.RESOURCE_HIDDEN_WHEN_UNAUTHORIZED
         )
         // ...
     }
     ```
2. **Domain Catalog Source Files (Dedicated Files)**:
   - `AccountsErrors.kt`
   - `ExpenseErrors.kt`
   - `NotificationErrors.kt`
   - `BffErrors.kt`
   - `PlatformErrors.kt`
   - `SimpleErrorDefinition.kt` (Immutable data class implementing `ErrorDefinition`)
3. **Automated Catalog Parity Test (`CatalogParityTest.kt`)**:
   - Compares every record in `contracts/errors/error-catalog.yaml` against static constants via static table lookup, asserting identical numeric codes, error names, HTTP statuses, and titles.

## Acceptance Criteria

1. All domain catalog classes reside in their own dedicated files with zero reflection or runtime classpath scanning.
2. Static catalog objects load in < 1ms on JVM startup without parsing YAML, reading files, or invoking reflection.
3. `CatalogParityTest.kt` passes with 100% agreement between YAML catalog and compiled Kotlin constants.
4. Generated code adheres strictly to repository Kotlin formatting and Spotless style rules.
5. All verification commands execute cleanly.

## Validation Commands

```powershell
./gradlew.bat :libs:errors:test :libs:errors:jacocoTestReport --rerun-tasks --no-daemon
uv run python tools/contracts/validate.py
git diff --check
```

## Evidence Expectations

- Successful execution log of `generate_error_catalogs.py`.
- Passing `CatalogParityTest` verifying 100% match with authoritative YAML catalog.

## Rollout & Rollback Strategy

- Additive static code generation in `libs/errors`.
- Zero impact on runtime traffic until exception and handler migrations in subsequent phases.
- Rollback: Revert generated files and script if catalog schema updates are required.

## Implementation Notes and Evidence

- Added `tools/codegen/generate_error_catalogs.py` as the deterministic generator
  from `contracts/errors/error-catalog.yaml`. It groups records by owner and
  emits one Kotlin object per domain plus a compiled aggregate view.
- Generated `AccountsErrors`, `ExpenseErrors`, `NotificationErrors`,
  `BffErrors`, and `PlatformErrors` with immutable `SimpleErrorDefinition`
  instances. Production catalog access performs no YAML parsing, reflection,
  classpath scanning, annotations, or `ServiceLoader` lookup.
- The generator emits `CatalogParityTest`, which checks all 99 authoritative
  records for numeric code, error name, title, and HTTP status against the
  compiled catalog. Missing HTTP status remains `null` for internal-only
  records; missing disclosure metadata uses the deterministic pre-migration
  default of public, except GraphQL `NOT_FOUND` records, which hide resources.
- Validation passed on 2026-10-07:
  `uv run python tools/codegen/generate_error_catalogs.py`,
  `uv run --frozen --no-build mypy tools/codegen/generate_error_catalogs.py`,
  `./gradlew.bat :libs:errors:test :libs:errors:jacocoTestReport --rerun-tasks --no-daemon --console=plain`,
  `uv run python tools/contracts/validate.py`, and `git diff --check`.
