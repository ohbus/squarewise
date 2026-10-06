# CORE-20: Expose recurring schedule management and pause notifications

Add authenticated recurring-schedule create/list/update/pause/resume transport,
bounded worker catch-up behavior, and visible notification when invalid membership
pauses generation. Depends on CORE-15 and NOT-07. Owns recurrence controllers,
tests, related contracts, and required event schemas.

## Deliverables & Decisions

1. **REST Contracts & ApiEndpoints**:
   - Centralized schedule endpoint path constants under `ApiEndpoints.ExpenseCore.V1` in `libs/ids` (`/groups/{groupId}/schedules`, `.../{scheduleId}`, `.../pause`, `.../resume`).
   - Defined REST paths and schemas (`CreateRecurringSchedule`, `UpdateRecurringSchedule`, `RecurringSchedule`) in `contracts/rest/expense-core.openapi.json`.
   - Verified via `python3 tools/contracts/validate.py`.

2. **Transport Controller & DTOs (`RecurringExpenseController.kt`, `RecurringScheduleDtos.kt`)**:
   - `CreateRecurringScheduleRequestDto`, `UpdateRecurringScheduleRequestDto`, `RecurringScheduleResponse`.
   - Mapped endpoints:
     - `POST /groups/{groupId}/schedules` (create recurring schedule)
     - `GET /groups/{groupId}/schedules` (list group recurring schedules)
     - `GET /groups/{groupId}/schedules/{scheduleId}` (get recurring schedule)
     - `PUT /groups/{groupId}/schedules/{scheduleId}` (update recurring schedule)
     - `POST /groups/{groupId}/schedules/{scheduleId}/pause` (pause recurring schedule)
     - `POST /groups/{groupId}/schedules/{scheduleId}/resume` (resume recurring schedule)
   - Validates group existence and user membership authorization (`membershipRepository.existsByGroupIdAndSubject`), throwing `404 Not Found` for unauthorized or non-existent resources.

3. **Domain Logic & Service Extensions (`RecurringExpenseService.kt`)**:
   - Added `updateSchedule(groupId, scheduleId, request)` for updating schedule parameters and custom payers/allocations.
   - Enforced bounded worker catch-up behavior (`maxCatchUpOccurrences: Int = 12`) during `processDueOccurrences` to prevent unbounded execution loops when catching up overdue schedules.
   - Implemented active group membership validation before generating due occurrences. If members leave or custom allocations reference invalid members, schedule generation automatically pauses (`schedule.paused = true`) and appends an outbox message (`eventType = "recurring.schedule.paused"`, `reason = "invalid_membership"`).

4. **Scheduled Background Runner (`RecurringExpenseWorker.kt`)**:
   - Injected `@Value("\${squarewise.recurring.max-catch-up-occurrences:12}") var maxCatchUpOccurrences: Int = 12`.
   - Passes limit to `service.processDueOccurrences(LocalDate.now(), maxCatchUpOccurrences)` on each poll tick.

5. **Unit & Integration Tests**:
   - `RecurringExpenseServiceTest`: Unit & integration tests for schedule update, bounded catch-up processing, and invalid membership pause notification outbox emission.
   - `RecurringExpenseControllerTest`: Controller integration tests verifying schedule creation, listing, inspection, update, pause, resume, and 404 handling.

## Owned Paths

- `app/expense-core/src/main/kotlin/com/subhrodip/squarewise/expensecore/recurring/`
- `app/expense-core/src/test/kotlin/com/subhrodip/squarewise/expensecore/recurring/`
- `contracts/rest/expense-core.openapi.json`
- `libs/ids/src/main/kotlin/com/subhrodip/squarewise/ids/ApiEndpoints.kt`
- `docs/tasks/details/CORE-20.md`

## Verification Evidence

- `python3 tools/contracts/validate.py`: All 6 JSON contracts, GraphQL declarations, and task registry valid.
- `./gradlew :app:expense-core:test --tests "com.subhrodip.squarewise.expensecore.recurring.*" --no-daemon`: BUILD SUCCESSFUL.
- `./gradlew :app:notifications:test --no-daemon`: BUILD SUCCESSFUL (38 tests passed).
