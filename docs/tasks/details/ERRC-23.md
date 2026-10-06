# ERRC-23: Migrate shared libraries and startup failures

## Context & Parent Milestone

- **Parent Workstream**: [`ERRC-01: Design six-digit domain error-code migration`](ERRC-01.md)
- **Phase**: Phase 4 — Bounded-context migration
- **Implementation Strategy**: [`docs/architecture/error-code-refactoring.md`](../../architecture/error-code-refactoring.md)

## Objective

Migrate all error definitions, exception translation adapters, and startup failure guards in the shared platform libraries (`libs/db`, `libs/security`, `libs/observability`, `libs/ids`) to use Domain 9 (`PlatformErrors`). Define clean translation policies preventing leaky library abstractions from bubbling up unhandled, and ensure failed startup sequences (bad configs, migration locks, broken keystores) terminate cleanly with descriptive diagnostics.

## Dependencies

- Preceding: [`ERRC-12`](ERRC-12.md), [`ERRC-13`](ERRC-13.md), [`ERRC-14`](ERRC-14.md), [`ERRC-15`](ERRC-15.md), [`ERRC-16`](ERRC-16.md), [`ERRC-17`](ERRC-17.md), [`ERRC-18`](ERRC-18.md)
- Platform guide: [`docs/architecture/errors/platform-libraries.md`](../../architecture/errors/platform-libraries.md)

## Owned Paths

- `docs/tasks/details/ERRC-23.md`
- `libs/db/src/main/kotlin/com/subhrodip/squarewise/db/`
- `libs/security/src/main/kotlin/com/subhrodip/squarewise/security/`
- `libs/observability/src/main/kotlin/com/subhrodip/squarewise/observability/`
- `libs/ids/src/main/kotlin/com/subhrodip/squarewise/ids/`
- `tools/qa/error_hygiene_allowlist.yaml` (prune all remaining library entries)

## Architecture & Design Patterns

- **Ports & Adapters (Platform Boundary Decoupling)**: Shared technical libraries must not throw raw vendor exceptions (e.g. `PSQLException`, `AmqpException`). They translate low-level driver failures into Platform Domain 9 definitions (`93xxxx` Persistence, `94xxxx` Messaging, `92xxxx` Security).
- **Fail-Fast Startup Pattern**: Environmental failures during application boot (e.g. Flyway migration lock, unavailable DB pool, missing private keys) throw typed startup exceptions that immediately stop the Spring context and exit with structured codes.
- **Circuit Breaker & Graceful Degradation Pattern**: Replicas or secondary read-pools that fail do not crash the application; they degrade gracefully to the primary writer pool with appropriate error telemetry.
- **Strict SOLID File Separation**: Every new exception, configuration validator, and database health indicator resides in its own isolated `.kt` file.

## Common Libraries & Framework Integration

- **`libs/db`**: Database pool configuration, read/write routing, and Flyway migration guards.
- **`libs/security`**: OIDC provider discovery and keystore loader.
- **`libs/observability`**: Tracing and metrics auto-configuration.
- **`libs/errors`**: `PlatformErrors` static catalog.

## Technical Requirements & Deliverables

1. **Migrate Platform Shared Exceptions**:
   - Error Framework (`91xxxx`): `ERROR_DEFINITION_NOT_FOUND` (`919901`).
   - Security (`92xxxx`): `JWT_SIGNATURE_INVALID` (`927101`), `KEYSTORE_UNAVAILABLE` (`928101`).
   - Persistence (`93xxxx`): `DB_CONNECTION_POOL_EXHAUSTED` (`938801`), `FLYWAY_MIGRATION_FAILED` (`938101`).
   - Messaging (`94xxxx`): `BROKER_CONNECTION_FAILED` (`948101`), `AMQP_MESSAGE_CORRUPTED` (`947101`).
   - Observability (`95xxxx`): `TELEMETRY_PIPELINE_DEGRADED` (`958801`).
   - IDs (`96xxxx`): `INVALID_UUID_FORMAT` (`961101`).
2. **Startup & Preflight Health Diagnostics**:
   - Implement preflight checks for database connectivity, Flyway lock resolution, and keystore access.
   - Format fatal startup errors as clean console messages with exit code and runbook links.
3. **Empty the Error Hygiene Allowlist**:
   - Prune all remaining platform entries from `tools/qa/error_hygiene_allowlist.yaml`.
   - The allowlist file should now be empty (0 entries), proving 100% of codebase has migrated.
4. **Library Test Suite Verification**:
   - Run tests across all libraries in `libs/` ensuring complete coverage.

## Acceptance Criteria

1. Zero generic exception throws or legacy error references remain in any module of `libs/`.
2. All shared library failures use Domain 9 `PlatformErrors` constants.
3. Startup preflight checks terminate cleanly with structured messages upon simulated config failures.
4. `tools/qa/error_hygiene_allowlist.yaml` has exactly 0 entries.
5. `make error-hygiene` passes across the entire repository with zero allowlist exceptions.
6. Validation commands execute cleanly.

## Validation Commands

```powershell
./gradlew.bat :libs:db:test :libs:security:test :libs:observability:test :libs:ids:test --rerun-tasks --no-daemon
uv run python tools/qa/check_error_hygiene.py
uv run python tools/contracts/validate.py
git diff --check
```

## Evidence Expectations

- JaCoCo test report showing complete test pass across all shared technical libraries.
- Clean output from `check_error_hygiene.py` with zero active allowlist entries.

## Rollout & Rollback Strategy

- Shared library update affecting all services.
- Internal error refactoring; public APIs remain additive.
- Rollback: Revert library commits if startup regressions are observed.
