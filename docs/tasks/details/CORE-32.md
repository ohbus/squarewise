# CORE-32: Multi-currency balance deterministic sorting and CSV export pagination bounds

## Objective

Remediate non-deterministic multi-currency balance sorting and CSV export row limit truncation (`AUD-08`, `AUD-09`) identified in the financial audit:
- In `JpaExpenseStore.balances(groupId)`, update the sorting comparator to order stably by participant ID and then currency: `sortedWith(compareBy<GroupBalanceItem> { it.participantId }.thenBy { it.amount.currency })`.
- In `ExpenseSearch.csv`, fix the pagination limit calculation so that `page()` is invoked with `limit = maxRows` rather than `minOf(maxRows, MAX_LIMIT)`. This allows CSV exports with up to `MAX_EXPORT_ROWS` (10,000) to proceed without false `Export exceeds the maximum row limit` exceptions when dataset size is between 1,001 and 10,000.
- Provide unit tests verifying stable balance sorting across participants with multiple currencies and large-volume CSV exports.

## Design Patterns & Architectural Guidance

- **Deterministic Contract Guarantee**: APIs must return identically ordered representations for identical ledger states regardless of underlying database execution plan variations.
- **Query / Export Separation**: Interactive paginated search queries must respect small page bounds (`MAX_LIMIT` = 1,000), while batch streaming or bulk export endpoints must support full allowable export limits (`MAX_EXPORT_ROWS` = 10,000) according to the OpenAPI specification.

## Dependencies

- `CORE-27`
- `DB-07`

## Owned paths

- `app/expense-core/src/main/kotlin/com/subhrodip/squarewise/expensecore/expenses/persistence/store/JpaExpenseStore.kt`
- `app/expense-core/src/main/kotlin/com/subhrodip/squarewise/expensecore/search/model/ExpenseSearch.kt`
- `app/expense-core/src/test/kotlin/com/subhrodip/squarewise/expensecore/search/`
- `docs/tasks/details/CORE-32.md`

## Acceptance criteria

- `JpaExpenseStore.balances` returns deterministically sorted balances: primary sort by `participantId` ascending, secondary sort by `currency` ascending.
- Multi-currency balances for the same participant always appear in consistent alphabetical order across successive calls.
- `ExpenseSearch.csv` successfully exports collections with more than 1,000 items when `maxRows` allows it (up to 10,000 items).
- All search and balance unit tests pass without regression.

## Validation commands

- `./gradlew :app:expense-core:test --tests "com.subhrodip.squarewise.expensecore.search.*" --no-daemon`
- `make contracts`
- `git diff --check`

## Implementation Notes & Evidence

- **AUD-08 Remediation**: In `JpaExpenseStore.balances`, replaced `.sortedBy { it.participantId }` with `.sortedWith(compareBy<GroupBalanceItem> { it.participantId }.thenBy { it.amount.currency })`. Guarantees stable and deterministic ordering when a participant has balances across multiple currencies.
- **AUD-09 Remediation**: In `ExpenseSearch.csv`, removed the internal delegating call to `page()` with `minOf(maxRows, MAX_LIMIT)` which improperly capped exports to 1,000 items. Matching expenses are now filtered directly against `maxRows` (up to `MAX_EXPORT_ROWS` = 10,000).
- **Evidence**:
  - `JpaExpenseStoreTest` verifies deterministic participant and currency ordering across multi-currency expenses.
  - `ExpenseSearchTest` verifies exporting 1,500 items succeeds without overflow exceptions.
  - `./gradlew :app:expense-core:test --tests "com.subhrodip.squarewise.expensecore.search.*" --tests "com.subhrodip.squarewise.expensecore.expenses.JpaExpenseStoreTest" --no-daemon` passed cleanly.
  - `make contracts` and `git diff --check` passed cleanly.
