# CQRS-oriented data access and PostgreSQL reader scaling plan

## Purpose and decision

This document defines the target data-access architecture for the Accounts,
Expense Core, and Notifications services when each service has one writable
PostgreSQL endpoint and zero or more read-only PostgreSQL replicas. It is an
implementation plan, not permission to add replicas or change service
boundaries. The current system remains writer-authoritative until the rollout
gates below are met.

The design is **CQRS-oriented, not event-sourced**:

- Commands execute against the writer and commit the aggregate mutation,
  audit record, synchronization change, and outbox record in one local
  transaction where required by the existing architecture.
- Queries use dedicated read ports and projections/DTOs. They may use a
  healthy replica only when the requested consistency policy permits it.
- The writer remains the source of truth. Read replicas, query projections,
  caches, and WebSocket hints are disposable accelerators.
- No cross-service database access, shared entities, distributed transaction,
  or automatic fallback from a failed write to a reader is allowed.

This preserves the accepted financial invariant: a successful command is
durable on the writer before its HTTP response or committed outbox event is
observable.

## Current-state findings

The repository already provides useful foundations:

1. Each stateful service owns PostgreSQL, Flyway migrations, private JPA
   entities, and an application-owned store boundary. The BFF has no database.
2. `libs/db` currently exposes only Spring Data JPA as a dependency and does
   not yet provide connection routing, health policy, transaction guards, or
   query telemetry.
3. `@Transactional(readOnly = true)` is present in stores, but it is not a
   routing contract. It cannot by itself guarantee replica safety, transaction
   scope, or causal consistency.
4. Expense Core contains the highest correctness sensitivity: group locks,
   membership revalidation, idempotency, ledger postings, settlements,
   recurrence claims, synchronization revisions, and outbox publication.
5. Repository methods include ordinary JPA reads, pessimistic/native locking,
   bounded pagination, and specialized SQL. These must be classified before
   any route is changed.
6. Open Session in View is already disabled. This must remain true because
   lazy associations and connection ownership must be resolved inside an
   explicit application transaction.
7. Actuator/Micrometer exists, but query identity, pool saturation, route,
   replica lag, and database wait diagnostics are not yet a unified contract.
8. Architecture documentation deliberately defers replicas and projections;
   this plan supersedes that deferral only after the documentation gate and
   measured capacity evidence are accepted.

## Target layering

Each feature slice must expose separate command and query ports. A controller
or GraphQL resolver may call an application handler, but must not select a
`DataSource`, JPA repository, SQL string, or replica directly.

```text
transport
  -> command handler -> command port -> writer adapter -> writer transaction
  -> query handler   -> query port   -> read policy -> query adapter
                                           |-> writer
                                           |-> replica pool
                                           |-> projection (future, explicit)
```

The ports return immutable domain/application DTOs, never JPA entities. Query
adapters should prefer purpose-built projections or JDBC/native SQL for hot,
read-only paths; JPA remains appropriate for aggregate loading and ordinary
queries. Every adapter has a stable operation name and an explicit result
bound/cardinality.

Recommended shared concepts in `libs/db`:

- `DbOperationKind`: `COMMAND`, `QUERY`, `LOCKING_QUERY`, `CLAIM`,
  `MIGRATION`, and `RECONCILIATION`.
- `ReadConsistency`: `STRONG`, `SESSION`, `BOUNDED_STALENESS`, and
  `EVENTUAL`.
- `ReadTarget`: `WRITER`, `REPLICA_POOL(name)`, and `PROJECTION(name)`.
- `DbExecutionContext`: operation name, service, feature, authenticated
  subject hash/tenant scope where safe, consistency requirement, deadline,
  correlation/trace ID, and optional causal revision/token.
- `ReadPolicy`: a validated decision that maps a query capability to an
  allowed target set, timeout, retry budget, and fallback rule.
- `DbRoute`: immutable route decision recorded in telemetry.

These are technical policies only. Business authorization and ownership remain
inside each service.

## Routing and consistency rules

### Commands and unsafe reads

The writer is mandatory for all inserts, updates, deletes, DDL, locking reads,
idempotency claims, rate-limit bucket updates, outbox/inbox deduplication,
worker claims, reconciliation writes, and any query used to decide a mutation.
This includes queries annotated or named as read-only if they participate in a
command transaction. `SELECT ... FOR UPDATE`, advisory locks, and transaction
isolation-sensitive SQL must never reach a replica.

### Strong reads

Use the writer for post-command GETs, read-after-write flows, authorization
decisions, current group membership, expense/settlement confirmation, sync
cursor advancement, and any endpoint whose contract promises the committed
state. The command response should preferably contain the committed result so
clients do not need an immediate follow-up query.

### Replica-eligible reads

Replica routing is opt-in per query capability, not inferred from HTTP GET.
Initial candidates are immutable profile lookup, notification history, bounded
search, historical audit browsing, and other explicitly eventual views. Each
candidate needs a freshness budget and a documented stale-data consequence.
Financial balances, active memberships, idempotency status, sync feeds, and
security/session state remain writer-only until a separately reviewed causal
design exists.

