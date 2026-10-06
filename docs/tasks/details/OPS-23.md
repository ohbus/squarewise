# OPS-23: Fixture-backed mutation capacity scenarios

Add safe k6 write scenarios for Expense Core expense creation/update and
settlement operations. The harness must create isolated fixture groups, use
unique UUIDv7 expense IDs and idempotency keys, validate 201/200/409 outcomes,
and clean up or isolate generated data after a run.

Acceptance requires ledger totals, balances, outbox counts, duplicate delivery,
and authorization behavior to remain correct under the configured write rate.
The 1M-user profile must not label a read request as write capacity after this
task is complete.

Validation: `make load-k6-validate`, fixture setup/cleanup checks, k6 mutation
run, and post-run financial reconciliation.

## Local verification note (2026-09-19)

The fixture-backed mutation script was exercised against the local Expense Core
stack and archived its fixture group during teardown. The local host-native
stack also verified Accounts, Expense Core, Notifications, and BFF readiness,
including a GraphQL `me` plus `groups` query. This is local integration evidence
only; representative write-capacity and post-run ledger/outbox reconciliation
remain open before OPS-23 can be closed.

A 5-second local run completed 250/250 writes with 0% HTTP failures and p95
latency of 15.57 ms. Its archived fixture reconciled to 250 expenses, 500
balance postings, 251 outbox records, 251 sync changes, and group revision 251.
An identical replay returned 201 without creating a second expense or ledger
effect. This is bounded local evidence, not 1M-user capacity evidence.

The 1M baseline profile now creates and archives its own fixture group and sends
real idempotent expense writes. A 5-second profile smoke completed 5,003
iterations at approximately 996 requests/second with 0% failures and passed
all thresholds. A concurrent 30-second mixed run maintained 0% failures but
exceeded Expense Core latency thresholds under local saturation; that result is
capacity evidence, not a pass claim.

Automated post-run financial reconciliation is implemented and verified via
`tools/ops/reconcile_mutation_fixture.py` and `make load-mutation-check`. The tool
queries Expense Core endpoints to verify zero-sum balance invariant across currencies,
monotonic sync changes, idempotent replay behavior without revision bumps (HTTP 201),
and rejection of mismatched idempotent replays with HTTP 409 conflict. This closes
the bounded local fixture and reconciliation increment for OPS-23. It does not
close representative production-like write-capacity evidence, multi-replica
contention, or target-environment SLO validation; those remain owned by QA-08.
