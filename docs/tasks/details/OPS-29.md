# OPS-29 — Implement zero-friction startup automation and background data seeding

## Status
`completed`

## Objective
Automate local configuration setup, backing service readiness checks, and initial development database seeding so developers opening the Devcontainer can immediately issue authenticated GraphQL and REST requests without manual intervention.

## Background and motivation
Even with a pre-configured container, developers often experience friction if they have to manually copy `.env.example`, poll for database and Keycloak readiness, or figure out how to seed test users and groups. Automating this in a clean, idempotent `postStartCommand` lifecycle hook ensures an instant "ready-to-code" experience.

## Owned paths
```
.devcontainer/post-start.sh
docs/tasks/details/OPS-29.md
```

## Dependencies
- `OPS-28` — Devcontainer workspace scaffolding and Compose integration.

## Architecture and design decisions

### 1. Script lifecycle hook (`.devcontainer/post-start.sh`)
Executes automatically on container start via `"postStartCommand"`:
1. **Local config bootstrap**: Copies `infra/local/.env.example` to `infra/local/.env` if not already present.
2. **Readiness polling**: Bounded polling loop checking PostgreSQL readiness via `pg_isready -h postgres-db -U squarewise -d squarewise_accounts` and Keycloak readiness via `curl http://idp-keycloak:8080/health/ready`.
3. **Idempotent seeding**: Queries `account_profiles` table count over TCP; if 0, triggers `uv run python3 tools/ops/seed_dev_data.py`. If data already exists, skips gracefully.
4. **Welcome dashboard**: Prints a clear terminal summary of all active endpoints, pre-minted Bearer tokens for personas, and common verification commands.

## Acceptance criteria
- [x] `.devcontainer/post-start.sh` ensures `infra/local/.env` exists with non-secret local development defaults.
- [x] `post-start.sh` polls PostgreSQL (`postgres-db:5432`) and Keycloak (`idp-keycloak:8080`) readiness probes with bounded retries.
- [x] `post-start.sh` conditionally runs `uv run python3 tools/ops/seed_dev_data.py` only when account profiles table is empty.
- [x] Welcome dashboard displays clickable local endpoints, pre-minted bearer tokens, and test execution hints.
- [x] `postStartCommand` is wired into `.devcontainer/devcontainer.json`.

## Validation commands
```bash
bash -n .devcontainer/post-start.sh
git diff --check
```

## Evidence
- `.devcontainer/post-start.sh` created and verified with `bash -n`.
- Bounded readiness waiting implemented for PostgreSQL TCP (`pg_isready`) and Keycloak HTTP (`/health/ready`).
- Automated conditional data seeding calls `tools/ops/seed_dev_data.py` over direct TCP.
- Welcome dashboard prints endpoints, personas, and test execution hints.
- Wired into `.devcontainer/devcontainer.json` via `"postStartCommand": "bash .devcontainer/post-start.sh"`.
