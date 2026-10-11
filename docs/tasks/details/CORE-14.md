# CORE-14: Durable expense update, deletion, and posting reversal

Implement persistent JPA entities, repositories, and REST endpoints for expense update (`PUT`)
and deletion (`DELETE`) with double-entry balance posting reversals in `app/expense-core`.

## Acceptance criteria

- `PUT /groups/{groupId}/expenses/{expenseId}` updates expense details, increments group revision, reverses previous balance postings, records new postings, and emits `expense.updated` outbox event and sync change.
- `DELETE /groups/{groupId}/expenses/{expenseId}` deletes/tombstones expense, increments group revision, reverses active balance postings, and emits `expense.deleted` outbox event and sync tombstone.
- Stale edits (version mismatch) reject with HTTP 409 Conflict under optimistic concurrency.
- Balance postings maintain double-entry net-zero sum invariant across group members upon update and deletion.
- All operations execute in an atomic database transaction.

## Implementation details

- **Flyway Migration**: Created `app/expense-core/src/main/resources/db/migration/V6__add_expense_soft_delete.sql` adding `deleted BOOLEAN NOT NULL DEFAULT FALSE` and `updated_at TIMESTAMP WITH TIME ZONE`.
- **Domain & DTOs**:
  - Defined `UpdateExpenseRequest` with Bean Validation annotations matching OpenAPI `UpdateExpense`.
  - Added `deleted` and `updatedAt` to `ExpenseRecord`.
- **Persistence & Entities**:
  - Added `deleted` and `updatedAt` fields to `ExpenseEntity`.
  - Filtered queries in `ExpenseRepository`: `findByGroupIdAndDeletedFalseOrderByCreatedAtDesc` and `findByGroupIdAndCategoryAndDeletedFalseOrderByCreatedAtDesc`.
- Added database-side posting aggregation by expense for reversal calculation.
- **Double-Entry Balance Posting Reversals**:
- On update/delete, previous balance postings for the expense are aggregated by
  participant and currency in the database.
- Each non-zero net sum is inverted and appended as a compensatory posting
  (`amountMinor = -sum`), strictly preserving immutable ledger history and
  maintaining zero-sum invariants (`FIN-05`, `FIN-09`) without loading all
  historical posting rows into application memory.
  - For edits, new postings corresponding to the updated payers and allocations are appended.
  - In-place collection synchronization for `payers` and `allocations` prevents transient unique constraint violations on `(expense_id, participant_id)`.
- **Group Revision & Event Sinks**:
  - Increments `group.revision` atomically within the same transaction.
  - Produces `expense.updated` / `expense.deleted` outbox events.
  - Appends sync changes / tombstones (`syncStore.delete`).
- **REST Endpoints**:
  - Implemented `PUT /expense-core/v1/groups/{groupId}/expenses/{expenseId}` and `DELETE /expense-core/v1/groups/{groupId}/expenses/{expenseId}` in `ExpenseController`.
  - Added unit and MockMvc tests verifying happy path, stale version 409 conflict, and 204 No Content.

## Verification evidence

- Contract validation:
  ```
  python3 tools/contracts/validate.py
  # valid JSON: contracts/rest/expense-core.openapi.json
  # valid task registry: 58 tasks
  ```
- Module tests:
  ```
  ./gradlew :app:expense-core:test --no-daemon
  # 68 tests completed, 0 failed, BUILD SUCCESSFUL
  ```
- Full test suite re-execution:
  ```

## 2026-10-11 scale audit remediation

Expense update and delete reversal now consume grouped database aggregates
instead of materializing the full posting history for the expense. This keeps
the reversal heap cost proportional to distinct participant/currency pairs.
  ./gradlew test --rerun --no-daemon
  # 30 actionable tasks: 5 executed, 25 up-to-date, BUILD SUCCESSFUL
  ```
