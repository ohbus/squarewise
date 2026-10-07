# Additive error-contract rollout verification ledger

This ledger is append-only operational evidence. A row may be marked `PASSED`
only from a staging or production-like deployment with the commit SHA, traffic
stage, observed metrics, and operator sign-off recorded.

| Timestamp (UTC) | Service | Commit SHA | Stage | 5xx delta | p99 delta (ms) | Serialization failures | Unmapped codes | Rollback seconds | Status | Sign-off |
| --- | --- | --- | ---: | ---: | ---: | ---: | ---: | ---: | --- | --- |
| 2026-10-07 | Accounts | — | 5% | — | — | — | — | — | NOT EXECUTED | — |
| 2026-10-07 | Expense Core | — | 5% | — | — | — | — | — | NOT EXECUTED | — |
| 2026-10-07 | Notifications | — | 5% | — | — | — | — | — | NOT EXECUTED | — |
| 2026-10-07 | BFF | — | 5% | — | — | — | — | — | NOT EXECUTED | — |

## Rollback rehearsal

Status: `NOT EXECUTED`. A staging environment, traffic controller, and operator
approval are required before timing rollback or asserting zero data impact.

## Evidence boundary

The local Docker acceptance evidence proves additive response compatibility and
legacy projection behavior only. It does not prove 100% traffic deployment,
automated canary analysis, staging rollback time, or production data safety.
