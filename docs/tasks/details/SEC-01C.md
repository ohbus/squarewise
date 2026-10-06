# SEC-01C: Enforce object authorization and workload trust

## Status

Done. Depends on SEC-01A and SEC-01B.

## Objective

Resolve findings SEC-004 (profile object authorization) and SEC-005 (service-to-service
workload trust).

1. **Profile Object Authorization (SEC-004):**
   - Public profile access is restricted to the caller's own profile (`/me` and self-lookup on `GET /profiles/{accountId}`).
   - Cross-account lookups via `GET /profiles/{accountId}` and `POST /profiles/batch` require a dedicated internal workload identity (`X-Squarewise-Workload-Role: internal-service`).
   - Unauthorized reads fail with 403 Forbidden / `ERR_04` rather than disclosing profile DTOs to arbitrary authenticated subjects. Unauthenticated calls fail with 401 Unauthorized / `ERR_03`.
2. **Workload Trust Boundary (SEC-005):**
   - Internal service-to-service calls carry an explicit workload trust assertion (`ApiEndpoints.Headers.WORKLOAD_ROLE: WORKLOAD_ROLE_INTERNAL`).
   - Services do not rely on private Docker networking alone for profile disclosure.
   - OpenAPI contracts (`contracts/rest/accounts.openapi.json`) and public-surface test collections explicitly document bearer security, 401 Unauthorized, and 403 Forbidden problem responses.

## Owned Paths

- `libs/ids/src/main/kotlin/com/subhrodip/squarewise/ids/contracts/ApiEndpoints.kt`
- `app/accounts/src/main/kotlin/com/subhrodip/squarewise/accounts/profile/api/ProfileController.kt`
- `app/accounts/src/test/kotlin/com/subhrodip/squarewise/accounts/profile/ProfileControllerTest.kt`
- `contracts/rest/accounts.openapi.json`
- `tools/bruno/accounts/get-profile-by-id.bru`
- `tools/bruno/accounts/batch-get-profiles.bru`
- `docs/tasks/details/SEC-01C.md`

## Acceptance Criteria

1. `ProfileController` rejects attempts by User A to read User B's profile via `GET /profiles/{id}` with 403 Forbidden (`ERR_04`) unless authorized as a self-lookup (`callerProfile.accountId == accountId`) or internal workload identity.
2. `POST /profiles/batch` rejects foreign account IDs with 403 Forbidden when called by a user without workload authority, while permitting self-lookups or authorized workload queries.
3. Updated OpenAPI contracts reflect the secured profile endpoints (`security: [{"bearerAuth": []}]`), 401 Unauthorized, and 403 Forbidden responses.
4. Internal service calls authenticate with appropriate workload roles/credentials.
5. Unit and integration tests verify rejection of cross-account reads, anonymous calls, and untrusted workload requests.

## Implementation Notes

- Added `WORKLOAD_ROLE = "X-Squarewise-Workload-Role"` and `WORKLOAD_ROLE_INTERNAL = "internal-service"` constants to `ApiEndpoints.Headers`.
- Updated `ProfileController.getProfileById`:
  - Validates caller identity (`Principal`) and verifies profile existence.
  - Allows self-lookup if `callerProfile.accountId == accountId`.
  - Permits arbitrary ID lookup if `workloadRole == ApiEndpoints.Headers.WORKLOAD_ROLE_INTERNAL`.
  - Otherwise throws `ApplicationException(ErrorCode.ERR_04, "Access denied to foreign profile")`.
- Updated `ProfileController.getProfilesBatch`:
  - Permits arbitrary batch lookups if `workloadRole == ApiEndpoints.Headers.WORKLOAD_ROLE_INTERNAL`.
  - For user callers without workload authority, restricts batch lookup to the caller's own `accountId`; any requested foreign account ID throws `ApplicationException(ErrorCode.ERR_04, "Batch profile lookup requires internal workload authority")`.
- Contracts updated in `contracts/rest/accounts.openapi.json` with `Forbidden` RFC 7807 response schema, declared on `/profiles/{accountId}` and `/profiles/batch`.
- Bruno collections updated with `X-Squarewise-Workload-Role: internal-service` header.
- Unit and MockMvc test coverage added in `ProfileControllerTest.kt` verifying:
  - Self-lookup (`200 OK`)
  - Cross-user lookup (`403 FORBIDDEN`)
  - Unauthenticated lookup (`401 UNAUTHORIZED`)
  - Workload lookup of arbitrary account (`200 OK`)
  - Workload lookup of non-existent account (`404 NOT_FOUND`)
  - User self-batch lookup (`200 OK`)
  - User cross-account batch lookup (`403 FORBIDDEN`)
  - Unauthenticated batch lookup (`401 UNAUTHORIZED`)
  - Workload batch lookup with valid and non-existent IDs (`200 OK`)
  - Batch boundary validation (empty IDs, invalid UUIDs, >100 IDs limit, deduplication)

## Verification Commands and Evidence

- `./gradlew :app:accounts:test --rerun-tasks --no-daemon`: Passed (19 tasks executed, 0 failures).
- `./gradlew :app:bff:test --rerun-tasks --no-daemon`: Passed (18 tasks executed, 0 failures).
- `uv run python tools/contracts/validate.py`: Passed (all JSON contracts valid, 4 GraphQL declaration files, 207 task specs).
- `uv run python tools/contracts/validate_public_surface.py`: Passed (45 REST operations, 9 root GraphQL operations, 50 Bruno requests with 71 assertions).
- `uv run python tools/ops/check_security_hygiene.py`: Passed (1031 tracked files scanned, 0 secrets detected).
