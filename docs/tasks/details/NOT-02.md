# NOT-02: Durable Notifications persistence

Move notification state behind a service-local JPA adapter and Flyway migration
without changing existing REST behavior or introducing cross-service storage.

Notification preferences now use a service-local JPA adapter with optimistic
versioning and a Flyway-owned schema. The in-memory adapter remains available
for focused tests.
