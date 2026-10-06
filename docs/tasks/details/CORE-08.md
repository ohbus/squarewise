# CORE-08: Durable Expense Core persistence

Move a meaningful aggregate from an in-memory implementation behind a
service-local JPA adapter and Flyway migration. Preserve deterministic unit
tests and keep Hibernate in schema-validation mode.

Settlements are now durable and group scoped. Reversal uses pessimistic
locking, while the in-memory adapter remains available for deterministic unit
tests. Groups, synchronization, outbox, and other aggregates remain in memory.
