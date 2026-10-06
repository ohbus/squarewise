# ACC-06: Refactor Accounts persistence into separated SOLID files

## Objective

Refactor multi-responsibility persistence files in the Accounts service into enterprise-ready, separated files adhering to the Single Responsibility Principle (SRP):
- Separate `AccountDeletionRequestEntity` and `AccountExportRequestEntity` into individual entity files.
- Separate `DeletionRequestRepository` and `ExportRequestRepository` into individual repository files.
- Separate `ProfileEntity` and `ProfileRepository` from `JpaProfileStore.kt`.
- Separate domain stores/in-memory implementations and JPA store adapters (`JpaDeletionRequestStore`, `JpaExportRequestStore`, `InMemoryDeletionRequestStore`, `InMemoryExportRequestStore`).
- Ensure structured KDoc comments on all classes, interfaces, and methods.

## Dependencies

- `DOC-24`

## Owned paths

- `app/accounts/src/main/kotlin/com/subhrodip/squarewise/accounts/`
- `docs/tasks/details/ACC-06.md`

## Acceptance criteria

- Zero files combining entities, repositories, and services/stores in the Accounts service.
- All tests in `:app:accounts:test` pass cleanly without regression.
- Every class, interface, and public method includes structured KDoc documentation comments.
- `git diff --check` passes cleanly.

## Validation commands

- `./gradlew :app:accounts:test --rerun-tasks --no-daemon`
- `git diff --check`

## Evidence

- `git diff --check -- app/accounts`: Clean (0 errors).
- `./gradlew :app:accounts:test --rerun-tasks --no-daemon`: 100% tests passed (BUILD SUCCESSFUL).
- All multi-responsibility files in Accounts service refactored into separated single-responsibility files:
  - `ProfileEntity.kt` (JPA entity)
  - `ProfileRepository.kt` (Spring Data JPA repository)
  - `JpaProfileStore.kt` (Persistence store implementation)
  - `AccountDeletionRequestEntity.kt` (JPA entity)
  - `AccountExportRequestEntity.kt` (JPA entity)
  - `DeletionRequestRepository.kt` (Spring Data JPA repository)
  - `ExportRequestRepository.kt` (Spring Data JPA repository)
  - `DeletionRequestStore.kt` (Domain store interface and in-memory implementation)
  - `ExportRequestStore.kt` (Domain store interface and in-memory implementation)
  - `JpaDeletionRequestStore.kt` (JPA store adapter)
  - `JpaExportRequestStore.kt` (JPA store adapter)
  - `AccountRequestPersistence.kt` (Deleted)
- Structured KDoc added to all created and modified classes, interfaces, and methods.
