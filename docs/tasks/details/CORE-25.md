# CORE-25: Verify group-rename concurrency against PostgreSQL

Replace the H2/JPA concurrency smoke with a real PostgreSQL two-transaction
scenario. Hold one row lock, start a competing rename, release the first
transaction, and assert serialized revisions, no lost update, one audit/sync/
outbox effect per commit, and deterministic final state. Capture timing and
database/runtime versions. Depends on CORE-22 and OPS-09.
