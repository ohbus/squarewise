# ERRC-29: Make additive fields required after compatibility window

## Context & Parent Milestone

- **Parent Workstream**: [`ERRC-01: Design six-digit domain error-code migration`](ERRC-01.md)
- **Phase**: Phase 5 — Clients, acceptance, scale, rollout
- **Implementation Strategy**: [`docs/architecture/error-code-refactoring.md`](../../architecture/error-code-refactoring.md)

## Objective

Following the conclusion of the announced public compatibility window and formal client adoption certification, promote `numericCode` and `errorName` from optional to required properties in the public RFC 9457 Problem Details schema and OpenAPI specifications. Retain the legacy symbolic `code` property for API v1 consumers while closing the compatibility window for partial or legacy-only emissions.

## Dependencies

- Preceding: [`ERRC-28: Dual-read/dual-write staged rollout`](ERRC-28.md)
- Formal product and client ecosystem sign-off.

## Owned Paths

- `docs/tasks/details/ERRC-29.md`
- `contracts/errors/problem.schema.json`
- `contracts/rest/accounts.openapi.json`
- `contracts/rest/expense-core.openapi.json`
- `contracts/rest/notifications.openapi.json`
- `docs/api/migration-notices/v1-error-compatibility-window.md`

## Architecture & Design Patterns

- **Contract Transition (Contract Phase of Expand/Contract)**: Completes the transition phase by enforcing that all server responses must contain `numericCode` and `errorName`.
- **Fail-Fast Schema Validation**: Updates test and CI gates to reject any mock or test response that omits the six-digit fields.
- **Controlled Backward Compatibility**: Retains the legacy `code` property as required in API v1, avoiding any breaking change for v1 clients that depend on symbolic codes.

## Common Libraries & Framework Integration

- **`libs/errors`**: Enforces non-null invariants for `numericCode` and `errorName` on `ProblemDetailsDto`.
- **OpenAPI & JSON Schema**: Formal schema contracts updated.

## Technical Requirements & Deliverables

1. **Schema Update (`contracts/errors/problem.schema.json`)**:
   - Update `required` array to mandate `numericCode` and `errorName`:
     ```json
     "required": [
       "type",
       "title",
       "status",
       "detail",
       "instance",
       "code",
       "numericCode",
       "errorName",
       "timestamp",
       "requestId",
       "source"
     ]
     ```
2. **OpenAPI Specs Update**:
   - Update `components/schemas/ProblemDetails` in Accounts, Expense Core, and Notifications OpenAPI specs with the updated `required` array.
3. **Migration Notice Publication (`docs/api/migration-notices/v1-error-compatibility-window.md`)**:
   - Document the formal closure of the migration window, client adoption metrics, and guidelines for third-party integrators.
4. **CI & Tooling Gates**:
   - Update `validate_openapi_parity.py` and `validate.py` to enforce that all Problem responses require the new fields.

## Acceptance Criteria

1. `contracts/errors/problem.schema.json` declares `numericCode` and `errorName` as required.
2. All OpenAPI contracts reflect the updated required fields.
3. `ProblemDetailsDto` in `libs/errors` enforces non-null `numericCode` and `errorName`.
4. All test suites pass cleanly with zero omission warnings.
5. All verification commands execute cleanly.

## Validation Commands

```powershell
uv run python tools/contracts/validate.py
make contracts
git diff --check
```

## Evidence Expectations

- Formal client adoption sign-off document.
- Clean contract validation output proving `numericCode` and `errorName` are universally required.

## Rollout & Rollback Strategy

- Contract governance promotion milestone.
- Non-breaking to v1 clients since legacy `code` remains present.
- Rollback: Revert required constraint to optional in schema if edge-case clients report issues.
