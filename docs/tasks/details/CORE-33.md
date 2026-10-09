# CORE-33: Multi-currency end-to-end integration and zero-sum property testing suite

## Objective

Build an automated regression and property-testing suite validating the complete multi-currency financial lifecycle and zero-sum ledger invariants across Expense Core:
- Implement a comprehensive multi-currency integration test (`PostgresMultiCurrencyLedgerTest`) executing against PostgreSQL:
  - Create a group with default currency `EUR`.
  - Incur expenses in `EUR`, `USD`, and `GBP` with mixed split modes (`EQUAL`, `EXACT`, `PERCENT_BASIS_POINTS`, `WEIGHTED_SHARES`).
  - Verify that `balances()` returns isolated, currency-separated net balances.
  - Request `getSettlementSuggestions()` and verify that suggestions are generated independently per currency without cross-currency conversion.
  - Record settlements in each currency (`USD`, `EUR`, `GBP`) and verify that the corresponding currency balance nets to zero while other currencies remain unaffected.
  - Reverse one settlement and verify that compensating postings only affect the target currency.
  - Run the ledger reconciliation queries from `docs/operations/ledger-reconciliation.md` to prove zero-sum balance and coverage across all currencies.
- Implement property-based invariant tests verifying:
  - $\sum \text{postings} == 0$ for every `(group_id, currency)` after arbitrary sequences of creates, updates, deletes, and settlements.
  - Zero minor-unit rounding leakage across arbitrary participant counts and basis point allocations.

## Design Patterns & Architectural Guidance

- **Property-Based Testing / Invariant Checking**: Rather than testing only static fixture examples, generate randomized participant graphs and transaction sequences to prove that the zero-sum ledger invariant holds universally under any combination of operations.
- **Testcontainers / PostgreSQL Isolation Pattern**: Test financial invariants against real PostgreSQL rather than an in-memory database to exercise actual Flyway migrations, locking, decimal aggregations, and constraint enforcement.

## Dependencies

- `CORE-29`
- `CORE-30`
- `CORE-31`
- `CORE-32`

## Owned paths

- `app/expense-core/src/test/kotlin/com/subhrodip/squarewise/expensecore/settlements/`
- `app/expense-core/src/test/kotlin/com/subhrodip/squarewise/expensecore/expenses/`
- `docs/tasks/details/CORE-33.md`

## Acceptance criteria

- `PostgresMultiCurrencyLedgerTest` passes against PostgreSQL with multi-currency groups (`EUR`, `USD`, `GBP`).
- Ledger reconciliation script returns 0 discrepancies across all groups and currencies.
- Property test verifies 100% zero-sum ledger compliance across 500+ randomized multi-currency mutations.
- Multi-currency lifecycle verified through REST endpoints with MockMvc.

## Validation commands

- `./gradlew :app:expense-core:test --no-daemon`
- `make contracts`
- `git diff --check`
