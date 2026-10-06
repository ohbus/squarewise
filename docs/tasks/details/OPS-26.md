# OPS-26 — Decouple dev data seeder and prepare local stack for non-docker runtimes

## Status

`completed`

## Objective

Decouple `tools/ops/seed_dev_data.py` from host Docker CLI assumptions, align OIDC token identities with database account personas, ensure local development data persists across container restarts, and make local verification tools aware of containerized developer environments.

## Background and motivation

1. The data seeder previously required a host Docker CLI even when PostgreSQL was directly reachable.
2. Synthetic seeded subjects did not match Keycloak service-account JWT subjects.
3. PostgreSQL lacked a persistent named local volume.
4. `make doctor` treated Docker as mandatory inside devcontainers.

## Owned paths

```text
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
- No application Kotlin/Java code changes were required.

## Design decisions

### Direct TCP SQL execution

When `SQUAREWISE_POSTGRES_HOST` or `PGHOST` is set, the seeder invokes `psql`
over TCP and passes `PGPASSWORD` in the process environment. Without either
setting, the host-native workflow retains its Docker-exec compatibility path.

### Token-subject synchronization

The seeder extracts the real JWT `sub` and `iss` claims for the local personas
and provisions Accounts identities and group memberships with those values.

### Persistent local database

The local Compose topology mounts the named `postgres-data` volume at
`/var/lib/postgresql/data` so ordinary container recreation preserves seeded data.

### Container-aware environment checks

`make doctor` skips host-level Docker checks when `DEVCONTAINER` is set while
continuing to validate Java 25, `uv`, and the Gradle wrapper.

## Acceptance criteria

- [x] The seeder supports direct TCP PostgreSQL access without a Docker CLI.
- [x] Seeded account identities use the real Keycloak JWT subjects.
- [x] Local PostgreSQL uses the persistent `postgres-data` named volume.
- [x] `make doctor` is devcontainer-aware.
- [x] Strict Python typing is preserved.

## Validation commands

```text
uv run --frozen --no-build mypy tools/ops/seed_dev_data.py
make compose-config
make doctor
DEVCONTAINER=true make doctor
git diff --check
```

## Evidence

The implementation, Compose validation, strict typing, environment checks, and
whitespace validation passed and are recorded in the progress ledger.
