# ERRC-28: Dual-read/dual-write staged rollout

## Context & Parent Milestone

- **Parent Workstream**: [`ERRC-01: Design six-digit domain error-code migration`](ERRC-01.md)
- **Phase**: Phase 5 — Clients, acceptance, scale, rollout
- **Implementation Strategy**: [`docs/architecture/error-code-refactoring.md`](../../architecture/error-code-refactoring.md)

## Objective

Execute the staged, canary-driven production rollout of the additive six-digit error responses across all Squarewise deployments. Follow an Expand/Contract migration pattern: deploy tolerant consumers first, enable additive error emissions per service with automated canary monitoring, monitor unknown-code exceptions, and verify that legacy clients experience zero disruptions.

## Dependencies

- Preceding: [`ERRC-24`](ERRC-24.md), [`ERRC-25`](ERRC-25.md), [`ERRC-26`](ERRC-26.md), [`ERRC-27`](ERRC-27.md)

## Owned Paths

- `docs/tasks/details/ERRC-28.md`
- `infra/deploy/canary/error-rollout-config.yaml`
- `docs/operations/rollout-verification-ledger.md`
- `tools/ops/validate_error_rollout.py`

## Architecture & Design Patterns

- **Expand/Contract Pattern (Tolerant Reader First)**: Consumers deploy first with tolerance for new fields (`numericCode`, `errorName`); producers deploy second with additive emission; legacy fields are preserved throughout.
- **Canary & Phased Service Rollout**: Gradual deployment order:
  1. Internal / Platform shared libraries
  2. Accounts Service (5% -> 25% -> 100%)
  3. Expense Core Service (5% -> 25% -> 100%)
  4. Notifications Service (5% -> 25% -> 100%)
  5. GraphQL BFF Service (5% -> 25% -> 100%)
- **Automated Canary Analysis (ACA) & Rollback Triggers**: Canary automatically halts and rolls back if:
  - Client 5xx error rate increases by > 0.05%
  - P99 response latency degrades by > 10ms
  - JSON serialization failure rate > 0
  - Unmapped / malformed error code emissions > 0

## Common Libraries & Framework Integration

- **`infra/deploy`**: Compose and deployment manifests.
- **`libs/observability`**: Canary health metrics and dashboard tracking.

## Technical Requirements & Deliverables

1. **Rollout Configuration Manifest (`infra/deploy/canary/error-rollout-config.yaml`)**:
   - Defines feature toggles and canary stage percentages per service.
   - Declares explicit automated health checks and metrics thresholds.
2. **Service Canary Execution Sequence**:
   - Step 1: Deploy Accounts canary; run smoke tests; monitor 6-digit emissions.
   - Step 2: Deploy Expense Core canary; verify transactional error handling and outbox events.
   - Step 3: Deploy Notifications canary; verify dead-letter and email dispatch logs.
   - Step 4: Deploy BFF canary; verify GraphQL extensions and WebSocket subscription stability.
3. **Rollout Verification Ledger (`docs/operations/rollout-verification-ledger.md`)**:
   - Live audit record logging timestamp, service, commit SHA, canary stage, metric values, and sign-off for each phase.
4. **Rehearsal of Automated Rollback**:
   - Execute a planned canary rollback in a staging environment to verify that reverting a service instantly restores pre-rollout error formatting without service downtime or database inconsistencies.

## Acceptance Criteria

1. All four deployables (Accounts, Expense Core, Notifications, BFF) successfully deploy to 100% traffic emitting additive six-digit error fields.
2. Legacy clients continue operating with zero breaking deserialization errors or status changes.
3. Automated canary analysis shows zero increase in 5xx errors or serialization regressions.
4. Rollback rehearsal succeeds cleanly in staging in < 60 seconds without data corruption.
5. All verification commands execute cleanly.

## Validation Commands

```powershell
uv run python tools/ops/validate_error_rollout.py
uv run python tools/contracts/validate.py
git diff --check
```

## Evidence Expectations

- Completed rollout verification ledger with metrics from each deployment phase.
- Rehearsal rollback logs confirming instant fail-safe recovery.

## Implementation Notes and Evidence

- Added `infra/deploy/canary/error-rollout-config.yaml` defining tolerant-reader compatibility, 5%/25%/100% service stages, bounded canary halt thresholds, and a 60-second rollback budget.
- Added `docs/operations/rollout-verification-ledger.md` with one explicit `NOT EXECUTED` row per service and a rollback rehearsal record.
- Added `tools/ops/validate_error_rollout.py` and the `rollout-validate` target to enforce manifest safety bounds and ledger evidence shape before staging execution.
- Local Bruno/E2E compatibility evidence supports the additive contract, but no staging or production-like traffic controller is available in this session.
- ERRC-26 remains incomplete while production-like performance evidence is open;
  ERRC-27 is complete. ERRC-28 remains in progress until staged deployment
  metrics and rollback rehearsal evidence are supplied.

## Rollout & Rollback Strategy

- Production deployment milestone.
- Additive emission; existing `code` preserved.
- Rollback: Automated canary abort or rolling downgrade via deployment manifest.
