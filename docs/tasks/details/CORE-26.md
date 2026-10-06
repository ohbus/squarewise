# CORE-26: Refactor Expense Core groups and settlements persistence into separated SOLID files

## Objective

Refactor multi-responsibility persistence files in Expense Core groups and settlements into enterprise-ready, separated files following the Single Responsibility Principle (SRP):
- Separate `GroupEntity`, `GroupMembershipEntity`, `GroupInvitationEntity`, `GroupAuditEntity` into individual entity files.
- Separate `GroupRepository`, `GroupMembershipRepository`, `GroupInvitationRepository`, `GroupAuditRepository` into individual repository files.
- Separate `JpaGroupStore` into its own adapter file.
- Separate `SettlementEntity` and `SettlementRepository` into individual files, distinct from `JpaSettlementStore`.
- Maintain all table/column names, JPA queries, Spring bean contracts, and structured KDoc comments.

## Dependencies

- `DOC-24`

## Owned paths

- `app/expense-core/src/main/kotlin/com/subhrodip/squarewise/expensecore/groups/`
- `app/expense-core/src/main/kotlin/com/subhrodip/squarewise/expensecore/settlements/`
- `docs/tasks/details/CORE-26.md`

## Implementation Notes

- Extracted `GroupEntity` into [`GroupEntity.kt`](../../../app/expense-core/src/main/kotlin/com/subhrodip/squarewise/expensecore/groups/GroupEntity.kt).
- Extracted `GroupMembershipEntity` into [`GroupMembershipEntity.kt`](../../../app/expense-core/src/main/kotlin/com/subhrodip/squarewise/expensecore/groups/GroupMembershipEntity.kt).
- Extracted `GroupInvitationEntity` into [`GroupInvitationEntity.kt`](../../../app/expense-core/src/main/kotlin/com/subhrodip/squarewise/expensecore/groups/GroupInvitationEntity.kt).
- Extracted `GroupAuditEntity` into [`GroupAuditEntity.kt`](../../../app/expense-core/src/main/kotlin/com/subhrodip/squarewise/expensecore/groups/GroupAuditEntity.kt).
- Extracted `GroupRepository` into [`GroupRepository.kt`](../../../app/expense-core/src/main/kotlin/com/subhrodip/squarewise/expensecore/groups/GroupRepository.kt).
- Extracted `GroupMembershipRepository` into [`GroupMembershipRepository.kt`](../../../app/expense-core/src/main/kotlin/com/subhrodip/squarewise/expensecore/groups/GroupMembershipRepository.kt).
- Extracted `GroupInvitationRepository` into [`GroupInvitationRepository.kt`](../../../app/expense-core/src/main/kotlin/com/subhrodip/squarewise/expensecore/groups/GroupInvitationRepository.kt).
- Extracted `GroupAuditRepository` into [`GroupAuditRepository.kt`](../../../app/expense-core/src/main/kotlin/com/subhrodip/squarewise/expensecore/groups/GroupAuditRepository.kt).
- Refactored [`JpaGroupStore.kt`](../../../app/expense-core/src/main/kotlin/com/subhrodip/squarewise/expensecore/groups/JpaGroupStore.kt) to contain strictly `JpaGroupStore` and its private response mapping extension. Removed obsolete `GroupAudit.kt`.
- Extracted `SettlementEntity` into [`SettlementEntity.kt`](../../../app/expense-core/src/main/kotlin/com/subhrodip/squarewise/expensecore/settlements/SettlementEntity.kt).
- Extracted `SettlementRepository` into [`SettlementRepository.kt`](../../../app/expense-core/src/main/kotlin/com/subhrodip/squarewise/expensecore/settlements/SettlementRepository.kt).
- Refactored [`JpaSettlementStore.kt`](../../../app/expense-core/src/main/kotlin/com/subhrodip/squarewise/expensecore/settlements/JpaSettlementStore.kt) to contain strictly `JpaSettlementStore` and its private entity/domain mapping functions.
- Added comprehensive structured KDoc comments on all classes, interfaces, and methods documenting intent, parameters, return values, invariants, and edge cases.

## Acceptance criteria

- Zero files combining entities, Spring Data repositories, and services/stores in groups and settlements.
- All existing tests in `:app:expense-core:test` pass without regression.
- Every new and touched class has structured KDoc documentation comments.
- `git diff --check` and build checks pass cleanly.

## Validation commands

- `./gradlew :app:expense-core:test --rerun-tasks --no-daemon`
- `git diff --check`

## Evidence

- Verified `./gradlew :app:expense-core:test --rerun-tasks --no-daemon` passed with 9 actionable tasks executed successfully.
- Verified `git diff --check` cleanly with 0 issues.
- Recorded in `docs/tasks/progress.md`.
