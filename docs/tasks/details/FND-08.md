# FND-08: Refactor domain ports, in-memory stores, and consumer services into dedicated files

## Objective

Refactor remaining multi-class and port files across the codebase to adhere to clean enterprise design:
1. `app/accounts/src/main/kotlin/com/subhrodip/squarewise/accounts/ProfileStore.kt`:
   - Extract `ProfileStore` interface, `StoredProfile` data class, and `InMemoryProfileStore` from `ProfileController.kt` into dedicated `ProfileStore.kt` and `InMemoryProfileStore.kt`.
   - Update `ProfileController` to inject `DeletionRequestService` and `ExportRequestService` via Spring constructor injection without inline instantations.
2. `app/expense-core/src/main/kotlin/com/subhrodip/squarewise/expensecore/groups/`:
   - Extract `GroupStore` interface and `InMemoryGroupStore` from `GroupController.kt` into dedicated `GroupStore.kt` and `InMemoryGroupStore.kt`.
3. `app/expense-core/src/main/kotlin/com/subhrodip/squarewise/expensecore/settlements/`:
   - Extract `Settlement` domain model, `SettlementStatus` enum, `SettlementStore` interface, and `InMemorySettlementStore` from `SettlementService.kt` into dedicated `Settlement.kt`, `SettlementStore.kt`, and `InMemorySettlementStore.kt`.
4. `app/notifications/src/main/kotlin/com/subhrodip/squarewise/notifications/`:
   - Extract `TransactionalNotificationEventProcessor` from `NotificationEventConsumer.kt` into `TransactionalNotificationEventProcessor.kt`.
5. Retain 100% existing test passing rate, API compatibility, and provide structured KDoc comments.

## Dependencies

- `DOC-24`
- `CORE-28`

## Owned paths

- `app/accounts/src/main/kotlin/com/subhrodip/squarewise/accounts/`
- `app/expense-core/src/main/kotlin/com/subhrodip/squarewise/expensecore/groups/`
- `app/expense-core/src/main/kotlin/com/subhrodip/squarewise/expensecore/settlements/`
- `app/notifications/src/main/kotlin/com/subhrodip/squarewise/notifications/`
- `docs/tasks/details/FND-08.md`

## Acceptance criteria

- `ProfileStore` and `InMemoryProfileStore` reside in dedicated files; `ProfileController` contains only controller endpoints and DTOs.
- `GroupStore` and `InMemoryGroupStore` reside in dedicated files; `GroupController` contains only controller endpoints and DTOs.
- `SettlementStore` and `InMemorySettlementStore` reside in dedicated files; `SettlementService` contains only service coordination logic.
- `TransactionalNotificationEventProcessor` resides in a dedicated file.
- All tests pass (`./gradlew test --rerun-tasks --no-daemon`).
- `git diff --check` passes cleanly.

## Validation commands

- `./gradlew test --rerun-tasks --no-daemon`
- `git diff --check`

## Evidence

- Recorded in `docs/tasks/progress.md`.
