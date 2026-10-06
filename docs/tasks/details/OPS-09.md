# OPS-09: Local Compose topology and developer command guide

## Objective

Provide validated Docker Compose entry points for dependency-only development,
each application’s exact standalone prerequisites, and the complete application
stack, with exhaustive Make help and operations documentation.

## Dependencies

- `OPS-03`
- `OPS-08`
- `DOC-10`

## Owned paths

- `infra/local/`
- `Makefile`
- `docs/operations/README.md`
- `docs/operations/quickstart.md`
- `docs/operations/compose-topology.md`
- `docs/tasks/details/OPS-09.md`

## Acceptance criteria

- A dependency-only Compose entry point runs PostgreSQL, RabbitMQ, and Mailpit.
- Each backend application has a documented Compose entry point containing only
  the external/upstream services needed for a native standalone run.
- One full Compose entry point runs all applications and dependencies.
- `make help` exhaustively describes startup, validation, status, logs, and
  teardown commands without hiding which Compose file is used.
- All Compose configurations render successfully and documentation matches the
  actual ports, credentials, health checks, and startup order.

## Validation commands

- `docker compose -f infra/local/docker-compose.yml config --quiet`
- Validate every standalone and full-stack Compose entry point with
  `docker compose ... config --quiet`.
- `make help`
- `make -n` for every added operational target.
- `python3 tools/contracts/validate.py`
- `git diff --check`

## Expected evidence

- Rendered configuration validation for every topology.
- A command-to-file/service matrix in the operations guide.
- Dry-run evidence for all Make targets.

## Implementation notes

- `docker-compose.yml` remains the dependency-only PostgreSQL, RabbitMQ, and
  Mailpit entry point.
- Added narrow native prerequisites for Accounts, Expense Core, Notifications,
  and BFF. The BFF entry point runs the real Accounts and Expense Core upstreams
  plus their dependencies; BFF itself remains native and database-free.
- `docker-compose.dev.yml` remains the complete applications-and-dependencies
  topology.
- Added explicit config, startup, status, log, and teardown Make targets for
  each topology, while retaining existing complete-stack aliases.
- Added the authoritative topology matrix and synchronized the operations plan
  and quickstart with actual ports, local credentials, health checks, and order.

## Known limitations

- The topologies intentionally share stable host ports and are designed to run
  one at a time.
- The local stack does not provision an OIDC provider; provider selection and
  authenticated identity integration remain external.

## Verification evidence

- `make compose-config` completed with exit code 0 for all six local Compose
  files.
- Direct `docker compose -f <file> config --services` inspection confirmed the
  expected service sets: dependencies (3), Accounts (1), Expense Core (2),
  Notifications (3), BFF upstreams (4), and full stack (7).
- `make help` completed with exit code 0 and listed config, startup, status,
  logs, and teardown commands for every topology.
- `make -n` completed with exit code 0 for all 36 local Compose targets and
  compatibility aliases.
- `python3 tools/contracts/validate.py` completed with exit code 0, validating
  all contract JSON, GraphQL declarations, and the 80-task registry.
- `git diff --check` completed with exit code 0.

Implementation commits: `ea5c1d8`, `05b326b`, and `bcdf107`.
