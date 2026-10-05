#!/usr/bin/env python3
"""
E2E Chaos & Fault Recovery Drill Test Suite

Verifies:
1. Transactional Outbox Isolation during Message Broker (RabbitMQ) Outages:
   - RabbitMQ container is paused / stopped.
   - Expense Core transactions (expense creation, balance postings, audit entries)
     continue to succeed with 100% integrity (local ACID guarantees).
   - Corresponding outbox events remain in PENDING status in PostgreSQL.
   - RabbitMQ container is restored and health is restored.
   - Expense Core outbox relay daemon drains PENDING records to PUBLISHED status.
   - Downstream Notifications service receives and confirms delivered events.
"""

import base64
import json
import os
import subprocess
import io
import sys
import time
from urllib.error import HTTPError, URLError
from urllib.request import Request, urlopen
import uuid
from typing import Any
from tests.http_constants import APPLICATION_JSON, AUTHORIZATION, BEARER_PREFIX, CONTENT_TYPE

BFF_URL = os.environ.get("SQUAREWISE_BFF_URL", "http://localhost:28080")
ACCOUNTS_URL = os.environ.get("SQUAREWISE_ACCOUNTS_URL", "http://localhost:28081")
EXPENSE_CORE_URL = os.environ.get("SQUAREWISE_EXPENSE_CORE_URL", "http://localhost:28082")
NOTIFICATIONS_URL = os.environ.get("SQUAREWISE_NOTIFICATIONS_URL", "http://localhost:28083")
EXPENSE_CORE_CONTAINER = os.environ.get("SQUAREWISE_EXPENSE_CORE_CONTAINER", "local-expense-core-1")
RABBITMQ_CONTAINER = os.environ.get("SQUAREWISE_RABBITMQ_CONTAINER", "local-rabbitmq-1")
POSTGRES_CONTAINER = os.environ.get("SQUAREWISE_POSTGRES_CONTAINER", "local-postgres-1")


def run_cmd(cmd: str) -> str:
    res = subprocess.run(cmd, shell=True, capture_output=True, text=True)
    if res.returncode != 0:
        raise RuntimeError(f"Command failed ({cmd}): {res.stderr.strip()}")
    return res.stdout.strip()


def query_postgres(sql: str) -> str:
    cmd = f'docker exec -i {POSTGRES_CONTAINER} psql -U squarewise -d squarewise_expense_core -t -A -c "{sql}"'
    return run_cmd(cmd)


def request_json(url: str, method: str = "GET", body: Any = None,
                 bearer: str | None = None, extra_headers: dict[str, str] | None = None) -> tuple[int, dict[str, Any]]:
    headers = {CONTENT_TYPE: APPLICATION_JSON}
    if bearer:
        headers[AUTHORIZATION] = f"{BEARER_PREFIX}{bearer}"
    if extra_headers:
        headers.update(extra_headers)

    data = json.dumps(body).encode("utf-8") if body is not None else None
    req = Request(url, data=data, headers=headers, method=method)
    try:
        with urlopen(req, timeout=10) as resp:
            return resp.status, json.loads(resp.read().decode("utf-8"))
    except HTTPError as e:
        raw = e.read().decode("utf-8")
        try:
            return e.code, json.loads(raw)
        except Exception:
            return e.code, {"raw": raw}


def graphql_query(query: str, variables: dict[str, Any] | None = None,
                  bearer: str = "local-user") -> tuple[int, dict[str, Any]]:
    payload = {"query": query, "variables": variables or {}}
    return request_json(f"{BFF_URL}/graphql", method="POST", body=payload, bearer=bearer)


def extract_jwt_subject(token: str | None) -> str:
    """Safely decode and extract the JWT 'sub' claim from a Bearer token if present."""
    if not token:
        return ""
    try:
        parts = token.split(".")
        if len(parts) >= 2:
            payload = parts[1]
            payload += "=" * (-len(payload) % 4)
            data = json.loads(base64.urlsafe_b64decode(payload))
            if "sub" in data:
                return str(data["sub"])
    except Exception:
        pass
    return ""


def resolve_membership_id(members: Any, *candidates: str | None) -> str:
    """Resolve membershipId matching any candidate identifier (JWT sub, accountId, displayName)."""
    valid_candidates = {c for c in candidates if c}
    member_list = members if isinstance(members, list) else []
    for m in member_list:
        if isinstance(m, dict) and (m.get("subject") in valid_candidates or m.get("displayName") in valid_candidates):
            return str(m["membershipId"])
    raise KeyError(f"None of candidates {valid_candidates} found in members: {members}")


