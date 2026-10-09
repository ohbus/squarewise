# CORE-34: Align JpaSettlementStore error catalog codes and eliminate redundant reversal queries

## Objective

Remediate error code mismatches and redundant database SELECT queries in `JpaSettlementStore`:
1. **Idempotency conflict error code**: Replace `ExpenseErrors.GROUP_NAME_CONFLICT` (`213301`) with canonical `ExpenseErrors.EXPENSE_IDEMPOTENCY_CONFLICT` (`233302`) when a settlement creation is replayed with changed parameters (`fromParticipantId`, `toParticipantId`, `amountMinor`, or `currency`).
2. **Archived group error code**: Replace `ExpenseErrors.GROUP_NAME_CONFLICT` (`213301`) with canonical `ExpenseErrors.GROUP_ARCHIVED` (`213401`) in `JpaSettlementStore.checkActiveGroup`.
3. **Settlement not-found error code**: Replace `ExpenseErrors.GROUP_NOT_FOUND` (`213201`) with canonical `ExpenseErrors.SETTLEMENT_NOT_FOUND` (`243201`) when attempting to reverse a non-existent settlement.
4. **Redundant query optimization**: In `JpaSettlementStore.reverse()`, eliminate redundant calls to `checkActiveGroup(groupId)` and reuse the group retrieved at the start of the transaction.
5. **KDoc**: Add structured doc comments explaining invariants and error policies on touched methods.

## Dependencies

- `CORE-29`

## Owned paths

- `app/expense-core/src/main/kotlin/com/subhrodip/squarewise/expensecore/settlements/persistence/JpaSettlementStore.kt`
- `app/expense-core/src/test/kotlin/com/subhrodip/squarewise/expensecore/settlements/JpaSettlementStoreTest.kt`
- `docs/tasks/details/CORE-34.md`

## Acceptance criteria

- Replaying settlement recording with conflicting parameters throws `ExpenseDomainException(ExpenseErrors.EXPENSE_IDEMPOTENCY_CONFLICT)`.
- Attempting to record or reverse a settlement in an archived group throws `ExpenseDomainException(ExpenseErrors.GROUP_ARCHIVED)`.
- Attempting to reverse an unrecorded settlement ID throws `ExpenseDomainException(ExpenseErrors.SETTLEMENT_NOT_FOUND)`.
- `JpaSettlementStore.reverse` queries `groupRepository` at most once.
- All unit and integration tests in `JpaSettlementStoreTest` and `:app:expense-core:test` pass cleanly.
- `git diff --check` passes cleanly.

## Validation commands

- `./gradlew :app:expense-core:test --tests "com.subhrodip.squarewise.expensecore.settlements.JpaSettlementStoreTest" --no-daemon`
- `./gradlew :app:expense-core:test --no-daemon`
- `python3 tools/contracts/validate.py`
- `git diff --check`

## Status

planned

## Implementation notes

- To be completed.

## Verification evidence

- (To be recorded upon completion)
