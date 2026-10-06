# ACC-02: Durable account deletion and export request persistence

Persist account deletion and GDPR export request states through JPA/Flyway adapters in `app/accounts`.

## Acceptance criteria

- Deletion request and export request lifecycles persist to PostgreSQL via JPA entities and Flyway migration.
- Requests remain idempotent and preserve attributable historical financial references upon deletion request.

## Implementation details

- **Flyway Migration `V2__create_deletion_and_export_requests.sql`**:
  - `account_deletion_requests`: Primary key `subject VARCHAR(200)` with `status` (`REQUESTED`, `CANCELLED`, `COMPLETED`), timestamp `requested_at`, and status check constraint.
  - `account_export_requests`: Primary key `export_id UUID`, `subject VARCHAR(200)`, `status` (`REQUESTED`, `READY`, `EXPIRED`), timestamp `requested_at`, status check constraint, and composite index on `(subject, requested_at DESC)`.
- **JPA Entities & Repositories (`AccountRequestPersistence.kt`)**:
  - `AccountDeletionRequestEntity` and `DeletionRequestRepository`.
  - `AccountExportRequestEntity` and `ExportRequestRepository` with `findBySubjectOrderByRequestedAtDesc(subject)`.
- **Stores & Idempotency**:
  - `DeletionRequestStore` with `InMemoryDeletionRequestStore` and `JpaDeletionRequestStore` (`@Service @Primary`).
  - `JpaDeletionRequestStore.request(subject)` is idempotent: if a deletion request already exists for the subject, the existing record is returned without modification or duplicate insertion.
  - Preserves attributable historical financial references: upon deletion request, the user's `account_profiles` record is preserved with `deletion_requested = true`, maintaining consistent `account_id` and `subject` references in historical group ledgers and expense postings.
  - `ExportRequestStore` with `InMemoryExportRequestStore` and `JpaExportRequestStore` (`@Service @Primary`).
  - `JpaExportRequestStore.request(subject)` creates a durable record with a UUIDv7 generated identifier.
- **Service Integration & Controller Wiring**:
  - `DeletionRequestService` and `ExportRequestService` inject their respective durable stores via `@Autowired` primary constructor while preserving backward-compatible secondary constructors for unit testing.
  - `ProfileController` wires `DeletionRequestService` to record durable deletion requests upon `POST /accounts/v1/me/deletion-request`.
- **Schema Alignment & Test Runtime**:
  - Aligned `ProfileEntity.defaultCurrency` column mapping to `columnDefinition = "CHAR(3)"` to match Flyway migration `V1__create_account_profiles.sql`.
  - Added `runtimeOnly(libs.boot.flyway)` and `testRuntimeOnly(libs.h2)` to `app/accounts/build.gradle.kts` for automated test execution against embedded databases.

## Verification evidence

- `./gradlew :app:accounts:test --no-daemon`: All 12 unit and integration tests passed, including `JpaRequestStoresTest`.
- `./gradlew test --no-daemon --rerun`: 30 Gradle test tasks passed across all subprojects.
- `python3 tools/contracts/validate.py`: All OpenAPI, JSON schema, and GraphQL contracts validated.
- `git diff --check`: Clean diff without trailing whitespace or formatting conflicts.
