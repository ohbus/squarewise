# BFF-05: Implement groupChanged GraphQL subscription with reactive live update sink

## Objective

Connect the GraphQL subscription `groupChanged(groupId: ID!): GroupInvalidation!` in BFF to the live update mechanism so clients receive real-time group change notifications.

## Acceptance criteria

- `GroupGraphqlController` implements `@SubscriptionMapping fun groupChanged(@Argument groupId: String): Flux<GroupInvalidation>`.
- Emits invalidation events (`groupId`, `revision`, `changeId`) when updates are published for that group.
- Mutations (`createExpense`, `recordRepayment`) emit invalidation events upon successful execution.
- Unit tests verify subscription emission and filtering by `groupId`.

## Owned paths

- `app/bff/src/main/kotlin/com/subhrodip/squarewise/bff/`
- `app/bff/src/test/kotlin/com/subhrodip/squarewise/bff/`

## Validation commands

- `./gradlew :app:bff:test --no-daemon`
- `python3 tools/contracts/validate.py`
- `git diff --check`

## Implementation notes

- Defined `GroupInvalidation(val groupId: String, val revision: Long, val changeId: String)` with default UUID generation for change tracking.
- Added a bounded replay sink in `LiveUpdateFanout` so events emitted during the authorized snapshot cannot be missed.
- Provided `@Bean fun liveUpdateFanout(): LiveUpdateFanout` in `BffApplication`.
- Implemented `@SubscriptionMapping fun groupChanged(@Argument groupId: String): Flux<GroupInvalidation>` in `GroupGraphqlController`, filtering published invalidations by target `groupId`.
- Expense and repayment mutations rely on committed outbox events; they no longer fabricate an expense version or constant revision in the BFF.
- Group subscriptions attach to the replay stream before the snapshot request completes.
- Added unit tests in `GroupGraphqlControllerTest` verifying:
  - Subscription stream emits matching events and discards unrelated groups.
  - Expense and repayment mutations do not publish fabricated revisions before the committed outbox event arrives.
  - `recordRepayment` mutation emits an invalidation with target groupId.
- Added unit test in `LiveUpdateFanoutTest` verifying reactive invalidation emission.

## Verification evidence

- `./gradlew :app:bff:test --no-daemon`: Passed cleanly (BUILD SUCCESSFUL, 7 actionable tasks).
- `python3 tools/contracts/validate.py`: Passed cleanly (71 tasks valid, contracts valid).
- `git diff --check`: Passed cleanly with zero whitespace or formatting issues.

## 2026-10-11 audit remediation

The BFF now bounds group listing at 100 groups, loads group collections only when
selected by GraphQL, and keeps the existing 1,000-expense look-ahead cap. The
look-ahead remains a deliberate compatibility shortcut until the GraphQL group
contract exposes a cursor connection.

