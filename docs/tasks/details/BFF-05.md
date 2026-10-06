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
- Added reactive sink `val invalidationSink: Sinks.Many<GroupInvalidation> = Sinks.many().multicast().directBestEffort()` with `emitInvalidation(groupId, revision, changeId)` and `invalidations(): Flux<GroupInvalidation>` in `LiveUpdateFanout`.
- Provided `@Bean fun liveUpdateFanout(): LiveUpdateFanout` in `BffApplication`.
- Implemented `@SubscriptionMapping fun groupChanged(@Argument groupId: String): Flux<GroupInvalidation>` in `GroupGraphqlController`, filtering published invalidations by target `groupId`.
- Added invalidation emissions in `createExpense` (passing `expense.version`) and `recordRepayment` (passing `1L`) on successful completion via `.doOnSuccess`.
- Added unit tests in `GroupGraphqlControllerTest` verifying:
  - Subscription stream emits matching events and discards unrelated groups.
  - `createExpense` mutation emits an invalidation with target groupId and version.
  - `recordRepayment` mutation emits an invalidation with target groupId.
- Added unit test in `LiveUpdateFanoutTest` verifying reactive invalidation emission.

## Verification evidence

- `./gradlew :app:bff:test --no-daemon`: Passed cleanly (BUILD SUCCESSFUL, 7 actionable tasks).
- `python3 tools/contracts/validate.py`: Passed cleanly (71 tasks valid, contracts valid).
- `git diff --check`: Passed cleanly with zero whitespace or formatting issues.

