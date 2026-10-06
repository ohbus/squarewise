# ERRC-06: Reconcile all REST OpenAPI contracts

## Context & Parent Milestone

- **Parent Workstream**: [`ERRC-01: Design six-digit domain error-code migration`](ERRC-01.md)
- **Phase**: Phase 1 — Contract-first compatibility
- **Implementation Strategy**: [`docs/architecture/error-code-refactoring.md`](../../architecture/error-code-refactoring.md)

## Objective

Synchronize and reconcile all OpenAPI 3.1 specifications across Accounts, Expense Core, and Notifications to reference the unified, additive Problem Details schema. Ensure every endpoint operation has complete, explicit non-2xx error response definitions, accurate HTTP status codes, and declared response headers (`WWW-Authenticate`, `Retry-After`, `Allow`).

## Dependencies

- Preceding: [`ERRC-05: Define the additive Problem Details contract`](ERRC-05.md)

## Owned Paths

- `docs/tasks/details/ERRC-06.md`
- `contracts/rest/accounts.openapi.json`
- `contracts/rest/expense-core.openapi.json`
- `contracts/rest/notifications.openapi.json`
- `docs/api/implementation-status.md`

## Architecture & Design Patterns

- **Contract-First API Design**: OpenAPI specifications serve as the source of truth for public REST APIs before controller implementations are altered.
- **DRY (Don't Repeat Yourself) via Component Schemas**: Replace ad-hoc, divergent Problem definitions across the three OpenAPI files with consistent, uniform `components/schemas/ProblemDetails` definitions mirroring `contracts/errors/problem.schema.json`.
- **Explicit Failure Matrix Pattern**: Every operation declares exact 4xx/5xx responses rather than relying on generic wildcard catchalls.
- **Header Contract Enforcement**: Declares explicit headers required by RFC standards (e.g. `WWW-Authenticate` on 401, `Allow` on 405, `Retry-After` on 429).

## Common Libraries & Framework Integration

- **`libs/errors`**: Server-side controllers and advice implement the exact responses declared in these OpenAPI contracts.
- **`libs/ids`**: Paths in OpenAPI documents strictly match constants in `com.subhrodip.squarewise.ids.ApiEndpoints`.

## Technical Requirements & Deliverables

1. **Schema Uniformity Across Services**:
   - Reconcile `components/schemas/ProblemDetails` in:
     - `contracts/rest/accounts.openapi.json`
     - `contracts/rest/expense-core.openapi.json`
     - `contracts/rest/notifications.openapi.json`
   - Include optional `numericCode` and `errorName` properties.
2. **Exhaustive Non-2xx Response Declarations**:
   - Audit all 45 REST operations across the three services.
   - For every endpoint, add explicit responses for predictable errors:
     - 400 Bad Request (Validation / Malformed syntax)
     - 401 Unauthorized (Missing / invalid bearer token)
     - 403 Forbidden (Ownership / authorization policy failure)
     - 404 Not Found (Missing entity / anti-enumeration hiding)
     - 409 Conflict (Optimistic lock / concurrency / state conflict)
     - 429 Too Many Requests (Rate limit exhaustion)
     - 500 Internal Server Error (Unexpected internal error)
3. **Response Headers Specification**:
   - Define `WWW-Authenticate` response header for all 401 responses.
   - Define `Retry-After` response header for all 429 responses.
   - Define `Allow` response header for all 405 responses.
4. **Documentation Sync**:
   - Update `docs/api/implementation-status.md` with the comprehensive endpoint-to-error coverage matrix.

## Acceptance Criteria

1. All three OpenAPI 3.1 JSON contracts parse cleanly without validation warnings.
2. `components/schemas/ProblemDetails` is identical across all three services and matches `contracts/errors/problem.schema.json`.
3. Every operation in Accounts, Expense Core, and Notifications has explicit definitions for its predictable error statuses.
4. No OpenAPI path drifts from `ApiEndpoints`.
5. Automated contract validation scripts pass without errors.

## Validation Commands

```powershell
uv run python tools/contracts/validate.py
git diff --check
```

## Evidence Expectations

- Clean contract validation report for all 45 REST operations.
- OpenAPI diff demonstrating unified `ProblemDetails` components and expanded non-2xx status definitions.

## Rollout & Rollback Strategy

- Specification-only update; non-breaking to existing clients.
- Rollback: Git checkout of previous OpenAPI definitions if toolchain or generator issues arise.
