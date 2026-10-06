# NOT-09: Add mark inbox notification as read endpoint in Notifications service

## Objective

Allow users to mark specific inbox notifications as read in the Notifications service.

## Acceptance criteria

- `POST /notifications/v1/inbox/{notificationId}/read`: Marks the notification with `notificationId` as read (`read = true`).
- Returns HTTP 204 No Content upon success; returns 404 when notification is not found for the authenticated user.
- Implemented in `NotificationInboxStore`, `InMemoryNotificationInboxStore`, and `JpaNotificationInboxStore`.
- Declared in `contracts/rest/notifications.openapi.json`.
- Unit and persistence tests in `InboxControllerTest.kt` and `JpaNotificationInboxStoreTest.kt`.

## Owned paths

- `app/notifications/src/main/kotlin/com/subhrodip/squarewise/notifications/`
- `app/notifications/src/test/kotlin/com/subhrodip/squarewise/notifications/`
- `contracts/rest/notifications.openapi.json`

## Validation commands

- `./gradlew :app:notifications:test --no-daemon`
- `python3 tools/contracts/validate.py`
- `git diff --check`

## Implementation details

- **NotificationInboxStore interface**: Added `fun markAsRead(subject: String, notificationId: UUID): Boolean`.
- **InMemoryNotificationInboxStore**: Synchronized list mutation, finds item by notificationId and copies with `read = true`. Returns false if not found or wrong subject.
- **JpaNotificationInboxStore**: `@Transactional` method using `repository.findBySubjectAndNotificationId`, sets `read = true` and saves. Returns false if entity not found.
- **InboxController**: Added `@PostMapping("/{notificationId}/read") @ResponseStatus(HttpStatus.NO_CONTENT) fun markAsRead(...)` that throws 404 if store returns false.
- **NotificationInbox service**: Added `markAsRead` passthrough.
- **Contract**: Added `/inbox/{notificationId}/read` POST with 204/401/404 responses and `NotificationId` path parameter component.
- **Tests**:
  - `InboxControllerTest`: MockMvc tests for 204 on success, 404 for unknown, 404 for wrong user, InMemoryStore unit test.
  - `JpaNotificationInboxStoreTest`: JPA test verifying mark-as-read with wrong subject, unknown ID, correct subject, and entity persistence.

## Verification evidence

- `./gradlew :app:notifications:test --no-daemon`: All tests passed.
- `python3 tools/contracts/validate.py`: All contracts validated cleanly.
- `git diff --check`: Clean.
