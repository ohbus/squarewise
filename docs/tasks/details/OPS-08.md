# OPS-08: Compose dev environment configuration hardening and live acceptance workflow

## Objective

Harden `infra/local/docker-compose.dev.yml` and `Makefile` for complete local parity, proper inter-service environment variables, and automated live acceptance validation.

## Acceptance criteria

- `docker-compose.dev.yml` supplies:
  - `SQUAREWISE_ACCOUNTS_URL: http://accounts:8080` to `bff`
  - `SQUAREWISE_NOTIFICATIONS_EMAIL_HOST: mailpit` and `SQUAREWISE_NOTIFICATIONS_EMAIL_PORT: 1025` to `notifications`
  - Healthy service dependencies across all containers
- `Makefile` includes `acceptance-live` target running `python3 tests/acceptance/runner.py --require-services`.
- Compose configuration validates cleanly with `docker compose -f infra/local/docker-compose.dev.yml config --quiet`.

## Owned paths

- `infra/local/docker-compose.dev.yml`
- `Makefile`
- `docs/operations/quickstart.md`

## Validation commands

- `docker compose -f infra/local/docker-compose.dev.yml config --quiet`
- `python3 tools/contracts/validate.py`
- `git diff --check`

## Implementation notes

- Configured `infra/local/docker-compose.dev.yml`:
  - Added `SQUAREWISE_NOTIFICATIONS_EMAIL_HOST: mailpit` and `SQUAREWISE_NOTIFICATIONS_EMAIL_PORT: 1025` to the `notifications` service.
  - Updated `notifications.depends_on` to include `mailpit: {condition: service_healthy}`.
  - Added `SQUAREWISE_ACCOUNTS_URL: http://accounts:8080` to the `bff` service environment.
- Configured `Makefile`:
  - Added `acceptance-live` to `.PHONY`.
  - Added `acceptance-live: ## Run the acceptance test harness requiring live running services` target executing `python3 tests/acceptance/runner.py --require-services`.
- Updated `docs/operations/quickstart.md`:
  - Documented container environment variables for Accounts and Mailpit inter-connectivity.
  - Documented `make acceptance-live` and `python3 tests/acceptance/runner.py --require-services` behavior and distinction from dry-run `make acceptance`.

## Verification evidence

- `docker compose -f infra/local/docker-compose.dev.yml config --quiet` succeeded with exit code 0.
- `make help` displays `acceptance-live` with help description.
- `python3 tools/contracts/validate.py` succeeded with 0 exit code.
- `git diff --check` succeeded with no whitespace issues.
