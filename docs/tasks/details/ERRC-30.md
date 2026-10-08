# ERRC-30: Retire legacy infrastructure, not the v1 field

## Context & Parent Milestone

- **Parent Workstream**: [`ERRC-01: Design six-digit domain error-code migration`](ERRC-01.md)
- **Phase**: Phase 5 — Clients, acceptance, scale, rollout
- **Implementation Strategy**: [`docs/architecture/error-code-refactoring.md`](../../architecture/error-code-refactoring.md)

## Objective

Decommission and delete internal legacy error enum structures (`com.subhrodip.squarewise.errors.domain.ErrorCode` with `ERR-01` through `ERR-12`), deprecated exception constructors, and transitional mapping shims across `libs/errors` and applications. Retain the public API v1 symbolic `code` string in responses by deriving it directly from `ErrorDefinition.legacyCode`. Preserve immutable historical catalog records.

## Dependencies

- Preceding: [`ERRC-29: Make additive fields required after compatibility window`](ERRC-29.md)

## Owned Paths

- `docs/tasks/details/ERRC-30.md`
- `libs/errors/src/main/kotlin/com/subhrodip/squarewise/errors/`
- `libs/security/src/main/kotlin/com/subhrodip/squarewise/security/`
- `app/accounts/src/`
- `app/expense-core/src/`
- `app/bff/src/`
- `contracts/errors/error-catalog.yaml`
- `contracts/errors/error-catalog.schema.json`
- `tools/codegen/generate_error_catalogs.py`
- `tools/errors/validate_catalog.py`
- `docs/architecture/error-code-standard.md`

## Architecture & Design Patterns

- **Strangler Fig Pattern Completion**: The legacy error enum and ad-hoc exception infrastructure are safely removed once 100% of callers route through the six-digit catalog and governed exception hierarchy.
- **DRY & Single Source of Truth**: Eliminate dual enum/constant definitions; `ErrorDefinition.legacyCode` becomes the sole source of truth for the v1 `code` property.
- **Immutable History Preservation**: While runtime code is deleted, historical registry data in `contracts/errors/` is preserved to document the history of deprecated codes.
- **Clean Dependency Architecture**: Verifies that `libs/errors` has zero dead code, orphaned enums, or deprecated classes.

## Common Libraries & Framework Integration

- **`libs/errors`**: Cleaned of legacy enums and deprecated classes.
- **Compiler Checks**: Prohibits `@Deprecated` legacy usages from compiling.

## Technical Requirements & Deliverables

1. **Delete Legacy Error Infrastructure & Attributes**:
   - Delete `legacyCode` attribute from `ErrorDefinition`, `SimpleErrorDefinition`, schemas, and catalogs.
   - Decommission legacy transitional mapping shims and `legacyErrorDefinition` schema defs.
2. **Promote `code` to ErrorCategory & Add `messageKey`**:
   - `ProblemDetailsDto.code` represents the canonical `ErrorCategory` enum string (`VALIDATION_ERROR`, `BUSINESS_RULE_VIOLATION`, `NOT_FOUND`, etc.).
   - `ProblemDetailsDto.messageKey` is emitted for frontend internationalization (i18n).
   - Rich `violations` structure emits field, message, `messageKey`, and `rejectedValue`.
3. **HTTP 400-Series Alignment**:
   - HTTP 400 reserved strictly for unparseable syntax (`REQUEST_BODY_MALFORMED`, `GRAPHQL_OPERATION_INVALID`).
   - HTTP 422 used for semantic validations (`REQUEST_VALIDATION_FAILED`, `ALLOCATION_SUM_MISMATCH`, etc.).
   - HTTP 410 used for expired resources (`SYNC_CURSOR_EXPIRED`, `INVITATION_EXPIRED`).
4. **Zero-Reflection & High-Performance Propagation**:
   - Hot path uses precompiled static singleton definitions and $O(1)$ set identity verification.
   - Bounded custom exceptions provide sensible defaults while supporting definition injection.

## Acceptance Criteria

1. Zero references to legacy `ERR_XX` enums remain in `app/` and `libs/`.
2. Public API v1 Problem Details continue emitting the correct symbolic `code` (`NOT_FOUND`, `UNAUTHORIZED`, etc.) derived from catalog metadata.
3. All service and library test suites compile and pass without deprecation warnings.
4. Repository dependency and package graph is clean of orphaned legacy classes.
5. All verification commands execute cleanly.

## Validation Commands

```powershell
./gradlew.bat test jacocoTestReport --rerun-tasks --no-daemon
uv run python tools/contracts/validate.py
git diff --check
```

## Evidence Expectations

- Global search output proving 0 occurrences of legacy `ERR_` enums in production source code.
- Full test suite execution report confirming 100% pass across all services.

## 2026-10-07 local implementation inventory

The local implementation increment is complete and is deliberately separated from
the still-unverified production-effective ERRC-29 gate. The runtime now:

- deletes `domain/ErrorCode.kt` and `domain/ApplicationException.kt`;
- derives v1 `code` directly from `ErrorDefinition.legacyCode ?: errorName` in REST,
  GraphQL, and security boundaries;
- removes generated `ExpenseErrors.ERR_*` aliases and migrates application callers
  to catalog definitions and bounded-context exceptions;
- stores current symbolic v1 aliases in the catalog while preserving retired `ERR-*`
  records in lifecycle history; and
- validates non-null public aliases against the symbolic-code pattern while retaining
  the separate legacy-record validator for historical fixtures. The six-digit
  validator is the active current-catalog gate; the compatibility validator is
  retained only for historical records and shared schema assertions.
- classifies `java.lang.ThreadDeath` by stable runtime type name so the fatal-error
  boundary does not compile against a deprecated Java constructor or type reference.

Local evidence is `./gradlew.bat test --no-daemon --console=plain` (successful),
zero `ERR_*` references in production `app/` and `libs/` sources, and passing
catalog/codegen/contract validation. External effective-date approval,
client adoption, staging rollout, and ERRC-26 performance evidence remain outside
local proof and continue to block production closure of ERRC-29/ERRC-28.

## Rollout & Rollback Strategy

- Code cleanup and technical debt elimination.
- No public API changes; API v1 contract responses remain identical.
- Rollback: Standard Git revert if unexpected downstream references are found.
