# CORE-10: Durable transactional outbox

Persist outbox records and worker state through JPA/Flyway. Competing workers
must claim safely, while retry, acknowledgement, and parking semantics remain
compatible with the current relay domain.

The JPA adapter persists lease, retry, acknowledgement, and parking state and
claims batches with `FOR UPDATE SKIP LOCKED`. Tests use H2 PostgreSQL mode; live
PostgreSQL locking behavior remains to be verified with Testcontainers.
