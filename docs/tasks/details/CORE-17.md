# CORE-17: Expose group members endpoint and durable member listing in Expense Core

## Objective

Expose a REST endpoint to list members of a group to allow authenticated participants and client applications to discover all group members.

## Acceptance criteria

- `GET /expense-core/v1/groups/{groupId}/members`: Lists all members of the group as `List<GroupMemberResponse(membershipId, groupId, subject)>`.
- Access restricted to members of the group (returns 404/403 for non-members).
- Implemented in both `JpaGroupStore` and `InMemoryGroupStore`.
- Endpoint and schemas declared in `contracts/rest/expense-core.openapi.json`.
- Comprehensive unit tests in `GroupControllerTest.kt` and `JpaGroupStoreTest.kt`.

## Owned paths

- `app/expense-core/src/main/kotlin/com/subhrodip/squarewise/expensecore/groups/`
- `app/expense-core/src/test/kotlin/com/subhrodip/squarewise/expensecore/groups/`
- `contracts/rest/expense-core.openapi.json`

## Validation commands

- `./gradlew :app:expense-core:test --no-daemon`
- `python3 tools/contracts/validate.py`
- `git diff --check`

## Implementation details

- **Domain Model & Controller (`GroupController.kt`)**:
  - Added `GroupMemberResponse(val membershipId: UUID, val groupId: UUID, val subject: String)`.
  - Added `listMembers(groupId: UUID, subject: String): List<GroupMemberResponse>` to `GroupStore` interface.
  - Implemented `listMembers` in `InMemoryGroupStore`: tracks `memberships` map of `UUID` to `MutableList<GroupMemberResponse>` (using `CopyOnWriteArrayList`), verifies the caller belongs to the group (throwing 404 NOT_FOUND if not), and returns all member records for that group.
  - Added endpoint `GET /expense-core/v1/groups/{groupId}/members` in `GroupController` mapping to `groups.listMembers(groupId, principal.name)`.
- **Durable Persistence (`JpaGroupStore.kt`)**:
  - Added `findByGroupId(groupId: UUID): List<GroupMembershipEntity>` to `GroupMembershipRepository`.
  - Implemented `listMembers(groupId, subject)` in `JpaGroupStore`: verifies caller membership in `groupId` via `existsByGroupIdAndSubject` (throwing 404 NOT_FOUND if absent), queries `findByGroupId`, and maps entities to `GroupMemberResponse`.
- **Contract (`contracts/rest/expense-core.openapi.json`)**:
  - Added path `/groups/{groupId}/members` with `listGroupMembers` GET operation returning an array of `GroupMember`.
  - Added `GroupMember` schema to `components.schemas` with required fields `membershipId`, `groupId`, and `subject`.
- **Tests**:
  - `GroupControllerTest.kt`: Added tests verifying member listing for authenticated group members, 404 NOT_FOUND for non-member callers, and member listing reflection after invitation claim.
  - `JpaGroupStoreTest.kt`: Added test verifying `listMembers` on durable store for creator and newly claimed members, and rejecting non-member access with 404 NOT_FOUND.

## Verification evidence

- `./gradlew :app:expense-core:test --no-daemon`: 84 tests completed, 0 failures.
- `python3 tools/contracts/validate.py`: all contract schemas valid (valid JSON and valid task registry).
- `git diff --check`: passed cleanly with zero whitespace or format issues.
