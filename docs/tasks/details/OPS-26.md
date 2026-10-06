# OPS-26 — Decouple dev data seeder and prepare local stack for non-docker runtimes

## Status
`completed`

## Objective
Decouple `tools/ops/seed_dev_data.py` from host Docker CLI assumptions, align OIDC token identities with database account personas, ensure local development data persists across container restarts, and make local verification tools aware of containerized developer environments.

## Background and motivation

### Current pain points
1. **Host Docker CLI tight coupling**: `tools/ops/seed_dev_data.py` executes SQL queries strictly via `subprocess.run(["docker", "exec", POSTGRES_CONTAINER, "psql", ...])`. In containerized developer environments (such as Devcontainers without Docker-outside-of-Docker), `docker` is not installed or available inside the workspace container, causing `make seed` to crash even though PostgreSQL is fully reachable over standard TCP on the container network (`postgres-db:5432`).
2. **Keycloak identity mismatch**: `seed_dev_data.py` currently seeds personas with hardcoded synthetic subjects (`sqw:a224e36b-...`), while Keycloak client-credentials service accounts (`squarewise-ci`, `squarewise-ci-e2e-bob`) mint JWT tokens with Keycloak-generated user UUIDs in the `sub` claim. When developers make authenticated API calls with acquired tokens, the backend looks up `account_profiles` by the token's `sub` and fails to find the seeded persona profile.
3. **Transient database volume**: `infra/local/docker-compose.yml` mounts `./init-databases.sql` but defines no named volume for `/var/lib/postgresql/data`. As a result, restarting or recreating the database container wipes all seeded tables, requiring manual re-seeding on every session.
4. **`make doctor` in non-Docker runtimes**: `make doctor` currently hard-fails if `docker` is not on the `PATH`. In a pure Devcontainer workspace, Docker daemon commands run on the host rather than inside the workspace container.

## Owned paths
```
tools/ops/seed_dev_data.py
Makefile
infra/local/docker-compose.yml
infra/local/docker-compose.dev.yml
docs/tasks/details/OPS-26.md
docs/tasks/registry.yaml
docs/tasks/board.md
docs/tasks/progress.md
```

## Dependencies
- `OPS-25` (done) — Python environment and `uv` package management are standardized.
- No application Kotlin/Java code changes required.

## Design decisions

### 1. Direct TCP SQL execution via `SQUAREWISE_POSTGRES_HOST` / `PGHOST`
In `tools/ops/seed_dev_data.py`, `exec_psql()` is updated to inspect `SQUAREWISE_POSTGRES_HOST` (or `PGHOST`). If set, it executes `psql -h <host> -p <port> -U <user> -d <db> -c <query>` passing `PGPASSWORD` in the execution environment. If not set, it retains backward compatibility by falling back to `docker exec`.

### 2. Dynamic token subject extraction and profile synchronization
When acquiring OIDC tokens for Alice (`squarewise-ci`), Bob (`squarewise-ci-e2e-bob`), and Charlie (`squarewise-ci-e2e-nonmember`), `seed_dev_data.py` extracts the real JWT `sub` and `iss` claims via `extract_jwt_sub()` (aligning with `tests/e2e/acquire_local_tokens.sh`). The persona's profile and identity in `squarewise_accounts` are provisioned with that exact `sub` and `iss`. When group memberships are populated, their subjects match the real token `sub`, enabling immediate authenticated interaction.

### 3. Named persistent volume `postgres-data`
`infra/local/docker-compose.yml` and dependent compose overlays declare named volume `postgres-data` mounted at `/var/lib/postgresql/data`. Seeded groups, expenses, and accounts persist across container restarts.

### 4. Container-aware `make doctor`
`Makefile` updates `doctor` to check if `$DEVCONTAINER` is set; if set, host-level Docker checks are skipped while continuing to verify Java 25, `uv`, and the Gradle wrapper.

## Acceptance criteria
- [x] `tools/ops/seed_dev_data.py` connects via direct TCP when `SQUAREWISE_POSTGRES_HOST` or `PGHOST` is set, without requiring the docker CLI.
- [x] `seed_dev_data.py` extracts the real JWT `sub` claim from acquired Keycloak tokens so that seeded account profiles match live authenticated personas.
- [x] `infra/local/docker-compose.yml` attaches a persistent named volume (`postgres-data`) so seeded development data survives container recreation.
- [x] `Makefile` `doctor` target checks if `DEVCONTAINER` is active and skips host-level Docker checks when running inside the workspace container.
- [x] Strict typing is preserved: `uv run --frozen --no-build mypy tools/ops/seed_dev_data.py` exits 0.

## Validation commands
```bash
uv run --frozen --no-build mypy tools/ops/seed_dev_data.py
make compose-config
make doctor
DEVCONTAINER=true make doctor
git diff --check
```

## Implementation order
1. Update `tools/ops/seed_dev_data.py` to support TCP connection and dynamic token subject extraction.
2. Update `infra/local/docker-compose.yml` and `docker-compose.dev.yml` to attach persistent volume `postgres-data`.
3. Update `Makefile` `doctor` target to skip Docker checks when `$DEVCONTAINER` is set.
4. Validate with `mypy`, `make compose-config`, and `make doctor`.
5. Update task tracking files.
