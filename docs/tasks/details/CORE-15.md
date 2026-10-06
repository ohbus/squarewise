# CORE-15: Durable recurring expense schedules, occurrences, and runner

Implement database-backed recurring expense scheduling, frozen occurrence uniqueness, and worker runner in `app/expense-core`.

## Acceptance criteria

- Flyway migration `V7` creates `recurring_expense_schedules` and `recurring_expense_occurrences` tables.
- `RecurringExpenseService` supports schedule creation, pause, resume, and due occurrence execution.
- Occurrence generation uses `RecurrencePolicy` and `OccurrenceIdentity` with idempotency to avoid duplicate expenses.
- Scheduled runner `RecurringExpenseWorker` periodically checks for due schedules and executes them atomically.
- All operations maintain group isolation and immutable financial audit rules.

## Implementation details

- **Flyway Migration `V7__add_recurring_expenses.sql`**:
  - `recurring_expense_schedules`: stores recurring configurations with `next_occurrence_date`, `frequency` (WEEKLY/MONTHLY), `day_of_month`, `paused`, `version`.
  - `recurring_expense_occurrences`: stores historical occurrence executions with unique constraint `(schedule_id, occurrence_date)` and foreign key to `expenses(expense_id)`.
- **Entities & Repositories (`RecurringEntities.kt`)**:
  - `RecurringExpenseSchedule` and `RecurringExpenseOccurrence` mapped with Spring Data JPA repositories.
  - Queries for unpaused due schedules (`findDueSchedules(asOfDate)`).
- **Service (`RecurringExpenseService.kt`)**:
  - `createSchedule(groupId, request)`
  - `pauseSchedule(scheduleId)` / `resumeSchedule(scheduleId)`
  - `processDueOccurrences(asOfDate)`: Generates deterministic occurrence ID via `OccurrenceIdentity.id()`, verifies idempotency, creates expense via `ExpenseStore.create()` (equal split across members or custom allocations), advances `schedule.nextOccurrenceDate` using `RecurrencePolicy.nextAfter()`, and saves occurrence record atomically.
- **Worker Runner (`RecurringExpenseWorker.kt`)**:
  - Scheduled background runner polling due schedules with configurable delay.
- **Tests**:
  - `RecurringExpenseServiceTest`: unit and integration tests verifying creation, pause/resume, due occurrence processing, equal splitting, and duplicate avoidance.
  - `RecurringExpenseWorkerTest`: verifies worker triggers due occurrence execution.

## Verification evidence

- `./gradlew :app:expense-core:test --no-daemon`: 79 tests completed, 0 failures.
- `git diff --check`: passed cleanly.
