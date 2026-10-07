# Database pool exhausted (`938801`)

## Summary

This runbook covers database availability or pool exhaustion symptoms. The requested code is retained as the operational name; verify the active catalog code before paging.

## Severity

Critical when sustained or accompanied by write failures.

## Blast Radius

Requests using the affected service database may fail or queue, including financial writes and outbox transactions.

## Immediate Triage

Check database pool acquisition latency, active connections, JVM heap, and `squarewise_errors_total` by service. Stop load tests and avoid blind retries.

## Diagnostic Commands

```text
sum by (service, pool) (rate(squarewise_db_pool_acquisition_seconds_count[5m]))
docker compose exec postgres pg_stat_activity
docker compose logs --since 15m accounts expense-core notifications
```

## Remediation Steps

Reduce concurrency, restore database reachability, and verify pool utilization and transaction completion. Reconcile outbox and ledger state before replaying work.

## Rollback Procedures

Roll back the responsible application revision only after confirming schema compatibility; retain database state and execute reconciliation afterward.
