# CORE-28: Refactor Expense Core recurring persistence into separated SOLID files

## Objective

Refactor multi-responsibility persistence files in Expense Core `recurring` into enterprise-ready, dedicated single-responsibility files adhering to SOLID principles:
- Split `RecurringEntities.kt`:
  - `RecurringExpenseSchedule.kt` (entity)
  - `RecurringExpenseOccurrence.kt` (entity)
  - `RecurringExpenseScheduleRepository.kt` (repository)
  - `RecurringExpenseOccurrenceRepository.kt` (repository)
- Retain existing table names, column mappings, Flyway database alignments, query annotations, and transactional boundaries.
- Provide structured KDoc comments on all classes, interfaces, and public methods.

## Dependencies

- `DOC-24`
- `CORE-26`

## Owned paths

- `app/expense-core/src/main/kotlin/com/subhrodip/squarewise/expensecore/recurring/`
- `docs/tasks/details/CORE-28.md`

## Acceptance criteria

- Zero multi-responsibility files combining multiple Entity and Repository classes in `recurring`.
- All tests in `:app:expense-core:test` pass cleanly without regression.
- Every class, interface, and public method includes structured KDoc documentation comments.
- `git diff --check` passes cleanly.

## Validation commands

- `./gradlew :app:expense-core:test --rerun-tasks --no-daemon`
- `git diff --check`

## Evidence

- `RecurringEntities.kt` decomposed into `RecurringExpenseSchedule.kt`, `RecurringExpenseOccurrence.kt`, `RecurringExpenseScheduleRepository.kt`, and `RecurringExpenseOccurrenceRepository.kt`.
- All classes, interfaces, and methods documented with structured KDoc comments.
- `./gradlew :app:expense-core:test --rerun-tasks --no-daemon` passed cleanly on 2026-09-18.
- `git diff --check` passed.
