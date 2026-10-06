# CORE-11: Durable synchronization changes

Persist group-scoped revisions, changes, and tombstones through JPA/Flyway while
preserving opaque cursor pagination and expiry semantics.

## Decisions and implementation notes

- Created Flyway migration `V4__create_sync_changes.sql` defining `sync_changes` with `change_id` (UUID primary key), `group_id`, `revision`, `entity_id`, `deleted` boolean, `payload` (TEXT), `created_at`, and a unique constraint on `(group_id, revision)`.
- Refactored `SynchronizationStore` into an interface with a companion `invoke()` factory to preserve backward compatibility for existing in-memory tests, and retained `InMemorySynchronizationStore`.
- Implemented `JpaSynchronizationStore` marked `@Primary` and `@Service`, using `SyncChangeRepository` with group-scoped revision generation (`findMaxRevision`), paginated ordered queries (`findChangesAfter`), and total remaining count (`countChangesAfter`).
- Preserved opaque base64 cursor encoding, group-scoped cursor isolation, and configurable expiry semantics.
- Fixed off-by-one race condition in `GroupControllerTest` member indexing.
- Added comprehensive integration tests in `JpaSynchronizationStoreTest` verifying sequential revisions, deletion tombstones, multi-page cursor pagination, and cross-group cursor rejection.
