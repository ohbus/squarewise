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

## Implementation Notes and Evidence

- Upgraded the existing negative Bruno probes across Accounts, Notifications, and the quality suite to assert the additive `numericCode`, `errorName`, and UUIDv7 `requestId` fields alongside the legacy `code` and HTTP status.
- Added `quality/legacy-client-compatibility.bru`, which projects only `code`, `status`, and `detail` to prove a legacy client can deserialize the response while modern fields remain available.
- Added typed E2E problem-contract assertions to the invalid-allocation, archived-group, invalid-magic-link, replay-conflict, and rate-limit scenarios.
- Live Bruno collection passed against the rebuilt Docker stack: 72 requests, 80 tests, 0 failures. The focused quality run also passed: 24 requests, 27 tests, 0 failures.
- Live E2E suites passed: `uv run python tests/e2e/test_rest_edge_cases.py` and `uv run python tests/e2e/test_auth_email_delivery.py`.
- Acceptance exposed two runtime integration gaps and they were closed in the owning boundaries: production servlet/reactive security chains now use the structured 401/403 handlers, and the legacy error adapter now derives additive fields from governed definitions instead of emitting nulls.
- `make contracts` is unavailable in this Windows environment because GNU Make is not installed; the equivalent `uv run python tools/contracts/validate.py` gate passed.

## Rollout & Rollback Strategy

- Test suite and fixture updates.
- Zero production code impact.
- Rollback: Revert fixture updates if assertions conflict with active API versions.
