# CQRS replica operations

The reader topology is optional. The default local Compose profile is
writer-only; merge `infra/local/docker-compose.replica.yml` to run the
diagnostic asynchronous PostgreSQL replica.

## Reader lag

`DbReaderLagProbe` reads PostgreSQL replay timestamp/LSN metadata from the
reader pool. A reader above the configured `squarewise.db.reader-lag-budget-ms`
is marked `LAGGING`. Explicitly approved non-strong reader queries, including
eventual and bounded-staleness reads, may use the bounded writer fallback.
Strong, command, lock, claim, migration, and reconciliation operations never
use that fallback policy.

## Reader outage

Connection/probe failures transition the named reader through disconnected and
circuit-open states. Inspect `squarewise_db_reader_failure_total`, the service
health endpoint, and the application route counters. Restore the reader or
force writer-only mode by setting `SQUAREWISE_DB_ENABLED=false` and restarting
the affected service. Do not delete PostgreSQL volumes as an outage response.

## Writer fallback

Fallback is bounded to explicitly approved non-strong reader queries. Sustained
`squarewise_db_fallback_total` growth is an incident signal because it can
consume writer capacity. Investigate reader lag/pool exhaustion and disable
replica routing before increasing writer pool limits.

## Evidence boundary

`sh tests/performance/cqrs-replica-smoke.sh` proves only local streaming,
recovery mode, replay position, and authenticated route telemetry. It is not
production capacity, failover, backup/restore, durability, or alert-routing
proof.
