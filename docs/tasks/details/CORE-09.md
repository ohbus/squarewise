# CORE-09: Durable groups and invitations

Persist groups, memberships, and single-use invitations using service-local JPA
and Flyway while retaining deterministic in-memory adapters for unit tests.

The JPA adapter uses an atomic conditional invite claim and pessimistic group
serialization for idempotent membership linking. Integration tests currently
use H2 compatibility mode; a live PostgreSQL concurrency run remains pending.
