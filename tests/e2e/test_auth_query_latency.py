"""Measure authenticated query latency and prove writes do not call Accounts."""

from __future__ import annotations

import json
import os
import statistics
import time
import uuid
from typing import Final
from urllib.error import HTTPError
from urllib.request import Request, urlopen


BEARER_TOKEN: Final[str] = os.environ.get("BEARER_TOKEN", "")
EXPENSE_CORE_URL: Final[str] = os.environ.get("EXPENSE_CORE_URL", "http://localhost:8082")
BFF_URL: Final[str] = os.environ.get("BFF_URL", "http://localhost:8080")
ACCOUNTS_URL: Final[str] = os.environ.get("ACCOUNTS_URL", "http://localhost:8081")
SAMPLE_COUNT: Final[int] = 5


def statement_count(base_url: str, operation: str) -> int:
    """Read one bounded SQL statement counter from a protected actuator endpoint."""

    request = Request(
        f"{base_url}/actuator/prometheus",
        headers={"Authorization": f"Bearer {BEARER_TOKEN}"},
    )
    with urlopen(request, timeout=10) as response:
        payload = response.read().decode("utf-8")
    for line in payload.splitlines():
        if "squarewise_db_statement_total" not in line:
            continue
        if f'operation="{operation}"' not in line:
            continue
        return int(float(line.rsplit(" ", maxsplit=1)[1]))
    return 0


def request_json(url: str, body: bytes | None = None) -> tuple[int, object, float]:
    """Perform one authenticated request and return status, JSON, and milliseconds."""

    headers = {"Authorization": f"Bearer {BEARER_TOKEN}"}
    if body is not None:
        headers["Content-Type"] = "application/json"
    request = Request(url, data=body, headers=headers, method="POST" if body is not None else "GET")
    started = time.perf_counter()
    try:
        with urlopen(request, timeout=10) as response:
            payload = response.read().decode("utf-8")
            status = response.status
    except HTTPError as error:
        payload = error.read().decode("utf-8")
        status = error.code
    elapsed_ms = (time.perf_counter() - started) * 1000
    try:
        return status, json.loads(payload), elapsed_ms
    except json.JSONDecodeError:
        return status, payload, elapsed_ms


def p95(samples: list[float]) -> float:
    """Return the nearest-rank p95 for a non-empty bounded sample."""

    return sorted(samples)[int((len(samples) - 1) * 0.95)]


def require_success(status: int, payload: object, operation: str) -> None:
    """Fail with bounded response context when a probe operation is rejected."""

    if status < 200 or status >= 300:
        raise AssertionError(f"{operation} failed with HTTP {status}: {payload}")


def main() -> int:
    """Run representative reads and a durable write through public interfaces."""

    if not BEARER_TOKEN:
        print("  [skip] BEARER_TOKEN is required for authenticated query/latency evidence")
        return 0

    accounts_before = statement_count(ACCOUNTS_URL, "groups.list")
    expense_before = statement_count(EXPENSE_CORE_URL, "groups.list")
    expense_latencies: list[float] = []
    bff_latencies: list[float] = []
    for _ in range(SAMPLE_COUNT):
        status, payload, elapsed_ms = request_json(f"{EXPENSE_CORE_URL}/expense-core/v1/groups")
        require_success(status, payload, "Expense Core groups.list")
        expense_latencies.append(elapsed_ms)

        status, payload, elapsed_ms = request_json(
            f"{BFF_URL}/graphql", b'{"query":"{ groups { id name } }"}'
        )
        require_success(status, payload, "BFF groups query")
        if not isinstance(payload, dict) or payload.get("errors"):
            raise AssertionError(f"BFF groups query returned errors: {payload}")
        bff_latencies.append(elapsed_ms)

    create_payload = json.dumps(
        {"name": f"AUTH-09 latency {uuid.uuid4().hex[:8]}", "kind": "TRIP", "currency": "EUR"}
    ).encode("utf-8")
    status, payload, write_latency = request_json(
        f"{EXPENSE_CORE_URL}/expense-core/v1/groups", create_payload
    )
    require_success(status, payload, "Expense Core groups.create")
    if not isinstance(payload, dict):
        raise AssertionError(f"Expense Core groups.create returned non-object: {payload}")
    group_id = payload.get("groupId") or payload.get("id")
    if not isinstance(group_id, str) or not group_id:
        raise AssertionError(f"Expense Core groups.create returned no group identifier: {payload}")
    archive_status, archive_payload, _ = request_json(
        f"{EXPENSE_CORE_URL}/expense-core/v1/groups/{group_id}/archive", b"{}"
    )
    require_success(archive_status, archive_payload, "Expense Core groups.archive")

    accounts_after = statement_count(ACCOUNTS_URL, "groups.list")
    expense_after = statement_count(EXPENSE_CORE_URL, "groups.list")
    if accounts_after != accounts_before:
        raise AssertionError(
            f"authenticated reads/writes changed Accounts groups.list: {accounts_before}->{accounts_after}"
        )
    if expense_after <= expense_before:
        raise AssertionError(
            f"authenticated reads produced no Expense Core groups.list statements: {expense_before}->{expense_after}"
        )
    print(
        "  SQL isolation: Accounts groups.list "
        f"{accounts_before}->{accounts_after}; Expense Core groups.list {expense_before}->{expense_after}"
    )
    print(
        "  latency p95 ms: "
        f"Expense Core groups.list={p95(expense_latencies):.2f}; "
        f"BFF groups={p95(bff_latencies):.2f}; groups.create={write_latency:.2f}"
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
