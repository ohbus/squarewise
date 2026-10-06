# NOT-10: Refactor Notifications persistence into separated SOLID files

## Objective

Refactor multi-responsibility persistence files in the Notifications service into enterprise-ready, separated files following the Single Responsibility Principle (SRP):
- Separate `NotificationInboxEntity` and `ProcessedNotificationEventEntity` into individual entity files.
- Separate `NotificationInboxRepository` and `ProcessedNotificationEventRepository` into individual repository files.
- Separate `NotificationPreferenceEntity` and `NotificationPreferenceRepository` from `JpaPreferenceStore.kt`.
- Keep `JpaNotificationInboxStore` and `JpaPreferenceStore` focused strictly on service/store adapter logic.
- Ensure structured KDoc comments on all classes, interfaces, and methods.

## Dependencies

- `DOC-24`

## Owned paths

- `app/notifications/src/main/kotlin/com/subhrodip/squarewise/notifications/`
- `docs/tasks/details/NOT-10.md`

## Acceptance criteria

- Zero files combining entities, repositories, and services/stores in the Notifications service.
- All tests in `:app:notifications:test` pass cleanly without regression.
- Every class, interface, and public method includes structured KDoc documentation comments.
- `git diff --check` passes cleanly.

## Validation commands

- `./gradlew :app:notifications:test --rerun-tasks --no-daemon`
- `git diff --check`

## Evidence

- Refactored `JpaNotificationInboxStore.kt` and `JpaPreferenceStore.kt` into dedicated SRP files:
  * `NotificationInboxEntity.kt`: Dedicated JPA entity for `notification_inbox_items` with KDoc.
  * `ProcessedNotificationEventEntity.kt`: Dedicated JPA entity for `notification_processed_events` with KDoc.
  * `NotificationInboxRepository.kt`: Dedicated Spring Data JPA repository interface with KDoc.
  * `ProcessedNotificationEventRepository.kt`: Dedicated Spring Data JPA repository interface with KDoc.
  * `JpaEventDeduplicator.kt`: Dedicated service implementation for event deduplication with KDoc.
  * `JpaNotificationInboxStore.kt`: Retained exclusively for store adapter logic and entity mapping with KDoc.
  * `NotificationPreferenceEntity.kt`: Dedicated JPA entity for `notification_preferences` with KDoc.
  * `NotificationPreferenceRepository.kt`: Dedicated Spring Data JPA repository interface with KDoc.
  * `JpaPreferenceStore.kt`: Retained exclusively for preference store adapter logic and mapping with KDoc.
- `./gradlew :app:notifications:test --rerun-tasks --no-daemon`: Passed (9 executed, 0 failed, 100% clean).
- `git diff --check`: Passed cleanly with no trailing whitespace or conflicts.
