# ERR-06: Expense and idempotency error migration

Migrate expense validation, allocation, duplicate idempotency, update conflict,
soft deletion, search, and export failures. Field metadata identifies only safe
parameter names. Tests verify rejected commands do not create postings, audit,
outbox, or sync effects.
