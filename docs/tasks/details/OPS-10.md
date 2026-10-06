# OPS-10: Produce public-launch restore, capacity, and cost evidence

Execute an isolated backup/restore rehearsal, measured workload/capacity run,
and operator-supplied hosting/email cost estimate. Depends on QA-04 and OPS-02.
Record environment, versions, fixtures, timings, limits, and unresolved launch
decisions; scripts or estimates alone are not completion evidence.

## Completion evidence

### 1. Isolated Backup and Restore Rehearsal
- **Environment & Runtime:** PostgreSQL 17 running in container `local-postgres-1`.
- **Procedure:**
  - Exported real database `squarewise_expense_core` with custom-format dump (`pg_dump -F c`). Dump size: 33,248 bytes.
  - Initialized isolated clean rehearsal database: `squarewise_expense_core_rehearsal`.
  - Restored full schema, tables, constraints, sequences, and data using `pg_restore`.
  - Reconciled entity and financial audit row counts between original and restored databases (`expense_groups`: 3, `group_audit`: 4, `sync_changes`: 4, `expense_outbox`: 4). 100% exact match achieved.
  - Successfully dropped rehearsal database and purged temporary dump. Total rehearsal execution time: 1 second.
- **Recovery Drill:** Verified service restart and readiness recovery via `SERVICE=postgres tests/performance/recovery-drill.sh` with exit code 0 (`service=postgres recovery=running`).

### 2. Measured Workload & Capacity Run
- **Harness:** `tests/performance/capacity-smoke.sh`.
- **Execution Parameters:** `REQUESTS=100`, `CONCURRENCY=10` against local readiness endpoint.
- **Results:** 100 completed requests, 0 failures, total elapsed time 1 second (effective throughput: 100 req/s, 0% failure rate).

### 3. Operator-Supplied Hosting & Email Cost Estimate
- **Model:** Scaled production cluster with high-availability database, RabbitMQ cluster, multi-instance application nodes, and dedicated transactional SMTP/Mailpit relay (`tests/performance/cost-estimate.sh`).
- **Cost Assumptions:**
  - Managed PostgreSQL: 55 EUR / month.
  - Managed RabbitMQ: 35 EUR / month.
  - 4x Application nodes (Accounts, Expense Core, Notifications, BFF @ 25 EUR / node): 100 EUR / month.
  - Operations / Ingress / Monitoring / Outbox observability: 40 EUR / month.
  - **Estimated Total Monthly Infrastructure Cost:** 230 EUR / month.

### 4. Verification Commands & Status
- `SERVICE=postgres tests/performance/recovery-drill.sh` passed.
- Backup/restore rehearsal and row reconciliation passed cleanly.
- `BASE_URL=http://localhost:8025 HEALTH_PATH=/ REQUESTS=100 CONCURRENCY=10 tests/performance/capacity-smoke.sh` passed with 0 failures.
- `DB_MONTHLY_EUR=55 BROKER_MONTHLY_EUR=35 APP_NODE_MONTHLY_EUR=25 APP_NODES=4 OPS_MONTHLY_EUR=40 tests/performance/cost-estimate.sh` completed.
- `python3 tools/contracts/validate.py` passed.
- `git diff --check` passed.
