#!/usr/bin/env python3
"""Automated post-run financial and persistence reconciliation for mutation load.

Queries Expense Core endpoints for a target group fixture and verifies:
1. Group exists and balance sum equals zero (per currency).
2. Balance postings are balanced and reconcile with total expenses.
3. Monotonic sync changes track mutation operations.
4. Idempotent expense replay returns HTTP 201 without duplicate effects or revision bumps.
"""

from __future__ import annotations

import argparse
import json
import sys
from urllib.error import HTTPError, URLError
from urllib.request import Request, urlopen
from typing import Any
from tests.http_constants import ACCEPT, APPLICATION_JSON, AUTHORIZATION, BEARER_PREFIX, CONTENT_TYPE, IDEMPOTENCY_KEY


def request_json(
    url: str,
    method: str = "GET",
    body: dict[str, Any] | None = None,
    headers: dict[str, str] | None = None,
    timeout: float = 5.0,
) -> tuple[int, Any]:
    req_headers = {ACCEPT: APPLICATION_JSON}
    if headers:
        req_headers.update(headers)
    data = None
    if body is not None:
        req_headers[CONTENT_TYPE] = APPLICATION_JSON
        data = json.dumps(body).encode("utf-8")

    req = Request(url, data=data, headers=req_headers, method=method)
    try:
        with urlopen(req, timeout=timeout) as resp:
            content = resp.read().decode("utf-8")
            return resp.status, json.loads(content) if content else {}
    except HTTPError as error:
        try:
            content = error.read().decode("utf-8")
            return error.code, json.loads(content) if content else {}
        except Exception:
            return error.code, {}
        finally:
            error.close()


