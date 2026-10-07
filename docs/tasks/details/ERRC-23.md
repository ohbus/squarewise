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

## Implementation Notes and Evidence

- Migrated the owned security rate-limit secret decoder to a typed
  `PlatformDomainException` backed by `PlatformErrors.PLATFORM_CONFIGURATION_INVALID`;
  malformed or undersized deployment secrets now fail closed without a raw
  `IllegalArgumentException` boundary.
- Removed the ERRC-23-owned `libs/security` hygiene waiver and updated its
  focused tests to assert the governed startup/configuration exception.
- The declared shared-library suite passed: `libs/db`, `libs/security`,
  `libs/observability`, and `libs/ids` tests completed successfully.
- Three remaining allowlist entries belong to `libs/errors` web infrastructure,
  which is owned by the later ERRC-30 legacy-infrastructure retirement task;
  therefore the repository-wide zero-entry acceptance remains pending that
  dependency and this task stays `in_progress`.

## Phase and Ownership Audit (2026-10-07)

- Phase 0 is complete: ERRC-01 through ERRC-04 are registered as `done`, and
  their audit, registry/schema, and catalog validation evidence is present in
  the registry and progress ledger.
- A fresh scan of all ERRC-23-owned production library paths found no legacy
  `ApplicationException`, legacy `ErrorCode`, or generic `throw` references.
- The remaining hygiene findings are the three direct `ApiProblem` construction
  sites in `libs/errors/http/`. Those files are outside ERRC-23's owned paths
  and inside ERRC-30's owned `libs/errors` tree. ERRC-30 depends on ERRC-29,
  so taking those files in this increment would violate the sequential task
  and ownership rules.
- The tracker therefore records an explicit cross-task acceptance dependency;
  no compatibility behavior or acceptance criterion is weakened to make the
  hygiene count appear complete.
- Migrated the owned OIDC servlet/reactive decoder validation, signing-algorithm
  policy, and production cryptographic-secret guard to the same governed
  `PlatformDomainException` configuration boundary. The validation is
  centralized for the servlet and reactive decoder factories, and tests now
  assert the catalog-governed failure type.
- Migrated rate-limit policy, decision, and key-material bounds to the governed
  configuration boundary as well; malformed Redis store responses remain
  translated by the existing fail-closed store exception policy.
- Added a database-library `DbPlatformException` and migrated pool endpoint/
  timing validation, reader-health constructor bounds, scheduler lag-budget
  validation, and database auto-configuration bounds to
  `PlatformErrors.PLATFORM_CONFIGURATION_INVALID`. The remaining database
  route-policy and watermark input invariants are tracked for the next slice.
- Migrated database route-policy, execution-context, and causal-watermark
  validation to `DbPlatformException`, including safe translation of malformed
  hexadecimal LSN input without leaking the parser exception.
- Added an observability-library platform exception and migrated telemetry
  threshold validation and the bounded metric-cardinality overflow path to
  catalog-governed Platform errors; the latter now fails with the static
  observability degradation definition rather than `check`.
- The IDs generation path remains unchanged after a dependency audit found a
  circular Gradle edge: `libs/errors` publicly depends on `libs/ids` for request
  ID generation, so importing `PlatformErrors` from `libs/ids` would make the
  build cyclic. A dependency-inversion task is required before that path can
  adopt a governed Platform exception safely.
- Replaced the final raw Redis result validation failures with governed
  persistence-data exceptions before the existing fail-closed store translation.
- Migrated the database route guard and routed-reader acquisition failures to
  governed configuration/database-availability definitions; JDBC causes remain
  attached internally while the public exception identity is static.
