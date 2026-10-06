# ACC-05: Expose batch profile lookup REST endpoint in Accounts service

## Objective

Allow internal services and client aggregators to resolve multiple profiles in a single HTTP request by passing an array of account UUIDs.

## Acceptance criteria

- `POST /accounts/v1/profiles/batch`: Accepts `BatchProfileRequest(accountIds: List<UUID>)`.
- Returns `List<ProfileResponse>` containing existing profiles for the provided account IDs.
- Validates request body (non-null, bounded list up to 100 items).
- Implemented in `ProfileStore`, `InMemoryProfileStore`, and `JpaProfileStore`.
- Declared in `contracts/rest/accounts.openapi.json`.
- Unit and JPA tests in `ProfileControllerTest.kt` and `JpaRequestStoresTest.kt`.

## Owned paths

- `app/accounts/src/main/kotlin/com/subhrodip/squarewise/accounts/`
- `app/accounts/src/test/kotlin/com/subhrodip/squarewise/accounts/`
- `contracts/rest/accounts.openapi.json`

## Validation commands

- `./gradlew :app:accounts:test --no-daemon`
- `python3 tools/contracts/validate.py`
- `git diff --check`

## Implementation details

- **Request DTO (`ProfileController.kt`)**:
  - Added `data class BatchProfileRequest(@field:Size(min = 1, max = 100) val accountIds: List<UUID>)`.
- **ProfileStore & In-Memory / JPA Implementations**:
  - Added `fun findByIds(accountIds: List<UUID>): List<ProfileResponse>` to `ProfileStore`.
  - Implemented `findByIds(accountIds: List<UUID>)` in `InMemoryProfileStore` by filtering existing profiles whose `response.accountId` is present in `accountIds`.
  - Implemented `findByIds(accountIds: List<UUID>)` in `JpaProfileStore` by calling `repository.findAllById(accountIds).map { it.toResponse() }`.
- **REST Controller (`ProfileController.kt`)**:
  - Added `@PostMapping("/profiles/batch") fun getProfilesBatch(@Valid @RequestBody request: BatchProfileRequest): List<ProfileResponse> = profiles.findByIds(request.accountIds)`.
- **Contract Updates (`contracts/rest/accounts.openapi.json`)**:
  - Added path `/profiles/batch` with POST operation `getProfilesBatch`.
  - Added request body referencing schema `#/components/schemas/BatchProfileRequest`.
  - Added 200 response returning an array of `#/components/schemas/Profile` items and 400 Problem response.
  - Added `BatchProfileRequest` schema in `components.schemas` with `accountIds` array bounded between 1 and 100 UUID items.
- **Tests**:
  - In `ProfileControllerTest.kt`: Added tests verifying batch lookup with valid IDs, handling empty list with 400 validation error, handling invalid UUID format, and testing `InMemoryProfileStore.findByIds`.
  - In `JpaRequestStoresTest.kt`: Added test `finds profiles in batch by account ids` asserting batch retrieval using `JpaProfileStore.findByIds` with database persistence.

## Verification evidence

- `./gradlew :app:accounts:test --no-daemon`: All 27 tests passed with 0 failures.
- `python3 tools/contracts/validate.py`: All OpenAPI and schema contracts validated cleanly.
- `git diff --check`: Clean without whitespace or formatting issues.
