# CORE-23: Verify group-rename atomic rollback and effect payloads

## Objective

Prove that a group rename either commits the group row, audit record,
synchronization change, and outbox message together, or commits none of them.
Exercise injected failures after each persistence step and verify no partial
state is observable. Assert exact event type, aggregate ID, group revision,
actor, name, and serialized payload in every effect.

## Dependencies and ownership

Depends on CORE-22. Owns Expense Core group, audit, sync, outbox integration
tests and any narrowly scoped test seams needed for deterministic failure
injection. Do not weaken production transaction boundaries to make tests pass.

## Acceptance criteria

- Failure after group save, audit save, sync append, or outbox append rolls back
  all durable effects.
- A successful rename creates exactly one audit, one sync change, and one
  outbox message with matching group ID and revision.
- Tests run against the repository's real JPA/Flyway test database.

## Validation

`./gradlew :app:expense-core:test --no-daemon`, contract validation, and diff
checks. Record failure point, database state, and exact commit evidence.
