# CORE-13: Durable expense ledger entity and posting persistence

Implement persistent JPA entities, Flyway migrations, and repositories for expense creation,
payers, and allocation splits in `app/expense-core`.

## Acceptance criteria

- Expense records, multiple payers, and allocation line items persist via JPA and Flyway schema.
- Expense creation commits atomically with ledger postings, group revision increments, and transactional outbox entries.
- Stale edits conflict under optimistic concurrency `@Version`.

## Implementation details

- **Flyway Migration `V5__create_expenses_and_ledger.sql`**:
  - `expenses`: Primary table holding UUID `expense_id`, `group_id` foreign key with cascade, positive `amount_minor`, 3-letter currency regex constraint, allocation mode, and optimistic locking `version` (default 1).
  - `expense_payers`: Supports multi-payer expenses with composite unique constraint `(expense_id, participant_id)` and positive amount checks.
  - `expense_allocations`: Line-item breakdown per participant with non-negative minor amounts and composite unique constraint.
  - `balance_postings`: Double-entry style financial postings (+amount for payer contribution, -amount for allocation obligation) with index on `(group_id, participant_id, currency)`.
- **Domain & Calculation Model (`ExpenseDomain.kt`, `AllocationCalculator.kt`)**:
  - Expanded `AllocationCalculator` to support `EQUAL`, `EXACT`, `PERCENT_BASIS_POINTS`, and `WEIGHTED_SHARES`.
  - Added REST DTOs (`CreateExpenseRequest`, `ExpenseResponse`, `GroupBalancesResponse`) and domain records (`ExpenseRecord`, `ExpensePayer`, `ExpenseAllocation`).
- **JPA Entities & Repositories (`ExpenseEntities.kt`)**:
  - `ExpenseEntity`, `ExpensePayerEntity`, `ExpenseAllocationEntity`, and `BalancePostingEntity`.
  - `ExpenseRepository` and `BalancePostingRepository` with JPQL `sumBalancesByGroup` grouping balances by participant and currency.
- **Service & Atomicity (`ExpenseStore.kt`, `ExpenseController.kt`)**:
  - `JpaExpenseStore`: In a single transaction (`@Transactional`), increments `expense_groups.revision`, saves expense entity and payers/allocations, writes balance postings, creates `expense.created` message in `transactional_outbox`, and pushes to `SynchronizationStore`.
  - Enforces idempotency: repeated request with identical payload returns existing record; repeated request with different payload throws HTTP 409 Conflict.
  - `ExpenseController`: Exposes `POST /expense-core/v1/groups/{groupId}/expenses`, `GET /expense-core/v1/groups/{groupId}/expenses`, and `GET /expense-core/v1/groups/{groupId}/balances`.
- **Error Handling Fixes (`libs/errors/GlobalErrorHandler.kt`)**:
  - Added handlers for `HttpMessageNotReadableException`, `ServletRequestBindingException`, and `MethodArgumentTypeMismatchException` to correctly return HTTP 400 with `ErrorCode.VALIDATION_FAILED` per `docs/architecture/error-flow.md`.
- **OpenAPI Contract**:
  - Updated `/groups/{groupId}/expenses` and `/groups/{groupId}/balances` in `contracts/rest/expense-core.openapi.json` to `x-implementation-status: implemented-durable`.

## Verification evidence

- `./gradlew :app:expense-core:test --no-daemon`: 62 tests passed including `JpaExpenseStoreTest` and `ExpenseControllerTest`.
- `./gradlew test --no-daemon`: All test suites across the repository passed.
- `python3 tools/contracts/validate.py`: All contracts valid.
- `git diff --check`: Working tree clean of trailing whitespace/conflicts.
