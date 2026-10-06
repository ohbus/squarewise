# CORE-18: Add group rename and update REST endpoint in Expense Core

## Objective

Allow group members to rename a group and update its metadata with revision
increments. Transactional audit, sync, outbox, and concurrent-write hardening
are required before this task can be accepted as complete.

## Acceptance criteria

- `PATCH /expense-core/v1/groups/{groupId}`: accepts `UpdateGroupRequest(name: String)`.
- Rejects requests from non-members with 404/403.
- Increments `revision` on the group entity upon successful update.
- Implemented in both `JpaGroupStore` and `InMemoryGroupStore`.
- Endpoint and schema declared in `contracts/rest/expense-core.openapi.json`.
- Comprehensive unit and persistence tests in `GroupControllerTest.kt` and `JpaGroupStoreTest.kt`.

## Owned paths

- `app/expense-core/src/main/kotlin/com/subhrodip/squarewise/expensecore/groups/`
- `app/expense-core/src/test/kotlin/com/subhrodip/squarewise/expensecore/groups/`
- `contracts/rest/expense-core.openapi.json`

## Validation commands

- `./gradlew :app:expense-core:test --no-daemon`
- `python3 tools/contracts/validate.py`
- `git diff --check`

## Implementation details

- **Request DTO**: Added `data class UpdateGroupRequest(@field:NotBlank @field:Size(max = 120) val name: String)` in `GroupController.kt`.
- **GroupStore interface**: Added `fun update(groupId: UUID, subject: String, request: UpdateGroupRequest): GroupResponse`.
- **InMemoryGroupStore**: Checks membership, trims name, increments revision atomically.
- **JpaGroupStore**: `@Transactional` method verifying membership via `existsByGroupIdAndSubject`, updates entity name (trimmed) and increments revision, then saves.
- **Controller**: `@PatchMapping("/{groupId}") fun update(...)` delegates to `GroupStore.update`.
- **Contract**: Added `patch` operation on `/groups/{groupId}` path with `UpdateGroup` request schema and 200/404 responses. Added `UpdateGroup` schema in components.
- **Tests**:
  - `GroupControllerTest`: PATCH rename with revision check, non-member 404, InMemoryGroupStore trim/revision unit test.
  - `JpaGroupStoreTest`: JPA rename with trim, revision increment, and non-member rejection.

## Verification evidence

- `./gradlew :app:expense-core:test --no-daemon`: All tests passed.
- `python3 tools/contracts/validate.py`: All contracts validated cleanly.
- `git diff --check`: Clean.

## Known limitations

- The current increment does not append an audit record, synchronization change,
  or outbox event.
- The JPA update has no optimistic version check or explicit lock, so concurrent
  rename semantics are not yet verified.
