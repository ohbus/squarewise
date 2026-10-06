# NOT-08: Complete Notifications preferences contract schema and persistence validation

## Objective

Ensure `contracts/rest/notifications.openapi.json` fully declares request and response schemas for `/preferences` and query parameters for `/inbox`, backed by validated persistence tests.

## Acceptance criteria

- `contracts/rest/notifications.openapi.json` declares:
  - `NotificationPreferences` schema (`emailEnabled`, `pushEnabled`).
  - Request body and 200 response for `GET /preferences` and `PUT /preferences`.
  - Query parameters (`cursor`, `limit`) and response schema for `GET /inbox`.
- `PreferenceController` validates input and is covered by unit tests.
- Persistence and controller tests verified.

## Implementation details

- **Contract Schema Declarations (`contracts/rest/notifications.openapi.json`)**:
  - Declared `components.schemas.NotificationPreferences` with boolean properties `emailEnabled` and `pushEnabled`.
  - Configured `paths["/preferences"]`:
    - `get`: 200 response with schema `NotificationPreferences`, 401 response.
    - `put`: `requestBody` required with schema `NotificationPreferences`, 204 response, 401 response.
  - Configured `paths["/inbox"]`:
    - Query parameters `cursor` (string, optional) and `limit` (integer, optional, min 1, max 100, default 50).
    - 200 response schema declaring `items` array with `$ref` to `components.schemas.InboxItem` and `nextCursor` (string, optional).
  - Added schemas `InboxItem`, `InboxPage`, and `Problem` with standard `Unauthorized` response component.
- **Preference Controller & Error Handling (`PreferenceController.kt`)**:
  - Updated `PreferenceController` to support nullable `Principal?` in `get` and `update` methods.
  - Implemented unauthenticated request handling returning 401 Unauthorized via `ResponseStatusException(HttpStatus.UNAUTHORIZED, "authenticated subject is required")`, handled by `GlobalErrorHandler`.
- **Unit & Persistence Verification**:
  - Expanded `PreferenceControllerTest.kt` with tests covering:
    - Default preferences retrieval for authenticated subjects (`GET /preferences`).
    - Preferences update and retrieval with valid payload (`PUT /preferences` -> 204, then `GET /preferences` -> 200).
    - Unauthenticated rejection for `GET /preferences` (HTTP 401, `ErrorCode.UNAUTHENTICATED`).
    - Unauthenticated rejection for `PUT /preferences` (HTTP 401, `ErrorCode.UNAUTHENTICATED`).
    - Blank principal subject rejection for GET and PUT (HTTP 401, `ErrorCode.UNAUTHENTICATED`).
  - Verified `JpaPreferenceStoreTest.kt` for persistence defaults and updates.
  - Verified full test suite in `:app:notifications:test`.

## Owned paths

- `app/notifications/src/main/kotlin/com/subhrodip/squarewise/notifications/`
- `app/notifications/src/test/kotlin/com/subhrodip/squarewise/notifications/`
- `contracts/rest/notifications.openapi.json`
- `docs/tasks/details/NOT-08.md`

## Validation commands

- `./gradlew :app:notifications:test --no-daemon`
- `python3 tools/contracts/validate.py`
- `git diff --check`

## Verification evidence

- `./gradlew :app:notifications:test --no-daemon`: Passed cleanly (all unit and JPA persistence tests executed and passed).
- `python3 tools/contracts/validate.py`: Passed (all OpenAPI contracts validated as valid JSON, GraphQL syntax validated, task registry validated).
- `git diff --check`: Passed with zero warnings or whitespace errors.
