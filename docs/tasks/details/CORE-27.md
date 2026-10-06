# CORE-27: Refactor Expense Core expenses, sync, and outbox persistence into separated SOLID files

## Objective

Refactor multi-responsibility persistence files in Expense Core (`expenses`, `sync`, and `messaging`) into enterprise-ready, dedicated single-responsibility files adhering to SOLID principles:
- Split `ExpenseEntities.kt`:
  - `ExpenseEntity.kt`
  - `ExpensePayerEntity.kt`
  - `ExpenseAllocationEntity.kt`
  - `BalancePostingEntity.kt`
  - `ExpenseRepository.kt`
  - `BalancePostingRepository.kt`
- Split `ExpenseStore.kt`:
  - `ExpenseStore.kt` (interface and `InMemoryExpenseStore`)
  - `JpaExpenseStore.kt` (Spring `@Service` adapter)
  - `ExpenseMapper.kt` (mapping extensions)
- Split `JpaSynchronizationStore.kt`:
  - `SyncChangeEntity.kt`
  - `SyncSnapshotEntity.kt`
  - `SyncChangeRepository.kt`
  - `SyncSnapshotRepository.kt`
  - `JpaSynchronizationStore.kt` (Spring `@Service` adapter)
- Split `JpaOutboxStore.kt`:
  - `OutboxEntity.kt`
  - `OutboxRepository.kt`
  - `JpaOutboxStore.kt` (Spring `@Service` adapter)
- Preserve all table names, column mappings, Flyway database alignments, query annotations, and transactional boundaries.
- Provide structured KDoc comments on all classes, interfaces, and public methods.

## Dependencies

- `DOC-24`
- `CORE-26`

## Owned paths

- `app/expense-core/src/main/kotlin/com/subhrodip/squarewise/expensecore/expenses/`
- `app/expense-core/src/main/kotlin/com/subhrodip/squarewise/expensecore/sync/`
- `app/expense-core/src/main/kotlin/com/subhrodip/squarewise/expensecore/messaging/`
- `docs/tasks/details/CORE-27.md`

## Acceptance criteria

- Zero multi-responsibility files combining Entity, Repository, and Service in `expenses`, `sync`, and `messaging`.
- All tests in `:app:expense-core:test` pass cleanly without regression.
- Every class, interface, and public method includes structured KDoc documentation comments.
- `git diff --check` passes cleanly.

## Validation commands

- `./gradlew :app:expense-core:test --rerun-tasks --no-daemon`
- `git diff --check`

## Implementation notes & decisions

- Separated multi-responsibility files in `expenses`, `sync`, and `messaging` according to SOLID SRP and the repository's single-class-per-file standard:
  - `ExpenseEntities.kt` decomposed into `ExpenseEntity.kt`, `ExpensePayerEntity.kt`, `ExpenseAllocationEntity.kt`, `BalancePostingEntity.kt`, `ExpenseRepository.kt`, and `BalancePostingRepository.kt`.
  - `ExpenseStore.kt` split into `ExpenseStore.kt` (interface and `InMemoryExpenseStore`), `JpaExpenseStore.kt` (`@Service` adapter), and `ExpenseMapper.kt` (mapping extensions).
  - `JpaSynchronizationStore.kt` split into `SyncChangeEntity.kt`, `SyncChangeRepository.kt`, and `JpaSynchronizationStore.kt`. (Note: snapshots are dynamically computed from `sync_changes` per V4 schema; no separate table/entity exists).
  - `JpaOutboxStore.kt` split into `OutboxEntity.kt`, `OutboxRepository.kt`, and `JpaOutboxStore.kt`.
- Preserved all table names (`expenses`, `expense_payers`, `expense_allocations`, `balance_postings`, `sync_changes`, `expense_outbox`), column mappings, unique constraints, and Flyway database alignments.
- Maintained exact transactional boundaries, Spring bean annotations (`@Entity`, `@Table`, `@Repository`, `@Service`, `@Primary`, `@Transactional`), and query locking.
- Added comprehensive structured KDoc comments across all public and internal classes, interfaces, and methods.

## Evidence

- `./gradlew :app:expense-core:test --rerun-tasks --no-daemon` completed with `BUILD SUCCESSFUL in 22s` (100% tests passing).
- `git diff --check` passed cleanly with 0 whitespace errors or conflict markers.
