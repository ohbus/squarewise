# ERRC-27: Observability, alerts, and runbooks

## Context & Parent Milestone

- **Parent Workstream**: [`ERRC-01: Design six-digit domain error-code migration`](ERRC-01.md)
- **Phase**: Phase 5 — Clients, acceptance, scale, rollout
- **Implementation Strategy**: [`docs/architecture/error-code-refactoring.md`](../../architecture/error-code-refactoring.md)

## Objective

Create operational Grafana dashboards, Prometheus alerting rules, and incident remediation runbooks for the six-digit error code standard. Ensure that every critical, availability, data-consistency, and retryable error code maps to an actionable alert and a step-by-step operator runbook. Validate cross-region metric aggregation without embedding region tags into error identities.

## Dependencies

- Preceding: [`ERRC-14`](ERRC-14.md), [`ERRC-19`](ERRC-19.md), [`ERRC-20`](ERRC-20.md), [`ERRC-21`](ERRC-21.md), [`ERRC-22`](ERRC-22.md), [`ERRC-23`](ERRC-23.md)

## Owned Paths

- `docs/tasks/details/ERRC-27.md`
- `infra/observability/dashboards/error-taxonomy-overview.json`
- `infra/observability/alerts/error-rules.yml`
- `docs/operations/runbooks/errors/`

## Architecture & Design Patterns

- **Actionable Alerting Pattern**: Alerts must represent actionable conditions requiring human intervention or automated runbook execution, avoiding noisy alerts for expected client 4xx rejections.
- **Hierarchical Dashboard Drill-Down**: Grafana dashboards allow operators to drill down: Global Overview -> Domain -> Module -> Exact 6-digit Code (`DM-L-C-EE`).
- **Standardized Runbook Structure**: Every runbook under `docs/operations/runbooks/errors/` follows a uniform template: Summary, Severity, Blast Radius, Immediate Triage, Diagnostic Commands, Remediation Steps, and Rollback Procedures.
- **Bounded Tag Dimensioning**: Prometheus alert expressions aggregate by bounded dimensions (`domain`, `category`, `code`), avoiding high-cardinality label explosion.

## Common Libraries & Framework Integration

- **Prometheus & Grafana**: Alertmanager rule definitions and dashboard JSON models.
- **`libs/observability`**: Micrometer metric emission (`squarewise_errors_total`).

## Technical Requirements & Deliverables

1. **Grafana Error Dashboard (`infra/observability/dashboards/error-taxonomy-overview.json`)**:
   - Visual panels:
     - Error rate by Domain (Accounts, Expense Core, Notifications, BFF, Platform).
     - Top 10 six-digit errors by frequency.
     - 4xx client errors vs 5xx server failures ratio.
     - Outbox & message dead-letter counters.
     - Latency impact of errors.
2. **Prometheus Alert Rules (`infra/observability/alerts/error-rules.yml`)**:
   - High 5xx Server Error Rate (> 1% over 5m window).
   - High Data Consistency Failures (`category="6"` > 0 events).
   - Outbox Delivery Relay Failure (`numericCode="285701"` > 5 events).
   - Rapid Dead-Letter Queue Ingestion (> 10 messages in 1m).
3. **Comprehensive Runbook Suite (`docs/operations/runbooks/errors/`)**:
   - `group-not-found.md` (`213201`)
   - `db-pool-exhausted.md` (`938801`)
   - `flyway-migration-failed.md` (`938101`)
   - `auth-token-revoked.md` (`137402`)
   - `email-dispatch-failed.md` (`345701`)
   - `upstream-timeout.md` (`428701`)
4. **Synthetic Error Alert Verification**:
   - Exercise Prometheus alerting rules against simulated error telemetry to confirm trigger fidelity.

## Acceptance Criteria

1. Grafana dashboard JSON imports cleanly and renders all panels with zero missing metric errors.
2. Alert rules validate against Prometheus rule linter (`promtool check rules`).
3. Every error with `severity=CRITICAL` or `category=6/8` in `error-catalog.yaml` links directly to a valid markdown runbook.
4. Tabletop exercise verifies an on-call engineer can triage an error from dashboard to runbook in < 2 minutes.
5. All verification commands execute cleanly.

## Validation Commands

```powershell
uv run python tools/contracts/validate.py
git diff --check
```

## Evidence Expectations

- Clean validation output for alert rules and dashboard definitions.
- Tabletop drill verification notes recorded in task progress update.

## Implementation Notes and Evidence

- Added `error-taxonomy-overview.json` with domain rate, top-ten six-digit code, 4xx/5xx ratio, outbox/dead-letter activity, and latency-impact panels using bounded labels only.
- Added `error-rules.yml` for the four required alert families: >1% 5xx over five minutes, data-consistency failures, more than five outbox relay failures, and rapid dead-letter ingestion.
- Added the six requested standardized runbooks under `docs/operations/runbooks/errors/`.
- Dashboard JSON and alert YAML parse successfully; catalog validation and `git diff --check` pass. Pinned `promtool` linting reports four valid rules, and synthetic rule tests fire all four required alerts.
- All 21 critical/category-6/8 catalog records now point to existing repository markdown runbooks. The three requested operational codes (`938801`, `938101`, `428701`) remain documented as operational names because they are not allocated identities in the frozen catalog.
- A timed dashboard -> alert -> runbook tabletop drill passed in 0.004s for the outbox relay failure path.
- ERRC-27 acceptance is complete; the catalog/runbook linkage, native rule lint, synthetic firing, and tabletop evidence are recorded in the progress ledger.

## Rollout & Rollback Strategy

- Operations and monitoring infrastructure rollout.
- Zero risk to application traffic.
- Rollback: Revert dashboard JSON and alert YAML if syntax errors occur.
