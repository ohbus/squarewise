# ERRC-02: Freeze audit and baseline behavior

## Context & Parent Milestone

- **Parent Workstream**: [`ERRC-01: Design six-digit domain error-code migration`](ERRC-01.md)
- **Phase**: Phase 0 — Governance and frozen evidence
- **Implementation Strategy**: [`docs/architecture/error-code-refactoring.md`](../../architecture/error-code-refactoring.md)

## Objective

Establish a frozen, reproducible baseline of all existing production error throw sites, catches, boundary handlers, HTTP status mappings, problem response bodies, and headers across all Squarewise deployables and shared libraries. Capture the exact characterization snapshot before any schema, catalog, or code modifications occur, ensuring zero unintended behavioral regressions.

## Dependencies

- Preceding: [`ERRC-01: Design six-digit domain error-code migration`](ERRC-01.md)
- Required inputs: Audit findings from [`ERRC-01A`](ERRC-01A.md), [`ERRC-01B`](ERRC-01B.md), and [`ERRC-01C`](ERRC-01C.md).

## Owned Paths

- `docs/tasks/details/ERRC-02.md`
- `docs/architecture/errors/baseline-audit-inventory.md`
- `tests/fixtures/errors/baseline/` (characterization snapshots)

## Architecture & Design Patterns

- **Characterization Testing Pattern**: Golden-master snapshots capturing current runtime outputs (status codes, headers, Problem Details payloads) across Accounts, Expense Core, Notifications, and BFF.
- **Single Source of Truth (SSOT)**: A machine-verifiable inventory of all 128 production `ApplicationException` usages, 181 `throw` expressions, 184 `require` calls, and 53 `catch` blocks.
- **KISS & DRY**: Pure data snapshots and simple markdown tables; no speculative abstractions or runtime overhead.
- **Separation of Concerns**: Strict boundary between baseline observation and future implementation; zero production code is modified in this task.

## Common Libraries & Framework Integration

- **`libs/errors`**: Catalog existing usages of `ErrorCode` (ERR-01 through ERR-12), `ApplicationException`, and `GlobalErrorHandler`.
- **`libs/ids`**: Verify `ApiEndpoints` usage across all controller error mappings.
- **`libs/observability`**: Audit MDC correlation handling, request ID propagation, and error logging boundaries.

## Technical Requirements & Deliverables

1. **Complete Source Inventory**:
   - Tabulate every occurrence of `ApplicationException`, `throw`, `require`, and `check` across:
     - `app/accounts`
     - `app/expense-core`
     - `app/notifications`
     - `app/bff`
     - Shared libraries (`libs/db`, `libs/security`, `libs/errors`, `libs/observability`, `libs/ids`).
2. **Boundary & Filter Audit**:
   - Document all Spring `@ExceptionHandler` methods in `GlobalErrorHandler` and controller advice.
   - Audit Spring Security filter chain error handling (AuthenticationEntryPoint, AccessDeniedHandler) in both Servlet and Reactive stacks.
   - Audit RabbitMQ message listener error handlers and dead-letter queue routing.
3. **Response Characterization Fixtures**:
   - Record exact JSON problem details returned for all 12 existing `ERR-XX` codes under current test scenarios.
   - Record response headers (`WWW-Authenticate`, `Retry-After`, `Allow`).
4. **Semantic Defect Register**:
   - Compile a validated list of known semantic inconsistencies (e.g. expense deletion reporting auth failure, profile absence alternating 401/404) to be addressed in subsequent refactoring phases.

## Acceptance Criteria

1. `docs/architecture/errors/baseline-audit-inventory.md` contains an exhaustive list of all 128 production `ApplicationException` call sites with file paths, line numbers, current codes, and planned migration targets.
2. Exact golden-master fixtures for all current error response shapes are checked into `tests/fixtures/errors/baseline/`.
3. All security filter and messaging unhandled exception pathways are mapped and classified.
4. No production Kotlin source code, public schemas, or build configurations are modified.
5. All verification commands execute cleanly with zero errors.

## Validation Commands

```powershell
uv run python tools/contracts/validate.py
uv run python tools/errors/validate_catalog.py
git diff --check
```

## Completion Evidence

- `docs/architecture/errors/baseline-audit-inventory.md` created with exhaustive cataloging of 131 production `ApplicationException` call sites (Accounts: 32, Expense Core: 87, Notifications: 7, BFF: 4, libs/errors: 1), 184 `throw` statements, 198 `require` statements, 57 `catch` blocks, and 10 `@ExceptionHandler` methods.
- 11 golden-master response fixtures checked into `tests/fixtures/errors/baseline/` (`err_01` through `err_11`, plus `response_headers_baseline.json`).
- Security filter entry points and messaging consumer error handling (including 4 broad `catch (Throwable)` sites) mapped and classified.
- Four known semantic status defects cataloged in the inconsistency register for intentional resolution in subsequent phases.
- Zero production Kotlin code, public schemas, or build configurations modified.
- `uv run python tools/contracts/validate.py`, `uv run python tools/errors/validate_catalog.py`, and `git diff --check` passed cleanly.

