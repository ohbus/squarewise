# ERRC-24: Update client fixtures, Bruno, and end-to-end acceptance

## Context & Parent Milestone

- **Parent Workstream**: [`ERRC-01: Design six-digit domain error-code migration`](ERRC-01.md)
- **Phase**: Phase 5 — Clients, acceptance, scale, rollout
- **Implementation Strategy**: [`docs/architecture/error-code-refactoring.md`](../../architecture/error-code-refactoring.md)

## Objective

Update the Bruno API collection, end-to-end multi-service acceptance test suites, and client test fixtures to validate the additive six-digit error responses across Accounts, Expense Core, Notifications, and BFF. Validate that both legacy clients (expecting only `code`) and modern clients (consuming `numericCode` and `errorName`) interoperate smoothly without errors.

## Dependencies

- Preceding: [`ERRC-19`](ERRC-19.md), [`ERRC-20`](ERRC-20.md), [`ERRC-21`](ERRC-21.md), [`ERRC-22`](ERRC-22.md), [`ERRC-23`](ERRC-23.md)

## Owned Paths

- `docs/tasks/details/ERRC-24.md`
- `tools/bruno/`
- `tests/e2e/`
- `docs/quality/acceptance.md`

## Architecture & Design Patterns

- **Consumer-Driven Contract Testing Pattern**: Validates that responses satisfy the expectations of multiple distinct consumer profiles (legacy client test harness vs additive six-digit client test harness).
- **End-to-End Black-Box Verification**: Tests exercise the full running container stack over real HTTP, GraphQL, and WebSocket protocols.
- **Fail-Fast Assertion Strategy**: Bruno assertions verify HTTP status, `Content-Type: application/problem+json`, `code`, `numericCode`, `errorName`, `requestId`, and `timestamp` on every error scenario.

## Common Libraries & Framework Integration

- **Bruno CLI (`bru`)**: Automated API regression testing against local stack.
- **Docker Compose**: Live multi-service topology testing (`make acceptance-live`).

## Technical Requirements & Deliverables

1. **Bruno Collection Updates (`tools/bruno/`)**:
   - Update existing 50 Bruno requests and add assertions for:
     - `res.body.code`: Verifies legacy code remains intact.
     - `res.body.numericCode`: Asserts valid 6-digit regex format.
     - `res.body.errorName`: Asserts expected SCREAMING_SNAKE_CASE identifier.
     - `res.body.requestId`: Asserts UUID format and matches correlation headers.
   - Add dedicated negative-path requests testing 400, 401, 403, 404, 409, and 429 across all services.
2. **End-to-End Acceptance Suites (`tests/e2e/`)**:
   - Python-based live acceptance suite validating full user journeys under error conditions:
     - Creating an expense with invalid member allocation.
     - Accessing an archived group.
     - Triggering rate-limit thresholds.
     - Invalid login magic link verification.
3. **Acceptance Ledger Updates**:
   - Update `docs/quality/acceptance.md` with the verified error test matrix across all 54 operations.

## Acceptance Criteria

1. All Bruno tests execute and pass with zero failures against a running Docker stack (`make acceptance-live`).
2. Every negative error response in Bruno asserts status, `code`, `numericCode`, and `errorName`.
3. Dual-client compatibility tests confirm that clients ignoring `numericCode` continue operating without deserialization errors.
4. Live E2E multi-service test suites pass with 100% success.
5. All verification commands execute cleanly.

## Validation Commands

```powershell
uv run python tools/contracts/validate.py
make contracts
git diff --check
```

## Evidence Expectations

- Bruno test execution report showing 100% assertion pass rate.
- E2E acceptance test run logs recorded in task progress update.

## Rollout & Rollback Strategy

- Test suite and fixture updates.
- Zero production code impact.
- Rollback: Revert fixture updates if assertions conflict with active API versions.
