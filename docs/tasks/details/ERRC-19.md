# ERRC-19: Migrate Accounts definitions and failures

## Context & Parent Milestone

- **Parent Workstream**: [`ERRC-01: Design six-digit domain error-code migration`](ERRC-01.md)
- **Phase**: Phase 4 — Bounded-context migration
- **Implementation Strategy**: [`docs/architecture/error-code-refactoring.md`](../../architecture/error-code-refactoring.md)

## Objective

Migrate all production error throw sites, catches, validation logic, and boundary handling in the Accounts service (`app/accounts`) to use strongly-typed static error definitions from `AccountsErrors` and governed `SquarewiseException` subclasses. Eliminate all 30 legacy `ApplicationException(ErrorCode...)` usages, resolve known semantic inconsistencies, and maintain full backward compatibility for API v1 responses.

## Dependencies

- Preceding: [`ERRC-16`](ERRC-16.md), [`ERRC-18`](ERRC-18.md)
- Accounts guide: [`docs/architecture/errors/accounts.md`](../../architecture/errors/accounts.md)

## Owned Paths

- `docs/tasks/details/ERRC-19.md`
- `app/accounts/src/main/kotlin/com/subhrodip/squarewise/accounts/`
- `app/accounts/src/test/kotlin/com/subhrodip/squarewise/accounts/`
- `tools/qa/error_hygiene_allowlist.yaml` (prune migrated Accounts entries)

## Architecture & Design Patterns

- **Domain-Driven Design (Bounded Context Purity)**: Accounts exceptions strictly utilize Domain 1 (`11xxxx` Profile, `12xxxx` Auth, `13xxxx` Session, `14xxxx` Lifecycle, `15xxxx` Identity) definitions from `AccountsErrors`.
- **Factory Pattern for Domain Exceptions**: Encapsulate exception instantiation within domain service factories (e.g. `AccountExceptions.profileNotFound(id)`), ensuring consistent diagnostic attachment.
- **Anti-Enumeration & Least Disclosure**: Reconcile profile lookup: missing profile returns uniform 404 anti-enumeration response whether the profile does not exist or caller lacks authorization.
- **Strict SOLID File Separation**: Every new exception, handler, or DTO is declared in its own dedicated file.

## Common Libraries & Framework Integration

- **`libs/errors`**: `AccountsErrors`, `SquarewiseException`, `GlobalErrorAdvice`.
- **`libs/security`**: OIDC token validation and security error entry points.
- **`libs/ids`**: `ApiEndpoints` constant routing.

## Technical Requirements & Deliverables

1. **Replace 30 Production `ApplicationException` Usages**:
   - Profile module: `PROFILE_NOT_FOUND` (`113201`), `PROFILE_ALREADY_EXISTS` (`113301`), `INVALID_PROFILE_DATA` (`113101`).
   - Authentication module: `MAGIC_LINK_EXPIRED` (`127401`), `INVALID_AUTH_CODE` (`127101`), `AUTH_RATE_LIMITED` (`127801`).
   - Session module: `REFRESH_TOKEN_EXPIRED` (`137401`), `REFRESH_TOKEN_FAMILY_REVOKED` (`137402`).
   - Lifecycle module: `DELETION_ALREADY_PENDING` (`144301`), `EXPORT_REQUEST_LIMIT` (`144801`).
2. **Reconcile Semantic Discrepancies**:
   - Fix profile absence inconsistently returning 401 vs 404: standardize on 404 anti-enumeration.
   - Separate rate-limit quota exhaustion (429) from Redis cache unavailable (503).
3. **Prune Error Hygiene Allowlist**:
   - Remove all Accounts entries from `tools/qa/error_hygiene_allowlist.yaml`.
4. **Service Test Suite Updates**:
   - Update `app/accounts/src/test/` to assert both legacy `code` and additive `numericCode` / `errorName` fields in MockMvc tests.

## Acceptance Criteria

1. Zero references to legacy `ErrorCode` or generic `ApplicationException` remain in `app/accounts/src/main/`.
2. All 30 Accounts error throw sites use `AccountsErrors` constants.
3. Accounts test suite passes cleanly with 100% test success and zero regression.
4. `make error-hygiene` passes with zero Accounts entries on the allowlist.
5. All verification commands execute cleanly.

## Validation Commands

```powershell
./gradlew.bat :app:accounts:test :app:accounts:jacocoTestReport --rerun-tasks --no-daemon
uv run python tools/qa/check_error_hygiene.py
uv run python tools/contracts/validate.py
git diff --check
```

## Evidence Expectations

- JaCoCo test report showing complete coverage across migrated Accounts controllers and services.
- Clean output from `check_error_hygiene.py` confirming zero residual Accounts debt.

## Rollout & Rollback Strategy

- Deployed to Accounts service.
- Additive fields ensure complete backward compatibility with existing BFF and web clients.
- Rollback: Standard Git revert of service branch if regressions occur.

## Implementation Notes and Evidence

- Replaced Accounts production `ApplicationException`/legacy error-code throw
  sites with catalog-governed `AccountsDomainException` definitions for profile,
  login, rate-limit, session, and identity failures.
- Added `AccountsInputException` for low-level configuration, crypto, and
  identity input contracts that intentionally remain `IllegalArgumentException`
  compatible while eliminating anonymous generic throw sites.
- Added governed exception compatibility handling in the shared legacy
  `ApiProblem` mapper, preserving v1 response shape and rate-limit retry headers.
- Profile absence now uses the catalog 404 anti-enumeration definition; callers
  without authentication remain 401, while foreign and batch access remain 403.
- Removed all Accounts-owned entries from `tools/qa/error_hygiene_allowlist.yaml`.
- The complete Accounts suite passed 267 tests on 2026-10-07; the Accounts
  production tree has no legacy `ApplicationException` or legacy error-code
  references.
