# CORE-22: Harden group updates with transactional change effects

Define optimistic/concurrent rename semantics and atomically persist the group
update, audit record, synchronization change, and outbox event. Depends on
CORE-18, CORE-11, and CORE-10. Owns the group update path and focused integration
tests. Evidence must include a two-transaction PostgreSQL concurrency scenario.

## Current increment

`JpaGroupStore.update` now takes the pessimistic group lock and persists a group
audit record, sync change, and `group.renamed.v1` outbox message in the same
transaction as the rename and revision increment. The two-transaction
concurrency test now exercises two simultaneous JPA updates and verifies
revisions 1 and 2 are both committed; production PostgreSQL confirmation remains
open.
