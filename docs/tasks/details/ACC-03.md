# ACC-03: Expose GDPR export request REST endpoints in Accounts service

Implement REST endpoints for GDPR account data export requests in `app/accounts`.

## Acceptance criteria

- `POST /accounts/v1/me/export-request`: Initiates an export request for the authenticated user, returning HTTP 202 Accepted and `ExportRequestResponse(exportId, status, requestedAt)`.
- `GET /accounts/v1/me/export-requests`: Lists a bounded page of export requests for the authenticated user ordered by requested date descending (`page` defaults to 0 and `limit` defaults to 100, with a maximum limit of 100).
- Updates `contracts/rest/accounts.openapi.json` declaring the new endpoints and `ExportRequest` schema.
- Controller MockMvc unit tests cover successful export request creation, retrieval, and unauthorized access rejection.

## Implementation details

- **OpenAPI Updates (`contracts/rest/accounts.openapi.json`)**:
  - Added `/me/export-request` (POST, operationId: `requestExport`, 202 Accepted).
  - Added `/me/export-requests` (GET, operationId: `listExportRequests`, 200 OK) with bounded `page` and `limit` query parameters.
  - Added `ExportRequest` schema with uuid, status enum, and timestamp.
- **REST Endpoints (`ProfileController.kt`)**:
  - Injected `ExportRequestService`.
  - Implemented `requestExport(@AuthenticationPrincipal principal: Principal): ExportRequestResponse`.
  - Implemented `listExportRequests(@AuthenticationPrincipal principal: Principal, page: Int, limit: Int): List<ExportRequestResponse>` with database-side paging.
  - Enforced 401 Unauthorized for unauthenticated callers.
- **Tests (`ProfileControllerTest.kt`)**:
  - MockMvc unit tests for creation (202), listing (200), and 401 unauthenticated rejection.

## Verification evidence

- `python3 tools/contracts/validate.py`: passed.
- `./gradlew :app:accounts:test --no-daemon`: 16 tests passed.
- `git diff --check`: passed cleanly.