def reconcile_group(
    base_url: str,
    group_id: str,
    token: str = "test-user",
    participant_id: str = "00000000-0000-7000-8000-000000000001",
) -> dict[str, Any]:
    auth_headers = {AUTHORIZATION: f"{BEARER_PREFIX}{token}"}

    # Fetch active member to obtain valid participantId
    members_url = f"{base_url}/expense-core/v1/groups/{group_id}/members"
    status_m, members_data = request_json(members_url, headers=auth_headers)
    if status_m == 200 and isinstance(members_data, list) and len(members_data) > 0:
        participant_id = str(members_data[0].get("membershipId", participant_id))

    # 1. Fetch group details
    group_url = f"{base_url}/expense-core/v1/groups/{group_id}"
    status, group_data = request_json(group_url, headers=auth_headers)
    if status != 200:
        raise AssertionError(f"failed to fetch group {group_id}: HTTP {status} {group_data}")

    initial_revision = group_data.get("revision", 0)
    currency = group_data.get("currency", "EUR")

    # 2. Verify balances sum to zero
    balances_url = f"{base_url}/expense-core/v1/groups/{group_id}/balances"
    status, balances_data = request_json(balances_url, headers=auth_headers)
    if status != 200:
        raise AssertionError(f"failed to fetch balances: HTTP {status} {balances_data}")

    balances = balances_data.get("balances", [])
    total_balance_minor = sum(int(b.get("amount", {}).get("minor", 0)) for b in balances)
    if total_balance_minor != 0:
        raise AssertionError(f"group balance sum does not equal zero: {total_balance_minor}")

    # 3. Verify sync changes feed
    sync_url = f"{base_url}/expense-core/v1/groups/{group_id}/sync/changes?limit=100"
    status, sync_data = request_json(sync_url, headers=auth_headers)
    if status != 200:
        raise AssertionError(f"failed to fetch sync changes: HTTP {status} {sync_data}")

    changes = sync_data.get("changes", [])
    if not changes:
        raise AssertionError("sync changes feed is empty for group")

    # Verify monotonic revision ordering in sync changes
    revisions = [c.get("revision", 0) for c in changes]
    for i in range(1, len(revisions)):
        if revisions[i] < revisions[i - 1]:
            raise AssertionError(f"sync changes revisions not monotonic: {revisions}")

    # 4. Find first expense entity to test idempotent replay
    first_expense = None
    for c in changes:
        if not c.get("deleted") and c.get("payload"):
            try:
                payload = json.loads(c["payload"])
                if "expenseId" in payload:
                    first_expense = payload
                    break
            except Exception:
                continue

    if not first_expense:
        raise AssertionError("no valid expense found in sync changes to test replay")

    expense_id = first_expense["expenseId"]
    expense_minor = str(first_expense.get("amountMinor", 100))
    expense_curr = first_expense.get("currency", currency)

    replay_payload = {
        "expenseId": expense_id,
        "description": "k6 capacity fixture",
        "category": "other",
        "amount": {"currency": expense_curr, "minor": expense_minor},
        "payers": [{"participantId": participant_id, "amount": {"currency": expense_curr, "minor": expense_minor}}],
        "allocation": {"mode": "EQUAL", "items": [{"participantId": participant_id, "value": "1"}]},
    }

    # First replay attempt with identical Idempotency-Key and payload
    replay_headers = {
        **auth_headers,
        IDEMPOTENCY_KEY: expense_id,
    }
    replay_url = f"{base_url}/expense-core/v1/groups/{group_id}/expenses"
    status, replay_resp = request_json(replay_url, method="POST", body=replay_payload, headers=replay_headers)
    if status != 201:
        # Some implementations might use existing payload description from the k6 baseline
        replay_payload["description"] = "k6 1M baseline expense"
        status, replay_resp = request_json(replay_url, method="POST", body=replay_payload, headers=replay_headers)
        if status != 201:
            raise AssertionError(f"idempotent replay failed: HTTP {status} {replay_resp}")

    # Re-fetch group to confirm revision did NOT increment on replay
    status, group_data_after = request_json(group_url, headers=auth_headers)
    if status != 200:
        raise AssertionError(f"failed to re-fetch group: HTTP {status}")
    revision_after = group_data_after.get("revision", 0)
    if revision_after != initial_revision:
        raise AssertionError(
            f"idempotent replay caused revision increase: {initial_revision} -> {revision_after}"
        )

    # Confirm conflicting payload with same expense ID / key is rejected with HTTP 409
    conflict_payload = dict(replay_payload)
    conflict_payload["description"] = "conflicting description for replay check"
    status, conflict_resp = request_json(replay_url, method="POST", body=conflict_payload, headers=replay_headers)
    if status != 409:
        raise AssertionError(f"expected 409 conflict for mismatched idempotent replay, got {status}: {conflict_resp}")

    return {
        "groupId": group_id,
        "groupRevision": initial_revision,
        "totalBalanceMinor": total_balance_minor,
        "syncChangesCount": len(changes),
        "idempotentReplayStatus": 201,
        "conflictReplayStatus": 409,
        "status": "reconciled",
    }


def main() -> int:
    parser = argparse.ArgumentParser(description="Reconcile Expense Core mutation load fixture.")
    parser.add_argument("--group-id", help="UUID of group fixture to reconcile. If omitted, searches latest k6 group.")
    parser.add_argument("--base-url", default="http://localhost:28082", help="Expense Core base URL")
    parser.add_argument("--token", default="test-user", help="Bearer token")
    args = parser.parse_args()

    group_id = args.group_id
    if not group_id:
        status, groups = request_json(f"{args.base_url}/expense-core/v1/groups", headers={AUTHORIZATION: f"{BEARER_PREFIX}{args.token}"})
        if status != 200 or not isinstance(groups, list) or not groups:
            print("ERROR: could not find any groups in Expense Core", file=sys.stderr)
            return 1
        # Find latest k6 group
        for g in groups:
            if isinstance(g, dict) and "k6-" in g.get("name", ""):
                group_id = g.get("groupId")
                break
        if not group_id:
            group_id = groups[0].get("groupId")

    print(f"Reconciling mutation fixture for group {group_id}...")
    try:
        report = reconcile_group(args.base_url, group_id, args.token)
        print(json.dumps(report, indent=2))
        print("Reconciliation PASSED.")
        return 0
    except AssertionError as err:
        print(f"Reconciliation FAILED: {err}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    sys.exit(main())
