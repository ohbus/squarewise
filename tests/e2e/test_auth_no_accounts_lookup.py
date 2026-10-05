"""Prove ordinary bearer requests do not require an Accounts service lookup."""

from __future__ import annotations

import json
import os
import re
import subprocess
from typing import Final
from urllib.error import HTTPError
from urllib.request import Request, urlopen


COMPOSE_FILE: Final[str] = os.environ.get(
    "SQUAREWISE_COMPOSE_FILE", "infra/local/docker-compose.dev.yml"
)
COMPOSE_PROJECT: Final[str] = os.environ.get("SQUAREWISE_COMPOSE_PROJECT", "")
REDIS_PASSWORD: Final[str] = os.environ.get(
    "REDIS_PASSWORD", "squarewise-redis-local-only"
)
BEARER_TOKEN: Final[str] = os.environ.get("BEARER_TOKEN", "")
EXPENSE_CORE_URL: Final[str] = os.environ.get("EXPENSE_CORE_URL", "http://localhost:8082")
BFF_URL: Final[str] = os.environ.get("BFF_URL", "http://localhost:8080")


def statement_count(base_url: str, operation: str) -> int:
    """Read one bounded Hibernate statement counter from a protected actuator endpoint."""
    if not BEARER_TOKEN:
        raise RuntimeError("BEARER_TOKEN is required for the local JWT lookup probe")
    request = Request(
        f"{base_url}/actuator/prometheus",
        headers={"Authorization": f"Bearer {BEARER_TOKEN}"},
    )
    with urlopen(request, timeout=10) as response:
        payload = response.read().decode("utf-8")
    for line in payload.splitlines():
        match = re.match(r'^squarewise_db_statement_total\{([^}]*)\}\s+([0-9.]+)$', line)
        if match and f'operation="{operation}"' in match.group(1):
            return int(float(match.group(2)))
    return 0


def compose(*arguments: str) -> str:
    """Run a Compose command against the explicitly selected local topology."""
    command: list[str] = ["docker", "compose"]
    if COMPOSE_PROJECT:
        command.extend(("--project-name", COMPOSE_PROJECT))
    command.extend(("-f", COMPOSE_FILE, *arguments))
    completed = subprocess.run(command, check=True, capture_output=True, text=True)
    return completed.stdout.strip()


def clear_rate_limit_namespace() -> None:
    """Delete only limiter keys so this probe is independent of prior suites."""
    raw_keys = compose(
        "exec", "-T", "redis", "redis-cli", "-a", REDIS_PASSWORD,
        "--no-auth-warning", "--scan", "--pattern", "squarewise:rl:v1:*",
    )
    keys = [key for key in raw_keys.splitlines() if key]
    if keys:
        compose(
            "exec", "-T", "redis", "redis-cli", "-a", REDIS_PASSWORD,
            "--no-auth-warning", "DEL", *keys,
        )


def request_json(url: str, body: bytes | None = None) -> tuple[int, object]:
    """Execute one authenticated public request and decode its JSON response."""
    if not BEARER_TOKEN:
        raise RuntimeError("BEARER_TOKEN is required for the local JWT lookup probe")
    headers = {"Authorization": f"Bearer {BEARER_TOKEN}"}
    if body is not None:
        headers["Content-Type"] = "application/json"
    request = Request(url, data=body, headers=headers, method="POST" if body else "GET")
    try:
        with urlopen(request, timeout=10) as response:
            return response.status, json.loads(response.read().decode("utf-8"))
    except HTTPError as error:
        raw = error.read().decode("utf-8")
        try:
            return error.code, json.loads(raw)
        except json.JSONDecodeError:
            return error.code, raw


def main() -> int:
    """Stop Accounts and verify resource authorization continues locally."""
    clear_rate_limit_namespace()
    accounts_before = statement_count("http://localhost:8081", "groups.list")
    expense_before = statement_count(EXPENSE_CORE_URL, "groups.list")
    expense_status, expense_response = request_json(
        f"{EXPENSE_CORE_URL}/expense-core/v1/groups"
    )
    if expense_status != 200:
        raise AssertionError(
            f"Expense Core bearer request failed before isolation check: HTTP {expense_status} ({expense_response})"
        )
    bff_status, bff_response = request_json(
        f"{BFF_URL}/graphql", b'{"query":"{ groups { id name } }"}'
    )
    if bff_status != 200 or not isinstance(bff_response, dict) or bff_response.get("errors"):
        raise AssertionError(
            f"BFF groups request failed before isolation check: HTTP {bff_status} ({bff_response})"
        )
    accounts_after = statement_count("http://localhost:8081", "groups.list")
    expense_after = statement_count(EXPENSE_CORE_URL, "groups.list")
    if accounts_after != accounts_before:
        raise AssertionError(
            f"ordinary group reads changed Accounts groups.list SQL count: {accounts_before} -> {accounts_after}"
        )
    if expense_after <= expense_before:
        raise AssertionError(
            f"ordinary group reads produced no Expense Core groups.list SQL: {expense_before} -> {expense_after}"
        )
    print(
        "  SQL telemetry: Accounts groups.list "
        f"{accounts_before}->{accounts_after}; Expense Core groups.list {expense_before}->{expense_after}"
    )
    compose("stop", "accounts")
    try:
        expense_status, expense_response = request_json(
            f"{EXPENSE_CORE_URL}/expense-core/v1/groups"
        )
        if expense_status != 200:
            raise AssertionError(
                f"Expense Core bearer request required Accounts: HTTP {expense_status} ({expense_response})"
            )

        bff_status, bff_response = request_json(
            f"{BFF_URL}/graphql", b'{"query":"{ groups { id name } }"}'
        )
        if bff_status != 200 or not isinstance(bff_response, dict) or bff_response.get("errors"):
            raise AssertionError(
                f"BFF groups request required Accounts: HTTP {bff_status} ({bff_response})"
            )
        print("  Expense Core and BFF authenticated reads succeeded while Accounts was stopped")
        return 0
    finally:
        compose("start", "accounts")


if __name__ == "__main__":
    raise SystemExit(main())