def run_chaos_recovery_tests() -> None:
    """Run outage and recovery checks with explicit UTF-8 console output."""
    if isinstance(sys.stdout, io.TextIOWrapper):
        sys.stdout.reconfigure(encoding="utf-8")
    if isinstance(sys.stderr, io.TextIOWrapper):
        sys.stderr.reconfigure(encoding="utf-8")
    print("=" * 70)
    print("🌪️  Running Chaos & Message Broker Outage Recovery Drill")
    print("=" * 70)

    # Step 1: Provision test users and active group
    print("\n[Step 1] Provisioning test users and active group...")
    user_a = os.environ.get("SQUAREWISE_E2E_TOKEN_A", os.environ.get("BEARER_TOKEN"))
    user_b = os.environ.get("SQUAREWISE_E2E_TOKEN_B", user_a)
    if not user_a or not user_b:
        raise RuntimeError("SQUAREWISE_E2E_TOKEN_A and SQUAREWISE_E2E_TOKEN_B must contain signed tokens")

    status, profile_a = request_json(f"{ACCOUNTS_URL}/accounts/v1/me", bearer=user_a)
    assert status == 200, f"Failed Alice profile: {profile_a}"
    alice_id = profile_a["accountId"]

    status, profile_b = request_json(f"{ACCOUNTS_URL}/accounts/v1/me", bearer=user_b)
    assert status == 200, f"Failed Bob profile: {profile_b}"
    bob_id = profile_b["accountId"]

    status, group_res = graphql_query(
        """
        mutation CreateGroup($input: CreateGroupInput!) {
            createGroup(input: $input) {
                id
                name
                revision
            }
        }
        """,
        variables={"input": {"name": "Chaos Expedition", "kind": "TRIP", "currency": "EUR"}},
        bearer=user_a
    )
    assert status == 200 and "data" in group_res, f"Group creation failed: {group_res}"
    group_id = group_res["data"]["createGroup"]["id"]
    print(f"  ✓ Group created: id={group_id}")

    # Add Bob to group
    status, invite = request_json(f"{EXPENSE_CORE_URL}/expense-core/v1/groups/{group_id}/invites", method="POST", body={"expiresInHours": 24}, bearer=user_a)
    assert status == 201, f"Failed invite: {invite}"
    status, claim = request_json(f"{EXPENSE_CORE_URL}/expense-core/v1/invites/{invite['token']}/claim", method="POST", bearer=user_b)
    assert status == 200, f"Failed claim: {claim}"
    status, members = request_json(f"{EXPENSE_CORE_URL}/expense-core/v1/groups/{group_id}/members", bearer=user_a)
    assert status == 200, f"Failed to list group members: {members}"
    alice_sub = extract_jwt_subject(user_a)
    bob_sub = extract_jwt_subject(user_b)
    alice_id = resolve_membership_id(members, alice_sub, profile_a.get("displayName"), profile_a.get("accountId"))
    bob_id = resolve_membership_id(members, bob_sub, profile_b.get("displayName"), profile_b.get("accountId"))
    print(f"  ✓ Members active: Alice={alice_id}, Bob={bob_id}")

    # Verify the GraphQL BFF exposes an upstream outage as a structured error
    # envelope and does not turn a dependency failure into fabricated data.
    print("\n[Step 2] Injecting Expense Core outage for GraphQL dependency check...")
    run_cmd(f"docker pause {EXPENSE_CORE_CONTAINER}")
    try:
        status, outage_response = graphql_query(
            "query GroupsDuringOutage { groups { id name revision } }", bearer=user_a
        )
        assert status == 200, f"Unexpected GraphQL transport status during outage: {outage_response}"
        assert outage_response.get("errors"), f"Expected GraphQL errors during outage: {outage_response}"
        outage_data = outage_response.get("data") or {}
        assert not outage_data.get("groups"), (
            f"BFF returned fabricated group data during outage: {outage_response}"
        )
        print("  ✓ GraphQL dependency outage returned a structured error envelope")
    finally:
        run_cmd(f"docker unpause {EXPENSE_CORE_CONTAINER}")
        print("  ✓ Expense Core is restored")

    status, recovered_response = graphql_query(
        "query GroupsAfterRecovery { groups { id name revision } }", bearer=user_a
    )
    recovered_groups = (recovered_response.get("data") or {}).get("groups") or []
    assert status == 200 and any(group.get("id") == group_id for group in recovered_groups), (
        f"GraphQL did not recover after Expense Core outage: {recovered_response}"
    )
    print("  ✓ GraphQL group query recovered after Expense Core restoration")

    # Step 3: Simulate RabbitMQ Outage (Pause container)
    print("\n[Step 3] Injecting fault: pausing RabbitMQ broker container...")
    run_cmd(f"docker pause {RABBITMQ_CONTAINER}")
    print("  ✓ RabbitMQ is now PAUSED (outage injected)")

    expense_id = str(uuid.uuid4())
    try:
        # Step 4: Execute Financial Operations during broker outage
        print("\n[Step 4] Executing expense mutation during RabbitMQ outage...")
        mutation = """
        mutation CreateExpense($groupId: ID!, $input: CreateExpenseInput!, $idempotencyKey: String!) {
            createExpense(groupId: $groupId, input: $input, idempotencyKey: $idempotencyKey) {
                id
                version
                description
                amount { currency minor }
            }
        }
        """
        payload = {
            "expenseId": expense_id,
            "description": "Emergency Generator Fuel",
            "amount": {"currency": "EUR", "minor": "7500"},
            "payers": [{"participantId": alice_id, "amount": {"currency": "EUR", "minor": "7500"}}],
            "allocation": {
                "mode": "EQUAL",
                "items": [
                    {"participantId": alice_id, "value": "1"},
                    {"participantId": bob_id, "value": "1"}
                ]
            }
        }
        status, exp_res = graphql_query(
            mutation,
            variables={"groupId": group_id, "input": payload, "idempotencyKey": str(uuid.uuid4())},
            bearer=user_a
        )
        assert status == 200 and "data" in exp_res, f"Mutation failed during outage: {exp_res}"
        print(f"  ✓ Expense successfully recorded during outage: '{exp_res['data']['createExpense']['description']}'")

        # Step 5: Verify Transactional Isolation & Outbox PENDING state in PostgreSQL
        print("\n[Step 5] Verifying transactional isolation and outbox state in PostgreSQL...")
        # Check balance posting succeeded
        status, balances_res = request_json(f"{EXPENSE_CORE_URL}/expense-core/v1/groups/{group_id}/balances", bearer=user_a)
        assert status == 200, f"Failed to get balances: {balances_res}"
        bal_map = {b["participantId"]: int(b["amount"]["minor"]) for b in balances_res["balances"]}
        assert bal_map.get(alice_id) == 3750, f"Alice balance mismatch: {bal_map}"
        assert bal_map.get(bob_id) == -3750, f"Bob balance mismatch: {bal_map}"
        print(f"  ✓ Financial ledger updated cleanly: {bal_map}")

        # Query Outbox record status directly
        outbox_status = query_postgres(f"SELECT status FROM expense_outbox WHERE aggregate_id = '{expense_id}' LIMIT 1;")
        print(f"  ✓ Outbox record for expense {expense_id} status: '{outbox_status}'")
        assert outbox_status in ("PENDING", "CLAIMED"), f"Expected PENDING or CLAIMED during outage, got: {outbox_status}"

    finally:
        # Step 6: Heal RabbitMQ Container
        print("\n[Step 6] Healing fault: unpausing RabbitMQ broker container...")
        run_cmd(f"docker unpause {RABBITMQ_CONTAINER}")
        print("  ✓ RabbitMQ is now UNPAUSED (broker healthy)")

    # Step 7: Verify Outbox Relay Drain to PUBLISHED
    print("\n[Step 7] Monitoring outbox relay daemon draining PENDING records to PUBLISHED...")
    drained = False
    for attempt in range(1, 15):
        current_status = query_postgres(f"SELECT status FROM expense_outbox WHERE aggregate_id = '{expense_id}' LIMIT 1;")
        if current_status == "PUBLISHED":
            print(f"  ✓ Outbox event successfully drained to PUBLISHED after {attempt}s")
            drained = True
            break
        time.sleep(1)

    assert drained, f"Outbox event failed to reach PUBLISHED status: {current_status}"

    # Step 8: Verify Event Delivery into Downstream Notifications Inbox
    print("\n[Step 8] Verifying downstream event delivery in Notifications inbox...")
    delivered = False
    for attempt in range(1, 10):
        status_inbox, inbox_data = request_json(f"{NOTIFICATIONS_URL}/notifications/v1/inbox", bearer=user_a)
        if status_inbox == 200 and inbox_data.get("items"):
            items = inbox_data["items"]
            matching = [item for item in items if expense_id in str(item.get("notificationId")) or group_id in str(item.get("message"))]
            if matching:
                print(f"  ✓ Notification confirmed in inbox: eventType={matching[0]['eventType']}, message='{matching[0]['message']}'")
                delivered = True
                break
        time.sleep(1)

    assert delivered, "Expected notification to be delivered to inbox after outbox drain"

    print("\n" + "=" * 70)
    print("🎉 ALL CHAOS & OUTAGE RECOVERY TESTS PASSED SUCCESSFULLY!")
    print("=" * 70)


if __name__ == "__main__":
    try:
        run_chaos_recovery_tests()
    except Exception as e:
        print(f"\n❌ TEST FAILED: {e}", file=sys.stderr)
        import traceback
        traceback.print_exc()
        # Guarantee container is unpaused on failure
        try:
            run_cmd(f"docker unpause {RABBITMQ_CONTAINER}")
        except Exception:
            pass
        sys.exit(1)
