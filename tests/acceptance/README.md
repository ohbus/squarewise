# Acceptance Test Harness (QA-01, QA-03 & QA-05)

This directory contains the backend acceptance runner for the Squarewise MVP.
It exercises real public HTTP and GraphQL interfaces and writes a machine-readable report to `build/reports/acceptance/qa-01.json`. Browser offline and accessibility journeys remain deferred until `app/web/` exists.

QA-05 adds public-interface edge-case probes in `qa05.py` for rollback,
concurrent rename, authorization, BFF fanout failure, and recovery. Unsupported
live fixtures are reported as `blocked` (not passed): real rollback requires
fault injection, concurrency requires independent database transactions, and
fanout failure requires controlled upstream failure.

## Test Scenarios

1. **QA-CONTRACTS**: Validates schemas and API specifications via `tools/contracts/validate.py`.
2. **QA-HEALTH**: Probes BFF `/actuator/health` and Expense Core `/actuator/health`.
3. **QA-GROUP-EXPENSE-SETTLEMENT**:
   - `POST /expense-core/v1/groups` to create a group.
   - `POST /expense-core/v1/groups/{groupId}/expenses` to create an expense with payers and allocations.
   - `GET /expense-core/v1/groups/{groupId}/balances` to check initial balances.
   - `POST /expense-core/v1/groups/{groupId}/settlements` to record a repayment.
   - `GET /expense-core/v1/groups/{groupId}/balances` to verify updated reconciled balances.
4. **QA-OFFLINE-REPLAY**:
   - `GET /expense-core/v1/groups/{groupId}/sync/snapshot` to verify snapshot schema and items.
   - `GET /expense-core/v1/groups/{groupId}/sync/changes` to verify changes feed.
5. **QA-WEBSOCKET-RESYNC**:
   - `POST /graphql` with query `query { groups { id name } }` using the injected signed `BEARER_TOKEN`.
   - Verifies HTTP 200 and presence of the `data` field.

## Execution

Run the scaffold-safe check (passes or marks unreachable services as blocked):

```sh
tests/acceptance/run.sh
```

or directly:

```sh
python3 tests/acceptance/runner.py
```

Run against live running services (fails if services are unreachable):

```sh
BFF_BASE_URL=http://localhost:28080 EXPENSE_CORE_BASE_URL=http://localhost:28082 tests/acceptance/run.sh --require-services
```

Options:
- `--require-services`: Exit with code 1 if services are unreachable/offline.
- `--timeout SECONDS`: HTTP request timeout (default: 2.0s).
- `--bff-url URL`: Base URL for BFF service (default: `http://localhost:28080` or `$BFF_BASE_URL`).
- `--expense-core-url URL`: Base URL for Expense Core service (default: `http://localhost:28082` or `$EXPENSE_CORE_BASE_URL`).
- `--report-path PATH`: Custom path for JSON report output (default: `build/reports/acceptance/qa-01.json`).

Run runner unit and integration tests:

```sh
python3 -m unittest tests/acceptance/test_runner.py
```

Run the deterministic QA-05 journey tests:

```sh
python3 -m unittest tests/acceptance/test_qa05.py
```
