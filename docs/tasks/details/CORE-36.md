# CORE-36: Consolidate settlement persistence port interfaces and eliminate redundant interface layer

## Objective

Consolidate single-implementation settlement persistence interfaces adhering to KISS and YAGNI principles:
1. **Interface consolidation**: Merge `SettlementCommandStore` into `SettlementStore` and remove the redundant `SettlementCommandStore.kt` interface file.
2. **Authoritative port**: Ensure `SettlementStore` directly defines `record(groupId: UUID, settlement: Settlement): Settlement` and `reverse(groupId: UUID, settlementId: UUID, reason: String): Settlement`.
3. **Adapter alignment**: Update `JpaSettlementStore` and test fake `InMemorySettlementStore` to implement `SettlementStore` directly.
4. **KDoc**: Add structured doc comments on `SettlementStore` detailing the persistence invariants, idempotency guarantees, and reversal mechanics.

## Dependencies

- `CORE-35`

## Owned paths

- `app/expense-core/src/main/kotlin/com/subhrodip/squarewise/expensecore/settlements/persistence/SettlementStore.kt`
- `app/expense-core/src/main/kotlin/com/subhrodip/squarewise/expensecore/settlements/persistence/SettlementCommandStore.kt`
- `docs/tasks/details/CORE-36.md`

## Acceptance criteria

- `SettlementStore` directly declares `record` and `reverse`.
- `SettlementCommandStore.kt` is deleted and no dangling imports or references remain.
- `JpaSettlementStore` and all test fakes compile and pass all tests against `SettlementStore`.
- Full project tests in `:app:expense-core:test` pass cleanly.
- `git diff --check` passes cleanly.

## Validation commands

- `./gradlew :app:expense-core:compileKotlin --no-daemon`
- `./gradlew :app:expense-core:test --no-daemon`
- `python3 tools/contracts/validate.py`
- `git diff --check`

## Status

done

## Implementation notes

- Consolidated the settlement persistence contract in `SettlementStore` and removed the redundant `SettlementCommandStore` interface.
- Updated the JPA adapter and in-memory test store to use the consolidated port.

## Verification evidence

- `./gradlew :app:expense-core:test --no-daemon`: passed across the workspace.
- `python3 tools/contracts/validate.py`: passed.
- `git diff --check`: passed.
