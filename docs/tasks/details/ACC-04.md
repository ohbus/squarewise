# ACC-04: Expose public profile lookup endpoint by account ID in Accounts service

## Objective

Allow client applications and internal services to look up public profile information (displayName, timezone, defaultCurrency) for any registered account by its `accountId`.

## Acceptance criteria

- `GET /accounts/v1/profiles/{accountId}`: Returns `ProfileResponse(accountId, displayName, timezone, defaultCurrency)`.
- Returns 404 when `accountId` does not exist.
- Implemented in `ProfileStore`, `JpaProfileStore`, and `InMemoryProfileStore`.
- Endpoint and schema declared in `contracts/rest/accounts.openapi.json`.
- Controller and store unit tests in `ProfileControllerTest.kt`.

## Owned paths

- `app/accounts/src/main/kotlin/com/subhrodip/squarewise/accounts/`
- `app/accounts/src/test/kotlin/com/subhrodip/squarewise/accounts/`
- `contracts/rest/accounts.openapi.json`

## Validation commands

- `./gradlew :app:accounts:test --no-daemon`
- `python3 tools/contracts/validate.py`
- `git diff --check`

## Implementation details

- **ProfileStore & In-Memory / JPA Implementations**:
  - Added `fun findById(accountId: UUID): ProfileResponse?` to `ProfileStore`.
  - Implemented `findById(accountId: UUID)` in `InMemoryProfileStore` by searching stored profiles for matching `response.accountId`.
  - Implemented `findById(accountId: UUID)` in `JpaProfileStore` by finding `ProfileEntity` via `repository.findById(accountId)`.
- **REST Endpoints (`ProfileController.kt`)**:
  - Restructured base mapping to `@RequestMapping("/accounts/v1")` with individual paths (`/me`, `/me/deletion-request`, `/me/export-request`, `/me/export-requests`).
  - Added public lookup endpoint `@GetMapping("/profiles/{accountId}") fun getProfileById(@PathVariable accountId: UUID): ProfileResponse`.
  - Returns 404 with `ResponseStatusException(HttpStatus.NOT_FOUND, "Profile not found")` when absent.
- **Contract Updates (`contracts/rest/accounts.openapi.json`)**:
  - Declared path `/profiles/{accountId}` with GET operation `getProfileById`, 200 response returning `Profile` schema, and 404 Problem response.
- **Tests**:
  - In `ProfileControllerTest.kt`: Added unit tests for successful 200 lookup of public profile by account ID, 404 Not Found when ID is absent, and `InMemoryProfileStore.findById`.
  - In `JpaRequestStoresTest.kt`: Added unit test for `JpaProfileStore.findById` verifying entity retrieval and null response on non-existent account ID.

## Verification evidence

- `python3 tools/contracts/validate.py`: All JSON and OpenAPI contracts validated cleanly.
- `./gradlew :app:accounts:test --no-daemon`: 22 tests passed (including all ProfileController, JpaProfileStore, and InMemoryProfileStore tests).
- `git diff --check`: Clean without whitespace or formatting issues.