### Session consistency

After a successful command, issue a writer watermark (for example commit LSN
or a service-level revision). A later query may use a replica only after health
metadata proves it has replayed at least that watermark. Otherwise route to the
writer until the deadline expires. Do not implement this with wall-clock
timestamps alone.

### Failure and fallback

Replica connection failure, lag over budget, pool exhaustion, or circuit-open
state removes that target from the eligible set. A query may fall back to the
writer only if its policy explicitly permits it and the writer budget protects
command traffic. There is no reader-to-reader retry for a write and no silent
weakening of consistency. If no permitted target exists, return a classified
temporary-unavailable response and emit an alertable reason.

## Connection architecture

`libs/db` should provide a Spring Boot auto-configuration with separate pools:

- `writer`: primary JDBC/Hikari pool, migrations, commands, locks, and strong
  reads;
- named `reader` pools: one pool per configured replica group, never shared
  with writer traffic;
- optional `projection` pools later, only for separately owned schemas.

Configuration must be namespaced, validated at startup, and fail closed for
production when a required writer is missing. Local development keeps one
database and may point the reader URL at the writer with an explicit
`reader-is-writer=true` diagnostic marker; this is not replica evidence.

Pool sizing is derived from measured database and application concurrency, not
replica count. Configure connection timeout, acquisition timeout, socket/read
timeout, max lifetime, keepalive, leak detection for non-production diagnostics,
and a bounded per-route concurrency limit. The sum of all service pools must
fit PostgreSQL `max_connections` with reserved capacity for migrations,
operators, failover, and health checks.

Flyway and Hibernate validation must always use the writer datasource. A
replica must never run migrations. JPA EntityManager factories must not be
silently mixed across pools; either use a routing datasource with a transaction
route fixed at transaction start or use explicit writer/reader transaction
managers. The preferred first implementation is a routing datasource with a
single persistence unit and a strict transaction-bound route guard.

`@Transactional(readOnly = true)` may remain a Hibernate optimization, but the
actual route comes from `DbExecutionContext`. A write transaction attempting a
reader route throws before SQL execution. A reader transaction attempting a
write-capable repository operation is rejected by tests and, where practical,
by database credentials with read-only privileges.

## Query and mutation performance model

Every database operation must have a stable low-cardinality operation name,
for example `expense.search.page`, not a raw SQL string or user input. Capture:

- route/target, pool, database, operation name, outcome, timeout and retry;
- duration histogram and count, including transaction duration separately;
- rows returned/affected, page size, and bounded cardinality;
- connection acquisition time, active/idle/pending pool gauges;
- PostgreSQL wait event/deadlock/serialization/lock-timeout classification;
- replica replay lag and route health;
- trace/span correlation without recording SQL parameters, tokens, email
  addresses, invitation secrets, or financial payloads.

Slow-query identification uses layered evidence:

1. Application histogram thresholds by operation and percentile, with separate
   command/query SLOs.
2. PostgreSQL `pg_stat_statements` for normalized total time, mean, p95,
   calls, shared-block reads/hits, temp blocks, rows, and WAL impact.
3. Periodic `EXPLAIN (ANALYZE, BUFFERS, WAL, SETTINGS)` in a controlled,
   production-like environment; never run `ANALYZE` casually on production
   mutations and never expose plans with secrets.
4. Lock/blocked-session reports, deadlock logs, autovacuum/bloat and index
   usage reports, replica replay and replication-slot health.
5. Load tests using a documented operation mix, hot-group contention,
   pagination depth, concurrent idempotency, outbox pressure, and replica
   lag/failure scenarios.

Do not optimize solely for one slow trace. A remediation record must state the
operation, baseline, hypothesis, index/query/schema change, write/read cost,
correctness impact, before/after plan, and rollback. Query plans belong in
reviewable performance evidence, not in application logs.

This evidence gate also applies to foreign-key changes. Keep same-service
foreign keys as the final referential-integrity guard; move only friendly
validation and authorization into the owning service transaction. Cross-service
relationships use IDs, authenticated service calls, versioned events,
idempotency, and reconciliation because separate service databases cannot share
an atomic foreign-key constraint.

## Service classification

### Expense Core

Keep all command aggregates and financial reads on the writer initially:
groups/membership authorization, expense mutation and idempotency, postings,
balances, settlements, recurring claims, sync changes, audit writes, and
outbox. A future replica may serve bounded historical search or audit browsing
only after authorization is evaluated on the writer or through a safe
authorization snapshot. The first read-scaling slice should be a dedicated
search query port with DTO projection, stable keyset pagination, covering
indexes, and explicit eventual consistency documentation.

### Accounts

Keep credential/session validation, login rate limits, magic-link/code
consumption, export/deletion state transitions, and auth-email outbox on the
writer. Profile/preferences reads may become replica eligible after a
read-after-write token is available and privacy/authorization checks remain
correct. Never place passwordless token consumption or refresh-token family
rotation on a reader.

