# ERRC-30: Retire legacy infrastructure, not the v1 field

## Context & Parent Milestone

- **Parent Workstream**: [`ERRC-01: Design six-digit domain error-code migration`](ERRC-01.md)
- **Phase**: Phase 5 — Clients, acceptance, scale, rollout
- **Implementation Strategy**: [`docs/architecture/error-code-refactoring.md`](../../architecture/error-code-refactoring.md)

## Objective

Decommission and delete internal legacy error enum structures (`com.subhrodip.squarewise.errors.ErrorCode` with `ERR-01` through `ERR-12`), deprecated exception constructors, and transitional mapping shims across `libs/errors` and applications. Retain the public API v1 symbolic `code` string in responses by deriving it directly from `ErrorDefinition.legacyCode`. Preserve immutable historical catalog records.

## Dependencies

- Preceding: [`ERRC-29: Make additive fields required after compatibility window`](ERRC-29.md)

## Owned Paths

- `docs/tasks/details/ERRC-30.md`
- `libs/errors/src/main/kotlin/com/subhrodip/squarewise/errors/`
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

1. **Delete Legacy Error Enums & Deprecated Classes**:
   - Safely remove `enum class ErrorCode { ERR_01, ERR_02, ... }`.
   - Safely remove legacy constructors: `ApplicationException(ErrorCode, String)`.
   - Remove legacy translation mappers.
2. **Derive v1 Public `code` from Catalog**:
   - Ensure `ProblemDetailsDto.code` is populated directly via `definition.legacyCode ?: definition.errorName`.
3. **Retire Old Validator Script**:
   - Upgrade or decommission `tools/errors/validate_catalog.py` in favor of `tools/errors/validate_six_digit_catalog.py`.
4. **Repository-Wide Clean Sweep**:
   - Verify zero occurrences of `ERR_01` through `ERR_12` in production code.

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

## Rollout & Rollback Strategy

- Code cleanup and technical debt elimination.
- No public API changes; API v1 contract responses remain identical.
- Rollback: Standard Git revert if unexpected downstream references are found.
