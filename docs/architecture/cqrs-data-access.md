# CQRS data-access contract

Squarewise uses explicit command/query policies at the application boundary. A
command, lock, claim, migration, reconciliation, or strong financial read uses
the writer. Only an explicitly approved query may use a named reader pool.
There is no blanket GET or read-only routing rule.

## Consistency levels

- `STRONG`: read from the writer; required for authorization, financial state,
  idempotency, revisions, and any operation immediately following a mutation.
- `SESSION`: read from a reader only after the caller's writer watermark has
  been replayed; otherwise use the writer.
- `BOUNDED_STALENESS`: use a reader only while its measured replay lag is inside
  the operation's budget; a stale or unavailable reader uses the bounded writer
  fallback when the operation explicitly permits it.
- `EVENTUAL`: an approved historical/read-model query may use a healthy reader;
  a failed reader may use bounded writer fallback. The fallback is never used
  for commands or strong reads.

## Writer-only invariants

Financial postings, balances, settlements, group membership authorization,
claims, idempotency records, revision allocation, outbox writes, delivery
leases/attempts, authentication/session/rate-limit state, migrations, and
reconciliation all remain writer-bound. Reader pools are read-only credentials
and are not used by Flyway or Hibernate schema validation.
Authorization helpers also force a writer context when nested inside a reader
query, so an eventual read cannot resurrect a revoked membership.

## Declarative Routing Annotations

Routing to reader and writer pools is configured declaratively using annotations processed by `DbRouteAspect`:
- `@DbReader(target = "namedPool", consistency = ReadConsistency.BOUNDED_STALENESS)`: Directs the execution context to an approved read replica pool.
- `@DbWriter`: Explicitly pins the context to the writer datasource.
- `@DbRouteContext`: Binds custom named contexts across controller and service boundaries.

The aspect automatically respects active transactions: write transactions retain writer affinity, and nested context invocations safely restore outer execution contexts upon completion.

## Replica Selection and Load Balancing

`DbRoutingDataSource` implements atomic round-robin load-balancing across configured, healthy read replicas. Configured writer pools are explicit shard/target pools, not an implicit load-balancing set:
- Reader replicas are health-checked continuously via `DbReaderHealth`. Unhealthy, disconnected, or circuit-broken replicas are excluded from rotation.
- If all reader replicas are degraded, unavailable, or behind a required watermark, the datasource falls back to the primary writer only for explicitly reader-eligible, non-strong queries and records bounded fallback telemetry.
- Queries requiring strict `STRONG` consistency or running inside write transactions bypass replica rotation and target the writer directly.
- An unqualified write always targets the primary writer; a named writer is selected only when the operation explicitly supplies `targetWriter`.

## In-Memory Caching and Transactional Invalidation

Hot, read-heavy domains utilize bounded in-memory caches to reduce database load:
- `GroupAccessCache`: Caches group metadata and active memberships.
- `ProfileCache`: Caches user account profiles and batch profile resolution results, ensuring fast GET `/me` reads.
- `PreferenceCache`: Caches notification preferences.

Write-path cache invalidations are coordinated locally with Spring's
`TransactionSynchronizationManager.registerSynchronization(afterCommit)` when a
transaction is active. This prevents dirty reads on the same instance, but does not
make caches or replica reads globally causal: other instances may remain stale until
invalidation propagation or TTL expiry. Financial balances and authorization bypass
these caches and use the writer.

## Local and Staging Topology

The initial local topology is diagnostic: `infra/local/docker-compose.replica.yml`
adds an asynchronous PostgreSQL streaming replica, while the default Compose
profile remains single-database writer-only mode.