### Notifications

Keep inbox deduplication, delivery attempts, preferences used for a send,
leases/claims, and DLQ/operator state on the writer. Historical inbox browsing
can become a replica candidate with bounded staleness. Email delivery must not
make a privacy or authorization decision from stale data.

### BFF

The BFF remains database-free. It propagates consistency intent and causal
metadata to REST services, preserves upstream error classification, and does
not retry a mutation as a query. GraphQL aliases/batching must have explicit
downstream budgets so fanout cannot exhaust writer or reader pools.

## Implementation phases and deliverables

### Phase 0: inventory and contract gate

Create a machine-readable operation catalog covering every repository/store
method, SQL query, transaction boundary, lock/claim behavior, expected
cardinality, consistency, route, timeout, and owner. Add architecture checks
that prohibit direct datasource access outside `libs/db` infrastructure and
prohibit replica routing from command packages. Update API/architecture docs
for any changed freshness semantics.

### Phase 1: shared kernel on one database

Implement `libs/db` configuration, route context, policy registry, pool
metrics, transaction guard, health model, and test fixtures while pointing
both logical routes at the same local writer. Migrate one read-only query slice
behind a query port. Prove zero command regressions and verify route labels in
tests. This phase must be useful without replicas.

### Phase 2: explicit query slices

Split stores into command handlers and query handlers feature by feature.
Remove repository exposure from application services, replace entity-returning
reads with DTO projections, bound all result sets, and classify every query.
Use keyset pagination for deep/high-volume feeds and retain offset pagination
only where the contract requires it. Add focused repository/adapter tests and
contract tests for stale/strong behavior.

### Phase 3: replica pilot

Deploy one PostgreSQL streaming replica for one non-financial historical query.
Add replay-lag health, causal watermark checks, circuit breaking, writer
fallback only where declared, and pool-budget dashboards. Test replica pause,
lag, disconnect, promotion simulation, and writer saturation. Keep the feature
flag off by default until SLO and correctness evidence passes.

### Phase 4: measured expansion

Promote additional query capabilities individually. Introduce read projections
only when normalized query evidence shows replicas and indexing are
insufficient; define projection rebuild, lag, versioning, backfill, and
authorization rules first. Do not create a CQRS event store merely to scale
ordinary reads.

### Phase 5: operational hardening

Add capacity tests, connection-budget validation, failover/runbook evidence,
backup/restore and migration compatibility checks, alert routing, and a
rollback switch that returns every query to the writer. Establish per-operation
performance budgets and a quarterly slow-query/index review.

## Acceptance criteria and evidence

The implementation is not complete until all of the following are evidenced:

- Every persistence operation is catalogued and has a command/query port,
  consistency policy, route, timeout, cardinality bound, and owner.
- Commands, locks, claims, idempotency, authorization-critical reads, and
  migrations cannot execute against a reader; automated tests prove this.
- A successful command is durable on the writer before its response/event and
  a causal follow-up cannot observe an older replica state.
- Replica lag, health, pool saturation, route choice, slow operations, lock
  waits, and fallback counts are visible in metrics/traces/logs without
  sensitive values.
- Failure tests cover reader outage, lag, pool exhaustion, writer outage,
  transaction rollback, deadlock/serialization retry policy, and promotion
  recovery. No retry duplicates a financial mutation.
- PostgreSQL plans and load results show agreed p95/p99 budgets for the real
  operation mix; improvements include write amplification and connection
  consumption, not latency alone.
- Migration, backup/restore, schema compatibility, and rollback procedures are
  tested with the reader topology.
- Local single-database mode is clearly labelled as non-replica evidence; CI
  and production-like environments run the multi-endpoint route tests.

## Risks and explicit non-goals

The main risks are stale authorization, accidental reader use in a command
transaction, replica lag during user-visible flows, pool multiplication,
projection drift, and treating metrics as proof without representative load.
The controls above address these with writer-only classifications, route
guards, causal watermarks, bounded pools, projection contracts, and evidence
gates.

This plan does not introduce cross-service joins, sharding, multi-primary
writes, Redis as a source of truth, Kafka, event sourcing, JPA second-level
cache, or a blanket `readOnly -> replica` convention. Each requires a separate
measured architecture decision.

## Initial file/work breakdown

The implementation should be registered as a parent data-access architecture
task with independently verifiable child increments:

1. Documentation/catalog and architecture gate.
2. `libs/db` routing, policy, pool, health, and telemetry kernel.
3. Expense Core query-port pilot and writer-only command guard.
4. Accounts and Notifications query-port migrations.
5. PostgreSQL replica topology and causal-read pilot.
6. Performance evidence, failure drills, dashboards, and operational runbooks.

Each child must own its paths, declare dependencies, update the progress ledger,
and record exact Gradle, contract, SQL-plan, load, and failure-test evidence.
