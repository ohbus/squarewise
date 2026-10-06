# OPS-02 performance and recovery drills

These scripts are reproducible probes for an isolated Squarewise environment.
They do not provision infrastructure or claim production capacity.

```sh
BASE_URL=http://localhost:28080 REQUESTS=50 CONCURRENCY=5 tests/performance/capacity-smoke.sh
COMPOSE_FILE=infra/local/docker-compose.yml tests/performance/recovery-drill.sh
tests/performance/cost-estimate.sh
# Replica/routing smoke evidence (requires the merged local replica profile)
tests/performance/cqrs-replica-smoke.sh

`tests/performance/replica-disconnect-recovery.sh` safely stops only the
replica, verifies the writer remains accepting connections, then starts the
replica with Compose health waiting and verifies streaming/recovery state. It
never promotes the replica or removes volumes.

The bounded Expense Core search plan can be checked against the live local
replica with `tests/performance/expense-search-plan.sh`. Override `GROUP_ID`
to inspect a populated group; the check requires the plan to use
`expenses_group_idx` and prints the full `EXPLAIN (ANALYZE, BUFFERS, SETTINGS)`
output. This is local plan evidence, not a production workload baseline.
```

The capacity probe checks readiness and reports throughput and failures. Supply
`AUTH_HEADER` and `HEALTH_PATH` to probe an authenticated endpoint. The recovery
drill validates Compose, restarts one service, and waits for it to run. It does
not delete volumes or test backup restoration. A launch restore drill must use
an isolated PostgreSQL backup, reconcile balances, audit rows, and outbox replay.

The cost output is an arithmetic estimate from operator supplied assumptions,
not a provider quote. Set `DB_MONTHLY_EUR`, `BROKER_MONTHLY_EUR`,
`APP_NODE_MONTHLY_EUR`, `APP_NODES`, and `OPS_MONTHLY_EUR`.

`cqrs-replica-smoke.sh` emits machine-readable local evidence for PostgreSQL
streaming, replica recovery mode, replay LSN, and route telemetry. It is a
topology/routing smoke test, not production capacity, failover, or durability
proof. Set `AUTH_HEADER="Authorization: Bearer <signed-token>"` to include
protected Prometheus route telemetry; without it the script verifies health
only.
