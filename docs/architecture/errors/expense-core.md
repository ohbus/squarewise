# Expense Core error ownership guide

Status: allocated and authoritative under `ERRC-04`; matches `contracts/errors/error-catalog.yaml`.

Expense Core owns Domains `21`-`28`. Financial mutation errors must state transaction,
idempotency, audit, sync-change, and outbox outcomes. No response may imply failure if
the durable financial write committed unless the contract explicitly describes an
accepted/indeterminate result.

## Allocated public families

| Candidate | Name | Current-compatible HTTP | Legacy `code` | Required distinction |
|---|---|:---:|---|---|
| `211101` | `GROUP_REQUEST_INVALID` | 400 | `VALIDATION_FAILED` | Create/rename/archive input |
| `213201` | `GROUP_NOT_FOUND` | 404 | `NOT_FOUND` | Missing or deliberately hidden group |
| `213301` | `GROUP_NAME_CONFLICT` | 409 | `CONFLICT` | Named uniqueness rule |
| `213401` | `GROUP_ARCHIVED` | 409 | `CONFLICT` | Lifecycle conflict; replaces inconsistent legacy mapping |
| `217201` | `GROUP_ACCESS_HIDDEN` | 404 | `NOT_FOUND` | Membership denial with anti-enumeration |
| `221201` | `INVITATION_NOT_FOUND` | 404 | `NOT_FOUND` | Unknown/revoked token when safely indistinguishable |
| `223401` | `INVITATION_EXPIRED` | 409 | `CONFLICT` | Expired invitation state |
| `223402` | `INVITATION_ALREADY_CLAIMED` | 409 | `CONFLICT` | Claimed invitation state |
| `223403` | `INVITATION_REVOKED` | 409 | `CONFLICT` | Revoked invitation state |
| `223404` | `MEMBER_ALREADY_REMOVED` | 409 | `CONFLICT` | Membership lifecycle |
| `223405` | `PLACEHOLDER_ALREADY_BOUND` | 409 | `CONFLICT` | Claim lifecycle |
| `223201` | `MEMBER_NOT_FOUND` | 404 | `NOT_FOUND` | Group member absent |
| `223202` | `PLACEHOLDER_NOT_FOUND` | 404 | `NOT_FOUND` | Claim target absent |
| `231101` | `EXPENSE_REQUEST_INVALID` | 400 | `VALIDATION_FAILED` | Structural input/currency/amount/date validation |
| `233201` | `EXPENSE_NOT_FOUND` | 404 | `NOT_FOUND` | Expense absent or hidden |
| `233301` | `EXPENSE_VERSION_CONFLICT` | 409 | `CONFLICT` | Optimistic version mismatch |
| `233302` | `EXPENSE_IDEMPOTENCY_CONFLICT` | 409 | `IDEMPOTENCY_CONFLICT` | Same key, different canonical request |
| `233501` | `ALLOCATION_SUM_MISMATCH` | 400 initially | `VALIDATION_FAILED` | Split does not equal total; future 422 is breaking |
| `233502` | `PARTICIPANT_SET_INVALID` | 400 | `VALIDATION_FAILED` | Payer/participant membership rule |
| `233503` | `DUPLICATE_EXPENSE_REJECTED` | 409 | `CONFLICT` | Semantically duplicate mutation |
| `243201` | `SETTLEMENT_NOT_FOUND` | 404 | `NOT_FOUND` | Settlement absent or hidden |
| `243301` | `SETTLEMENT_VERSION_CONFLICT` | 409 | `CONFLICT` | Concurrent settlement mutation |
| `243401` | `SETTLEMENT_ALREADY_REVERSED` | 409 | `CONFLICT` | Reversal lifecycle |
| `243501` | `INSUFFICIENT_BALANCE` | 422 | `VALIDATION_FAILED` initially | Preserve actual current contract during catalog audit |
| `253201` | `SCHEDULE_NOT_FOUND` | 404 | `NOT_FOUND` | Recurring schedule absent |
| `253301` | `SCHEDULE_VERSION_CONFLICT` | 409 | `CONFLICT` | Concurrent schedule mutation |
| `253401` | `SCHEDULE_PAUSED` | 409 | `CONFLICT` | Invalid current state |
| `261101` | `SYNC_CURSOR_INVALID` | 400 | `VALIDATION_FAILED` | Decode/version/signature failure |
| `261102` | `SYNC_CURSOR_EXPIRED` | 400 initially | `VALIDATION_FAILED` | Future 410 requires explicit contract versioning |
| `263301` | `SYNC_REVISION_CONFLICT` | 409 | `CONFLICT` | Offline replay base revision mismatch |
| `271101` | `SEARCH_QUERY_INVALID` | 400 | `VALIDATION_FAILED` | Filter/sort/date/cursor validation |
| `271102` | `EXPORT_REQUEST_INVALID` | 400 | `VALIDATION_FAILED` | Export bounds/format validation |

The exact HTTP/legacy mapping for `INSUFFICIENT_BALANCE` must be derived from current
contracts and tests before freeze; the candidate numeric identity does not authorize a
status change.

## Internal and asynchronous families

Define distinct registered failures for unknown persistence constraint, lock timeout,
deadlock retry exhaustion, connection-pool/database unavailability, inconsistent
ledger, posting imbalance, missing audit/sync/outbox companion record, outbox claim
loss, publish nack/timeout, relay retry exhaustion, recurrence lock contention,
occurrence poison, partial batch, CSV serialization, and object-storage/export failure.
Translate a platform failure only when Expense Core offers a different stable semantic
contract.

## Exception and transaction rules

- Expected domain rejection uses typed outcomes or fixed-definition exceptions; leaf
  types exist only for recovery/catching value.
- Known optimistic and named-constraint failures translate at repository/application
  adapters; unknown persistence exceptions remain internal and keep their cause.
- A reusable missing-resource exception accepts only an Expense-owned narrow family,
  not arbitrary definitions.
- Financial posting, audit, sync change, and outbox persistence share the documented
  local transaction. Mapping must never obscure whether it committed.
- Offline replay, idempotency, and outbox retry preserve stable identity across retries.

## Required tests

Cover every missing/hidden resource, archived and membership behavior, invite and
placeholder lifecycle, amount/currency/rounding/participant/allocation validation,
idempotency equivalence and conflict, optimistic races, settlement reversal and
balance rules, schedule locks and poison occurrences, cursor tamper/version/expiry,
offline revision conflict, database constraint/deadlock/timeout/outage, transaction
rollback/commit ambiguity, outbox nack/retry/parking, export failure, redaction, and
stable public envelope/status/header behavior.
