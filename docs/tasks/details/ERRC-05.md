# ERRC-05: Define the additive Problem Details contract

## Context & Parent Milestone

- **Parent Workstream**: [`ERRC-01: Design six-digit domain error-code migration`](ERRC-01.md)
- **Phase**: Phase 1 — Contract-first compatibility
- **Implementation Strategy**: [`docs/architecture/error-code-refactoring.md`](../../architecture/error-code-refactoring.md)

## Objective

Update the public RFC 9457 Problem Details contract in `contracts/errors/problem.schema.json` to support additive six-digit error fields without breaking existing consumers. Retain the legacy symbolic `code` while adding optional `numericCode` (`DMLCEE`) and `errorName` (`SCREAMING_SNAKE_CASE`). Define safe bounds, field constraints, violation arrays, and content negotiation rules.

## Dependencies

- Preceding: [`ERRC-04: Allocate and review the complete error catalog`](ERRC-04.md)

## Owned Paths

- `docs/tasks/details/ERRC-05.md`
- `contracts/errors/problem.schema.json`
- `contracts/errors/examples/` (canonical problem JSON examples)
- `docs/architecture/error-code-standard.md`

## Architecture & Design Patterns

- **Tolerant Reader & Expand/Contract Pattern**: Allow existing API v1 consumers to continue reading `code` undisturbed while new consumers can opt-in to `numericCode` and `errorName`.
- **RFC 9457 Problem Details Adapter**: Conforms to standard HTTP Problem Details (`application/problem+json`) with Squarewise domain extensions.
- **Fail-Closed & Bounded Fields Pattern**: Hard constraints on string lengths (title <= 128 chars, detail <= 512 chars, requestId UUID format) and array bounds (max 50 violation items) to prevent denial-of-service via oversized responses.
- **Security Redaction Pattern**: Invariants ensuring no stack traces, raw error messages, or internal tokens can ever be represented within the Problem schema.

## Common Libraries & Framework Integration

- **`libs/errors`**: Shared schema model used by `ProblemDetails` DTOs in Spring Web and WebFlux.
- **`libs/ids`**: Identifier formats for `requestId` and domain IDs.

## Technical Requirements & Deliverables

1. **Update Problem Details Schema (`contracts/errors/problem.schema.json`)**:
   - Add optional property `numericCode`:
     ```json
     "numericCode": {
       "type": "string",
       "pattern": "^[1-9]{4}(0[1-9]|[1-9][0-9])$",
       "description": "Canonical six-digit machine error code (DMLCEE)."
     }
     ```
   - Add optional property `errorName`:
     ```json
     "errorName": {
       "type": "string",
       "pattern": "^[A-Z0-9_]{3,64}$",
       "description": "Immutable SCREAMING_SNAKE_CASE symbolic name."
     }
     ```
   - Retain required property `code` for v1 backward compatibility.
   - Retain `type`, `title`, `status`, `detail`, `instance`, `timestamp`, `requestId`, `source`.
   - Update `violations` array schema with bounded field lengths and structured path references.
2. **Canonical Contract Examples (`contracts/errors/examples/`)**:
   - `legacy-problem.json`: Validates existing v1-only response shape.
   - `additive-problem.json`: Validates response shape containing both `code`, `numericCode`, and `errorName`.
   - `validation-problem.json`: Validates 400 Bad Request with bounded violation items.
   - `security-problem.json`: Validates 401/403/404 anti-enumeration responses.

## Acceptance Criteria

1. `contracts/errors/problem.schema.json` validates both legacy (pre-migration) and additive responses cleanly.
2. Property `numericCode` adheres to regex `^[1-9]{4}(0[1-9]|[1-9][0-9])$`.
3. Property `errorName` conforms to `^[A-Z0-9_]{3,64}$`.
4. Schema prohibits `additionalProperties` beyond declared fields to prevent information leakage.
5. All JSON examples in `contracts/errors/examples/` validate against the updated schema.
6. Verification commands run cleanly.

## Validation Commands

```powershell
uv run python tools/contracts/validate.py
git diff --check
```

## Evidence Expectations

- JSON schema validation output confirming backward and forward compatibility.
- Test report proving both legacy and additive JSON payloads pass schema validation.

## Implementation Notes and Verification

- Updated `contracts/errors/problem.schema.json` with optional `numericCode` and
  `errorName` fields, bounded RFC 9457 fields, bounded structured violations,
  UUID request IDs, and closed top-level and violation objects.
- Added `legacy-problem.json`, `additive-problem.json`, `validation-problem.json`,
  and `security-problem.json` under `contracts/errors/examples/`.
- `uv run --with jsonschema` validated all four examples using Draft 2020-12 and
  format checks in an isolated environment.
- `uv run python tools/contracts/validate.py` passed (252 tasks, 15 JSON files),
  `uv run python tools/errors/validate_catalog.py` passed (99 unique codes),
  catalog unit tests passed (7 tests), and `git diff --check` passed.

The default `uv` environment does not install `jsonschema`; schema validation was
therefore run with the isolated `uv --with jsonschema` dependency. Repository
dependencies and runtime code were not changed.

## Rollout & Rollback Strategy

- Contract change preceding application code updates.
- Fully backward-compatible; additive optional fields.
- Rollback: Revert schema changes to previous revision if client compatibility issues arise.
