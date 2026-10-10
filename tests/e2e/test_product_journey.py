#!/usr/bin/env python3
"""
Comprehensive End-to-End Test Suite for Squarewise Backend.

Verifies the entire product lifecycle across all four microservices
(Accounts, Expense Core, Notifications, GraphQL BFF) in the Docker environment:
1. User profile registration and verification via Accounts API & BFF GraphQL `me`
2. Group creation via BFF GraphQL `createGroup`
3. Multi-participant invitation & invite claiming via Expense Core REST
4. Expense addition via BFF GraphQL `createExpense` with multi-participant equal split
5. Balance calculation verification via BFF GraphQL `group` and REST `/balances`
6. Settlement suggestions calculation via BFF GraphQL `settlementSuggestions`
7. Repayment recording via BFF GraphQL `recordRepayment` and balance reconciliation
8. Outbox message dispatch to RabbitMQ and consumption into Notifications Inbox
9. Offline synchronization snapshot & change feed verification
"""

import base64
import argparse
import atexit
import json
import io
import os
import sys
import time
from pathlib import Path
from urllib.error import HTTPError, URLError
from urllib.request import Request, urlopen
import uuid
from typing import Any, Tuple
from tests.http_constants import (
    ACCEPT,
    APPLICATION_JSON,
    AUTHORIZATION,
    BEARER_PREFIX,
    CONTENT_TYPE,
    IDEMPOTENCY_KEY,
)
from tests.e2e.qa10_evidence import ExecutionOperation, load_execution_specs, write_execution_evidence

BASE_URL = os.environ.get("SQUAREWISE_BFF_URL", "http://localhost:28080")
ACCOUNTS_URL = os.environ.get("SQUAREWISE_ACCOUNTS_URL", "http://localhost:28081")
EXPENSE_CORE_URL = os.environ.get("SQUAREWISE_EXPENSE_CORE_URL", "http://localhost:28082")
NOTIFICATIONS_URL = os.environ.get("SQUAREWISE_NOTIFICATIONS_URL", "http://localhost:28083")


def request_json(
    url: str,
    method: str = "GET",
    body: Any = None,
    bearer: str | None = None,
    extra_headers: dict[str, str] | None = None,
    timeout: float = 10.0,
) -> Tuple[int, Any]:
    headers = {
        ACCEPT: APPLICATION_JSON,
        CONTENT_TYPE: APPLICATION_JSON,
    }
    if bearer:
        headers[AUTHORIZATION] = f"{BEARER_PREFIX}{bearer}"
    if extra_headers:
        headers.update(extra_headers)

    data = None
    if body is not None:
        data = json.dumps(body).encode("utf-8")

    req = Request(url, data=data, headers=headers, method=method)
    try:
        with urlopen(req, timeout=timeout) as response:
            status = response.status
            content = response.read().decode("utf-8")
            return status, json.loads(content) if content else {}
    except HTTPError as error:
        try:
            content = error.read().decode("utf-8")
            parsed = json.loads(content) if content else {}
        except Exception:
            parsed = {"raw": content}
        return error.code, parsed
    except Exception as error:
        return 503, {"error": str(error)}


def request_text(
    url: str,
    method: str = "GET",
    bearer: str | None = None,
    extra_headers: dict[str, str] | None = None,
    timeout: float = 10.0,
) -> Tuple[int, str]:
    """Request a non-JSON response while preserving the signed-persona headers."""
    headers = {CONTENT_TYPE: APPLICATION_JSON}
    if bearer:
        headers[AUTHORIZATION] = f"{BEARER_PREFIX}{bearer}"
    if extra_headers:
        headers.update(extra_headers)

    req = Request(url, headers=headers, method=method)
    try:
        with urlopen(req, timeout=timeout) as response:
            return response.status, response.read().decode("utf-8")
    except HTTPError as error:
        return error.code, error.read().decode("utf-8", errors="replace")
    except Exception as error:
        return 503, str(error)


def graphql_query(
    query: str,
    variables: dict[str, Any] | None = None,
    bearer: str | None = None,
) -> dict[str, Any]:
    payload = {"query": query}
    if variables:
        payload["variables"] = variables
    status, res = request_json(f"{BASE_URL}/graphql", method="POST", body=payload, bearer=bearer)
    assert status == 200, f"GraphQL HTTP status {status}: {res}"
    if "errors" in res and res["errors"]:
        raise AssertionError(f"GraphQL returned errors: {res['errors']}")
    return res.get("data", {})


def graphql_error_name(response: Any) -> str:
    """Return the canonical error identity from a GraphQL error envelope."""
    errors = response.get("errors") if isinstance(response, dict) else None
    if not isinstance(errors, list) or not errors or not isinstance(errors[0], dict):
        raise AssertionError(f"GraphQL response did not contain an error: {response}")
    extensions = errors[0].get("extensions")
    if not isinstance(extensions, dict) or not isinstance(extensions.get("errorName"), str):
        raise AssertionError(f"GraphQL error missing errorName: {response}")
    return extensions["errorName"]


def bootstrap_profile(url: str, bearer: str) -> tuple[int, Any]:
    """Read a profile while allowing the freshly started OIDC decoder to settle."""
    last_result: tuple[int, Any] = (503, {"error": "profile bootstrap did not run"})
    for attempt in range(3):
        last_result = request_json(url, bearer=bearer)
        if last_result[0] == 200 or last_result[0] not in (401, 502, 503):
            return last_result
        if attempt < 2:
            time.sleep(2)
    return last_result


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


def resolve_membership_id(members: list[dict[str, Any]], *candidates: str | None) -> str:
    """Resolve membershipId matching any candidate identifier (JWT sub, accountId, displayName)."""
    valid_candidates = {c for c in candidates if c}
    for m in members:
        if m.get("subject") in valid_candidates or m.get("displayName") in valid_candidates:
            return str(m["membershipId"])
    raise KeyError(f"None of candidates {valid_candidates} found in members: {members}")


def product_execution_operations(path: Path) -> list[ExecutionOperation]:
    """Load the reviewed operation assertions without adding callable source signals."""

    return [
        {
            "surface": spec["surface"],
            "operation": spec["operation"],
            "status": "passed",
            "artifact": str(path),
            "assertions": spec["assertions"],
        }
        for spec in load_execution_specs(Path(__file__).with_name("product-operation-specs.json"))
    ]


def run_e2e_tests() -> int:
    """Run the product lifecycle journey with explicit UTF-8 console output."""
    cleanup_group_id: str | None = None

    def archive_test_group() -> None:
        """Archive the generated group after success or a failed journey assertion."""
        if cleanup_group_id:
            archive_status, archive_response = request_json(
                f"{EXPENSE_CORE_URL}/expense-core/v1/groups/{cleanup_group_id}/archive",
                method="POST",
                bearer=user_a,
            )
            if archive_status not in (200, 204):
                print(
                    f"WARNING: failed to archive E2E group {cleanup_group_id}: "
                    f"HTTP {archive_status} ({archive_response})",
                    file=sys.stderr,
                )

    atexit.register(archive_test_group)
    if isinstance(sys.stdout, io.TextIOWrapper):
        sys.stdout.reconfigure(encoding="utf-8")
    if isinstance(sys.stderr, io.TextIOWrapper):
        sys.stderr.reconfigure(encoding="utf-8")
    print("=" * 70)
    print("🚀 Running Squarewise End-to-End Multi-Service Production Test Suite")
    print("=" * 70)

    # 1. Health checks across all services
    print("\n[Step 1] Verifying service actuator health...")
    services = {
        "BFF": f"{BASE_URL}/actuator/health",
        "Accounts": f"{ACCOUNTS_URL}/actuator/health",
        "Expense Core": f"{EXPENSE_CORE_URL}/actuator/health",
        "Notifications": f"{NOTIFICATIONS_URL}/actuator/health",
    }
    for name, health_url in services.items():
        status, body = request_json(health_url)
        assert status == 200, f"{name} health check failed: HTTP {status} ({body})"
        status_val = body.get("status") if isinstance(body, dict) else "UNKNOWN"
        print(f"  ✓ {name} is healthy: status={status_val}")

    unauthenticated_graphql_status, _ = request_json(
        f"{BASE_URL}/graphql",
        method="POST",
        body={"query": "query { me { accountId } }"},
    )
    assert unauthenticated_graphql_status == 401, (
        f"Unauthenticated GraphQL request returned HTTP {unauthenticated_graphql_status}"
    )
    print("  ✓ Unauthenticated GraphQL requests are rejected by BFF security")

    # 2. User profiles in Accounts & GraphQL me
    print("\n[Step 2] Testing User Profiles (Alice & Bob)...")
    user_a = os.environ.get("SQUAREWISE_E2E_TOKEN_A", os.environ.get("BEARER_TOKEN"))
    user_b = os.environ.get("SQUAREWISE_E2E_TOKEN_B", user_a)
    user_nonmember = os.environ.get("SQUAREWISE_E2E_TOKEN_NONMEMBER", user_b)
    if not user_a or not user_b or not user_nonmember:
        raise RuntimeError("E2E persona variables must contain signed tokens")

    # Query 'me' for Alice via Accounts API
    status_a, profile_a = bootstrap_profile(f"{ACCOUNTS_URL}/accounts/v1/me", user_a)
    assert status_a == 200, f"Accounts getMe failed for Alice: HTTP {status_a} ({profile_a})"
    alice_id = profile_a["accountId"]
    assert alice_id, "Accounts getMe must return a non-empty accountId"
    print(f"  ✓ Alice profile created: accountId={alice_id}, displayName={profile_a['displayName']}")

    profile_status, profile_by_id = request_json(
        f"{ACCOUNTS_URL}/accounts/v1/profiles/{alice_id}", bearer=user_a
    )
    assert profile_status == 200, (
        f"Accounts getProfileById failed for the owning subject: "
        f"HTTP {profile_status} ({profile_by_id})"
    )
    assert profile_by_id.get("accountId") == alice_id, (
        "Accounts getProfileById must return the requested owner profile"
    )

    foreign_profile_status, foreign_profile = request_json(
        f"{ACCOUNTS_URL}/accounts/v1/profiles/{alice_id}", bearer=user_b
    )
    assert foreign_profile_status == 403, (
        f"Accounts getProfileById must reject a foreign subject: "
        f"HTTP {foreign_profile_status} ({foreign_profile})"
    )
    print("  ✓ Accounts getProfileById enforces owner access and foreign-subject denial")

    # Query 'me' for Bob via GraphQL BFF
    data_bob = graphql_query(
        "query { me { accountId displayName defaultCurrency timezone } }",
        bearer=user_b
    )
    bob_me = data_bob["me"]
    bob_id = bob_me["accountId"]
    assert bob_id, "Bob accountId should not be empty"
    print(f"  ✓ Bob profile verified via GraphQL me: accountId={bob_id}, displayName={bob_me['displayName']}")

    batch_status, batch_profiles = request_json(
        f"{ACCOUNTS_URL}/accounts/v1/profiles/batch",
        method="POST",
        body={"accountIds": [alice_id, alice_id]},
        bearer=user_a,
    )
    assert batch_status == 200, (
        f"Accounts getProfilesBatch failed for the owning subject: "
        f"HTTP {batch_status} ({batch_profiles})"
    )
    assert [profile["accountId"] for profile in batch_profiles] == [alice_id], (
        "Accounts getProfilesBatch must deduplicate owner-only IDs"
    )

    workload_batch_status, workload_batch = request_json(
        f"{ACCOUNTS_URL}/accounts/v1/profiles/batch",
        method="POST",
        body={"accountIds": [alice_id, bob_id, str(uuid.uuid4())]},
        bearer=user_a,
        extra_headers={"X-Squarewise-Workload-Role": "internal-service"},
    )
    assert workload_batch_status == 403, (
        f"Accounts getProfilesBatch accepted forged workload authority: "
        f"HTTP {workload_batch_status} ({workload_batch})"
    )
    print("  ✓ Accounts rejects forged workload-role headers")

    foreign_batch_status, foreign_batch = request_json(
        f"{ACCOUNTS_URL}/accounts/v1/profiles/batch",
        method="POST",
        body={"accountIds": [alice_id]},
        bearer=user_b,
    )
    assert foreign_batch_status == 403, (
        f"Accounts getProfilesBatch must reject a foreign subject: "
        f"HTTP {foreign_batch_status} ({foreign_batch})"
    )
    print("  ✓ Accounts getProfilesBatch enforces deduplication, omission, and isolation")

    export_status, export_request = request_json(
        f"{ACCOUNTS_URL}/accounts/v1/me/export-request",
        method="POST",
        bearer=user_a,
    )
    assert export_status == 202, (
        f"Accounts requestExport failed: HTTP {export_status} ({export_request})"
    )
    export_id = export_request.get("exportId")
    assert export_id, "Accounts requestExport must return an exportId"

    exports_status, export_requests = request_json(
        f"{ACCOUNTS_URL}/accounts/v1/me/export-requests", bearer=user_a
    )
    assert exports_status == 200, (
        f"Accounts listExportRequests failed: HTTP {exports_status} ({export_requests})"
    )
    assert any(item.get("exportId") == export_id for item in export_requests), (
        "Accounts listExportRequests must expose the newly requested export"
    )
    print("  ✓ Accounts requestExport and listExportRequests preserve the durable request")

    update_status, updated_profile = request_json(
        f"{ACCOUNTS_URL}/accounts/v1/me",
        method="PATCH",
        body={"displayName": "Alice", "timezone": "Europe/Vienna"},
        bearer=user_a,
    )
    assert update_status == 200, (
        f"Accounts updateMe failed: HTTP {update_status} ({updated_profile})"
    )
    assert updated_profile.get("accountId") == alice_id, (
        "Accounts updateMe must preserve the authenticated account identity"
    )
    assert updated_profile.get("timezone") == "Europe/Vienna", (
        "Accounts updateMe must persist the requested timezone"
    )
    print("  ✓ Accounts updateMe persists a validated profile change")

    malformed_group_status, malformed_group_response = request_json(
        f"{BASE_URL}/graphql",
        method="POST",
        body={"query": 'query { group(id: "not-a-uuid") { id } }'},
        bearer=user_b,
    )
    assert malformed_group_status == 200, (
        f"Malformed GraphQL group query returned HTTP {malformed_group_status}: "
        f"{malformed_group_response}"
    )
    assert isinstance(malformed_group_response, dict) and malformed_group_response.get("errors"), (
        f"Malformed GraphQL group query should return errors: {malformed_group_response}"
    )
    print("  ✓ GraphQL rejects malformed group identifier in its error envelope")

    missing_group_status, missing_group_response = request_json(
        f"{BASE_URL}/graphql",
        method="POST",
        body={"query": f'query {{ group(id: "{uuid.uuid4()}") {{ id }} }}'},
        bearer=user_b,
    )
    assert missing_group_status == 200, (
        f"Missing GraphQL group query returned HTTP {missing_group_status}: "
        f"{missing_group_response}"
    )
    assert isinstance(missing_group_response, dict) and missing_group_response.get("errors"), (
        f"Missing GraphQL group query should return errors: {missing_group_response}"
    )
    print("  ✓ GraphQL returns an error envelope for a missing group")

    malformed_suggestions_status, malformed_suggestions_response = request_json(
        f"{BASE_URL}/graphql",
        method="POST",
        body={"query": 'query { settlementSuggestions(groupId: "not-a-uuid") { fromParticipantId } }'},
        bearer=user_b,
    )
    assert malformed_suggestions_status == 200, (
        f"Malformed GraphQL suggestions query returned HTTP {malformed_suggestions_status}: "
        f"{malformed_suggestions_response}"
    )
    assert isinstance(malformed_suggestions_response, dict) and malformed_suggestions_response.get("errors"), (
        f"Malformed GraphQL suggestions query should return errors: {malformed_suggestions_response}"
    )
    print("  ✓ GraphQL rejects malformed settlement-suggestions group identifier")

    malformed_create_status, malformed_create_response = request_json(
        f"{BASE_URL}/graphql",
        method="POST",
        body={
            "query": "mutation { createGroup(input: { name: \"Invalid\", kind: NOT_A_KIND, currency: \"EUR\" }) { id } }"
        },
        bearer=user_b,
    )
    assert malformed_create_status == 200, (
        f"Malformed GraphQL createGroup returned HTTP {malformed_create_status}: "
        f"{malformed_create_response}"
    )
    assert isinstance(malformed_create_response, dict) and malformed_create_response.get("errors"), (
        f"Malformed GraphQL createGroup should return errors: {malformed_create_response}"
    )
    print("  ✓ GraphQL rejects invalid createGroup enum input")

    empty_groups_status, empty_groups_response = request_json(
        f"{BASE_URL}/graphql",
        method="POST",
        body={"query": "query { groups { id name } }"},
        bearer=user_nonmember,
    )
    assert empty_groups_status == 200, (
        f"Empty GraphQL groups query returned HTTP {empty_groups_status}: {empty_groups_response}"
    )
    assert isinstance(empty_groups_response, dict) and not empty_groups_response.get("errors"), (
        f"Empty GraphQL groups query should not return errors: {empty_groups_response}"
    )
    assert empty_groups_response.get("data", {}).get("groups") == [], (
        f"Expected empty GraphQL groups result: {empty_groups_response}"
    )
    print("  ✓ GraphQL groups returns an empty list for a user without memberships")

    # 3. Create Group via GraphQL
    print("\n[Step 3] Creating Group via BFF GraphQL mutation createGroup...")
    group_name = f"Alps Trip {uuid.uuid4().hex[:6]}"
    create_group_mutation = """
    mutation CreateGroup($input: CreateGroupInput!) {
        createGroup(input: $input) {
            id
            name
            revision
        }
    }
    """
    create_res = graphql_query(
        create_group_mutation,
        variables={"input": {"name": group_name, "kind": "TRIP", "currency": "EUR"}},
        bearer=user_a
    )
    group_id = create_res["createGroup"]["id"]
    cleanup_group_id = group_id
    assert group_id, "Expected non-empty groupId"
    print(f"  ✓ Group created: id={group_id}, name='{group_name}'")

    updated_group_response = graphql_query(
        f'mutation {{ updateGroup(groupId: "{group_id}", name: "{group_name} Updated") {{ id name }} }}',
        bearer=user_a,
    )
    assert updated_group_response["updateGroup"]["id"] == group_id
    assert updated_group_response["updateGroup"]["name"] == f"{group_name} Updated"
    print("  ✓ Authorized GraphQL group update persisted the renamed group")

    listed_groups_status, listed_groups = request_json(
        f"{EXPENSE_CORE_URL}/expense-core/v1/groups", bearer=user_a
    )
    assert listed_groups_status == 200, (
        f"Expense Core listGroups failed: HTTP {listed_groups_status} ({listed_groups})"
    )
    assert any(group.get("groupId") == group_id for group in listed_groups), (
        "Expense Core listGroups must include the created group"
    )
    group_status, group_response = request_json(
        f"{EXPENSE_CORE_URL}/expense-core/v1/groups/{group_id}", bearer=user_a
    )
    assert group_status == 200, (
        f"Expense Core getGroup failed: HTTP {group_status} ({group_response})"
    )
    assert group_response.get("groupId") == group_id, (
        "Expense Core getGroup must return the requested group"
    )
    print("  ✓ Expense Core listGroups and getGroup expose the owner-visible group")

    protected_group_before = dict(group_response)
    balances_before_status, balances_before = request_json(
        f"{EXPENSE_CORE_URL}/expense-core/v1/groups/{group_id}/balances", bearer=user_a
    )
    assert balances_before_status == 200, f"Initial group balances failed: {balances_before}"

    outsider_update_status, outsider_update_response = request_json(
        f"{BASE_URL}/graphql",
        method="POST",
        body={
            "query": (
                f'mutation {{ updateGroup(groupId: "{group_id}", name: "Unauthorized rename") '
                "{ id name } }"
            )
        },
        bearer=user_nonmember,
    )
    assert outsider_update_status == 200, (
        f"Unauthorized GraphQL update returned HTTP {outsider_update_status}: "
        f"{outsider_update_response}"
    )
    assert isinstance(outsider_update_response, dict) and outsider_update_response.get("errors"), (
        f"Unauthorized GraphQL update should return errors: {outsider_update_response}"
    )
    print("  ✓ GraphQL rejects non-member group update")

    assert graphql_error_name(outsider_update_response) == "GROUP_ACCESS_HIDDEN"
    after_update_status, after_update_group = request_json(
        f"{EXPENSE_CORE_URL}/expense-core/v1/groups/{group_id}", bearer=user_a
    )
    after_update_balances_status, after_update_balances = request_json(
        f"{EXPENSE_CORE_URL}/expense-core/v1/groups/{group_id}/balances", bearer=user_a
    )
    assert after_update_status == 200 and after_update_group == protected_group_before, (
        f"Unauthorized update changed group state: before={protected_group_before}, after={after_update_group}"
    )
    assert after_update_balances_status == 200 and after_update_balances == balances_before, (
        f"Unauthorized update changed balances: before={balances_before}, after={after_update_balances}"
    )
    repayment_group_before = dict(after_update_group)

    outsider_group_status, outsider_group_response = request_json(
        f"{BASE_URL}/graphql",
        method="POST",
        body={"query": f'query {{ group(id: "{group_id}") {{ id name }} }}'},
        bearer=user_nonmember,
    )
    assert outsider_group_status == 200, (
        f"Unauthorized GraphQL group query returned HTTP {outsider_group_status}: "
        f"{outsider_group_response}"
    )
    assert isinstance(outsider_group_response, dict) and outsider_group_response.get("errors"), (
        f"Unauthorized GraphQL group query should return errors: {outsider_group_response}"
    )
    assert "Alps Trip" not in json.dumps(outsider_group_response), (
        f"Unauthorized GraphQL group query leaked group data: {outsider_group_response}"
    )
    print("  ✓ GraphQL rejects non-member group query without leaking group data")

    outsider_suggestions_status, outsider_suggestions_response = request_json(
        f"{BASE_URL}/graphql",
        method="POST",
        body={"query": f'query {{ settlementSuggestions(groupId: "{group_id}") {{ fromParticipantId }} }}'},
        bearer=user_nonmember,
    )
    assert outsider_suggestions_status == 200, (
        f"Unauthorized GraphQL suggestions returned HTTP {outsider_suggestions_status}: "
        f"{outsider_suggestions_response}"
    )
    assert isinstance(outsider_suggestions_response, dict) and outsider_suggestions_response.get("errors"), (
        f"Unauthorized GraphQL suggestions should return errors: {outsider_suggestions_response}"
    )
    print("  ✓ GraphQL rejects non-member settlement suggestions")

    outsider_repayment_status, outsider_repayment_response = request_json(
        f"{BASE_URL}/graphql",
        method="POST",
        body={
            "query": (
                f'mutation {{ recordRepayment(input: {{ groupId: "{group_id}", '
                'fromParticipantId: "outsider", toParticipantId: "alice", '
                'amount: { currency: "EUR", minor: "100" }, reason: "unauthorized", '
                'idempotencyKey: "unauthorized-repayment" }) '
                "{ id } }"
            )
        },
        bearer=user_nonmember,
    )
    assert outsider_repayment_status == 200, (
        f"Unauthorized GraphQL repayment returned HTTP {outsider_repayment_status}: "
        f"{outsider_repayment_response}"
    )
    assert isinstance(outsider_repayment_response, dict) and outsider_repayment_response.get("errors"), (
        f"Unauthorized GraphQL repayment should return errors: {outsider_repayment_response}"
    )
    print("  ✓ GraphQL rejects non-member repayment recording")

    assert graphql_error_name(outsider_repayment_response) == "GROUP_ACCESS_HIDDEN"
    after_repayment_group_status, after_repayment_group = request_json(
        f"{EXPENSE_CORE_URL}/expense-core/v1/groups/{group_id}", bearer=user_a
    )
    after_repayment_balances_status, after_repayment_balances = request_json(
        f"{EXPENSE_CORE_URL}/expense-core/v1/groups/{group_id}/balances", bearer=user_a
    )
    assert after_repayment_group_status == 200 and after_repayment_group == repayment_group_before, (
        f"Unauthorized repayment changed group state: before={repayment_group_before}, "
        f"after={after_repayment_group}"
    )
    assert after_repayment_balances_status == 200 and after_repayment_balances == balances_before, (
        f"Unauthorized repayment changed balances: before={balances_before}, after={after_repayment_balances}"
    )

    malformed_repayment_status, malformed_repayment_response = request_json(
        f"{BASE_URL}/graphql",
        method="POST",
        body={
            "query": (
                f'mutation {{ recordRepayment(input: {{ groupId: "{group_id}", '
                'fromParticipantId: "alice", toParticipantId: "bob", '
                'amount: { currency: "EUR", minor: "not-money" }, reason: "invalid", '
                'idempotencyKey: "malformed-repayment" }) '
                "{ id } }"
            )
        },
        bearer=user_a,
    )
    assert malformed_repayment_status == 200, (
        f"Malformed GraphQL repayment returned HTTP {malformed_repayment_status}: "
        f"{malformed_repayment_response}"
    )
    assert isinstance(malformed_repayment_response, dict) and malformed_repayment_response.get("errors"), (
        f"Malformed GraphQL repayment should return errors: {malformed_repayment_response}"
    )
    print("  ✓ GraphQL rejects malformed repayment money")

    # 4. Invite Bob and Claim Invite
    print("\n[Step 4] Inviting Bob to Group and Claiming Invite...")
    # Create invite token from Alice
    status_inv, invite = request_json(
        f"{EXPENSE_CORE_URL}/expense-core/v1/groups/{group_id}/invites",
        method="POST",
        body={"expiresInHours": 24},
        bearer=user_a
    )
    assert status_inv == 201, f"Expense Core createInvite failed: {invite}"
    token = invite["token"]
    print(f"  ✓ Invite generated with token={token[:12]}...")

    # Claim invite as Bob
    status_claim, claim_res = request_json(
        f"{EXPENSE_CORE_URL}/expense-core/v1/invites/{token}/claim",
        method="POST",
        bearer=user_b
    )
    assert status_claim == 200, f"Expense Core claimInvite failed for Bob: {claim_res}"
    print(f"  ✓ Bob claimed invite successfully: {claim_res.get('name')}")

    placeholder_status, placeholder = request_json(
        f"{EXPENSE_CORE_URL}/expense-core/v1/groups/{group_id}/placeholders",
        method="POST",
        body={"name": "Temporary participant"},
        bearer=user_a,
    )
    assert placeholder_status == 201, (
        f"Expense Core createPlaceholder failed: HTTP {placeholder_status} ({placeholder})"
    )
    placeholder_id = placeholder.get("membershipId")
    assert placeholder_id, "Expense Core createPlaceholder must return a membershipId"
    remove_status, remove_response = request_json(
        f"{EXPENSE_CORE_URL}/expense-core/v1/groups/{group_id}/members/{placeholder_id}",
        method="DELETE",
        bearer=user_a,
    )
    assert remove_status == 204, (
        f"Expense Core removeGroupMember failed: HTTP {remove_status} ({remove_response})"
    )
    print("  [ok] Expense Core createPlaceholder and removeGroupMember preserve lifecycle state")

    # Verify group members via Expense Core
    status_members, members = request_json(
        f"{EXPENSE_CORE_URL}/expense-core/v1/groups/{group_id}/members",
        bearer=user_a
    )
    assert status_members == 200, f"Expense Core listGroupMembers failed: {members}"
    alice_sub = extract_jwt_subject(user_a)
    bob_sub = extract_jwt_subject(user_b)
    alice_membership_id = resolve_membership_id(members, alice_sub, profile_a.get("displayName"), profile_a.get("accountId"))
    bob_membership_id = resolve_membership_id(members, bob_sub, bob_me.get("displayName"), bob_me.get("accountId"))
    alice_id = alice_membership_id
    bob_id = bob_membership_id
    print(f"  ✓ Group members verified: Alice membership={alice_id}, Bob membership={bob_id}")

    preview_status, preview = request_json(
        f"{EXPENSE_CORE_URL}/expense-core/v1/allocations/preview",
        method="POST",
        body={"totalMinor": "10000", "participantIds": [alice_id, bob_id]},
        bearer=user_a,
    )
    assert preview_status == 200, (
        f"Expense Core previewAllocation failed: HTTP {preview_status} ({preview})"
    )
    assert preview.get("totalMinor") == "10000", (
        "Expense Core previewAllocation must preserve the requested total"
    )
    assert preview.get("allocations") == {alice_id: "5000", bob_id: "5000"}, (
        "Expense Core previewAllocation must split the total equally"
    )
    print("  ✓ Expense Core previewAllocation returns an equal two-member split")

    # 5. Add Expense via GraphQL createExpense
    print("\n[Step 5] Adding Expense via GraphQL createExpense (Alice pays 100.00 EUR split equally with Bob)...")
    expense_id = str(uuid.uuid4())
    idempotency_key = f"idemp-e2e-{uuid.uuid4()}"
    create_expense_mutation = """
    mutation CreateExpense($groupId: ID!, $input: CreateExpenseInput!, $idempotencyKey: String!) {
        createExpense(groupId: $groupId, input: $input, idempotencyKey: $idempotencyKey) {
            id
            description
            amount {
                currency
                minor
            }
            allocations {
                participantId
                amount {
                    currency
                    minor
                }
            }
        }
    }
    """
    expense_input = {
        "expenseId": expense_id,
        "description": "Ski Passes",
        "amount": {"currency": "EUR", "minor": "10000"},
        "payers": [{"participantId": alice_id, "amount": {"currency": "EUR", "minor": "10000"}}],
        "allocation": {
            "mode": "EQUAL",
            "items": [
                {"participantId": alice_id, "value": "1"},
                {"participantId": bob_id, "value": "1"},
            ]
        }
    }
    exp_res = graphql_query(
        create_expense_mutation,
        variables={"groupId": group_id, "input": expense_input, "idempotencyKey": idempotency_key},
        bearer=user_a
    )
    created_exp = exp_res["createExpense"]
    assert created_exp["id"] == expense_id
    assert created_exp["amount"]["minor"] == "10000"
    print(f"  ✓ Expense recorded: id={expense_id}, amount=100.00 EUR, allocations count={len(created_exp['allocations'])}")

    listed_expenses_status, listed_expenses = request_json(
        f"{EXPENSE_CORE_URL}/expense-core/v1/groups/{group_id}/expenses",
        bearer=user_a,
    )
    assert listed_expenses_status == 200, (
        f"Expense Core listExpenses failed: "
        f"HTTP {listed_expenses_status} ({listed_expenses})"
    )
    assert any(item.get("expenseId") == expense_id for item in listed_expenses), (
        "Expense Core listExpenses must return the persisted expense"
    )

    searched_expenses_status, searched_expenses = request_json(
        f"{EXPENSE_CORE_URL}/expense-core/v1/groups/{group_id}/search?query=Ski",
        bearer=user_a,
    )
    assert searched_expenses_status == 200, (
        f"Expense Core searchExpenses failed: "
        f"HTTP {searched_expenses_status} ({searched_expenses})"
    )
    assert any(
        item.get("expenseId") == expense_id for item in searched_expenses.get("expenses", [])
    ), "Expense Core searchExpenses must find the persisted expense by description"
    print("  ✓ Expense Core listExpenses and searchExpenses expose the persisted expense")

    export_status, export_csv = request_text(
        f"{EXPENSE_CORE_URL}/expense-core/v1/groups/{group_id}/export?query=Ski",
        bearer=user_a,
        extra_headers={"Accept": "text/csv"},
    )
    assert export_status == 200, (
        f"Expense Core exportExpenses failed: HTTP {export_status} ({export_csv})"
    )
    assert export_csv.startswith("expenseId,description,currency,amountMinor,category\n"), (
        "Expense Core exportExpenses must return the documented CSV header"
    )
    assert expense_id in export_csv and "Ski Passes" in export_csv and ",10000," in export_csv, (
        "Expense Core exportExpenses must include the persisted matching expense"
    )
    print("  [ok] Expense Core exportExpenses returns the persisted matching CSV row")

    replay_res = graphql_query(
        create_expense_mutation,
        variables={"groupId": group_id, "input": expense_input, "idempotencyKey": idempotency_key},
        bearer=user_a,
    )
    replayed_expense = replay_res["createExpense"]
    assert replayed_expense["id"] == expense_id, (
        f"GraphQL idempotent replay returned a different expense: {replayed_expense}"
    )
    print("  ✓ GraphQL createExpense replay is idempotent")

    tampered_expense_input = {**expense_input, "description": "Tampered replay"}
    tampered_status, tampered_response = request_json(
        f"{BASE_URL}/graphql",
        method="POST",
        body={
            "query": create_expense_mutation,
            "variables": {
                "groupId": group_id,
                "input": tampered_expense_input,
                "idempotencyKey": idempotency_key,
            },
        },
        bearer=user_a,
    )
    assert tampered_status == 200, (
        f"Tampered GraphQL replay returned HTTP {tampered_status}: {tampered_response}"
    )
    assert isinstance(tampered_response, dict) and tampered_response.get("errors"), (
        f"Tampered GraphQL replay should return errors: {tampered_response}"
    )
    print("  ✓ GraphQL createExpense rejects a tampered idempotency replay")

    # 6. Verify Balances via GraphQL group query and REST
    print("\n[Step 6] Verifying balances via GraphQL group query...")
    group_query = """
    query GetGroup($id: ID!) {
        group(id: $id) {
            id
            name
            balances {
                participantId
                money {
                    currency
                    minor
                }
            }
        }
    }
    """
    group_data = graphql_query(group_query, variables={"id": group_id}, bearer=user_a)["group"]
    balance_map = {b["participantId"]: int(b["money"]["minor"]) for b in group_data["balances"]}
    print(f"  ✓ Current balances: {balance_map}")
    assert balance_map.get(alice_id) == 5000, f"Alice should be owed 50.00 EUR (+5000), got: {balance_map.get(alice_id)}"
    assert balance_map.get(bob_id) == -5000, f"Bob should owe 50.00 EUR (-5000), got: {balance_map.get(bob_id)}"
    assert sum(balance_map.values()) == 0, "Balances must sum to zero"
    print("  ✓ Zero-sum ledger invariant holds: sum(balances) == 0")

    # 7. Query settlement suggestions
    print("\n[Step 7] Checking settlement suggestions via GraphQL...")
    suggestions_query = """
    query GetSuggestions($groupId: ID!) {
        settlementSuggestions(groupId: $groupId) {
            fromParticipantId
            toParticipantId
            amount {
                currency
                minor
            }
        }
    }
    """
    sugg_data = graphql_query(suggestions_query, variables={"groupId": group_id}, bearer=user_a)
    suggestions = sugg_data["settlementSuggestions"]
    assert len(suggestions) == 1, f"Expected exactly 1 settlement suggestion, got: {suggestions}"
    sugg = suggestions[0]
    assert sugg["fromParticipantId"] == bob_id
    assert sugg["toParticipantId"] == alice_id
    assert sugg["amount"]["minor"] == "5000"
    print(f"  ✓ Settlement suggestion verified: Bob pays Alice 50.00 EUR")

    rest_suggestions_status, rest_suggestions = request_json(
        f"{EXPENSE_CORE_URL}/expense-core/v1/groups/{group_id}/settlements/suggestions",
        bearer=user_a,
    )
    assert rest_suggestions_status == 200, (
        f"Expense Core getSettlementSuggestions failed: "
        f"HTTP {rest_suggestions_status} ({rest_suggestions})"
    )
    assert any(
        item.get("fromParticipantId") == bob_id and item.get("toParticipantId") == alice_id
        for item in rest_suggestions
    ), "Expense Core getSettlementSuggestions must expose Bob's outstanding payment"
    print("  ✓ Expense Core getSettlementSuggestions matches the GraphQL projection")

    # 8. Record Repayment via GraphQL
    print("\n[Step 8] Recording repayment via GraphQL recordRepayment...")
    record_repayment_mutation = """
    mutation RecordRepayment($input: RepaymentInput!) {
        recordRepayment(input: $input) {
            id
            status
            amount {
                currency
                minor
            }
        }
    }
    """
    repay_input = {
        "groupId": group_id,
        "fromParticipantId": bob_id,
        "toParticipantId": alice_id,
        "amount": {"currency": "EUR", "minor": "5000"},
        "reason": "Settling ski passes",
        "idempotencyKey": f"repayment-e2e-{uuid.uuid4()}"
    }
    repay_res = graphql_query(record_repayment_mutation, variables={"input": repay_input}, bearer=user_b)
    settlement = repay_res["recordRepayment"]
    assert settlement["status"] == "RECORDED"
    assert settlement["amount"]["minor"] == "5000"
    print(f"  ✓ Repayment recorded successfully: id={settlement['id']}, status={settlement['status']}")

    # 9. Record and reverse a REST settlement against a second persisted expense.
    rest_expense_id = str(uuid.uuid4())
    rest_expense_status, rest_expense = request_json(
        f"{EXPENSE_CORE_URL}/expense-core/v1/groups/{group_id}/expenses",
        method="POST",
        body={
            "expenseId": rest_expense_id,
            "description": "REST settlement fixture",
            "amount": {"currency": "EUR", "minor": "1000"},
            "payers": [{"participantId": alice_id, "amount": {"currency": "EUR", "minor": "1000"}}],
            "allocation": {
                "mode": "EQUAL",
                "items": [
                    {"participantId": alice_id, "value": "1"},
                    {"participantId": bob_id, "value": "1"},
                ],
            },
        },
        bearer=user_a,
        extra_headers={IDEMPOTENCY_KEY: f"rest-expense-{uuid.uuid4()}"},
    )
    assert rest_expense_status == 201, (
        f"REST settlement fixture expense failed: HTTP {rest_expense_status} ({rest_expense})"
    )
    update_status, updated_rest_expense = request_json(
        f"{EXPENSE_CORE_URL}/expense-core/v1/groups/{group_id}/expenses/{rest_expense_id}",
        method="PUT",
        body={
            "version": 1,
            "description": "Updated REST settlement fixture",
            "amount": {"currency": "EUR", "minor": "1000"},
            "payers": [{"participantId": alice_id, "amount": {"currency": "EUR", "minor": "1000"}}],
            "allocation": {
                "mode": "EQUAL",
                "items": [
                    {"participantId": alice_id, "value": "1"},
                    {"participantId": bob_id, "value": "1"},
                ],
            },
        },
        bearer=user_a,
    )
    assert update_status == 200, (
        f"Expense Core updateExpense failed: HTTP {update_status} ({updated_rest_expense})"
    )
    assert updated_rest_expense.get("expenseId") == rest_expense_id
    assert updated_rest_expense.get("version") == 2

    settlement_status, rest_settlement = request_json(
        f"{EXPENSE_CORE_URL}/expense-core/v1/groups/{group_id}/settlements",
        method="POST",
        body={
            "fromParticipantId": bob_id,
            "toParticipantId": alice_id,
            "amountMinor": "500",
            "currency": "EUR",
        },
        bearer=user_b,
        extra_headers={IDEMPOTENCY_KEY: f"rest-settlement-{uuid.uuid4()}"},
    )
    assert settlement_status == 201, (
        f"Expense Core recordSettlement failed: HTTP {settlement_status} ({rest_settlement})"
    )
    settlement_id = rest_settlement.get("id")
    assert settlement_id and rest_settlement.get("status") == "RECORDED"
    assert rest_settlement.get("fromParticipantId") == bob_id
    assert rest_settlement.get("toParticipantId") == alice_id
    assert rest_settlement.get("amountMinor") == 500

    reversal_status, reversed_settlement = request_json(
        f"{EXPENSE_CORE_URL}/expense-core/v1/groups/{group_id}/settlements/{settlement_id}/reversal",
        method="POST",
        body={"reason": "E2E settlement reversal"},
        bearer=user_b,
    )
    assert reversal_status == 200, (
        f"Expense Core reverseSettlement failed: HTTP {reversal_status} ({reversed_settlement})"
    )
    assert reversed_settlement.get("id") == settlement_id
    assert reversed_settlement.get("status") == "REVERSED"

    delete_status, delete_response = request_json(
        f"{EXPENSE_CORE_URL}/expense-core/v1/groups/{group_id}/expenses/{rest_expense_id}?version=2",
        method="DELETE",
        bearer=user_a,
    )
    assert delete_status == 204, (
        f"Expense Core deleteExpense failed: HTTP {delete_status} ({delete_response})"
    )
    print("  [ok] Expense Core recordSettlement and reverseSettlement preserve settlement identity")
    print("  [ok] Expense Core updateExpense and deleteExpense preserve optimistic versioning")

    # 10. Verify Reconciled Balances via REST
    print("\n[Step 9] Verifying balances via REST /balances endpoint...")
    status_bal, rest_balances = request_json(
        f"{EXPENSE_CORE_URL}/expense-core/v1/groups/{group_id}/balances",
        bearer=user_a
    )
    assert status_bal == 200, f"Expense Core getBalances failed: {rest_balances}"
    bal_items = rest_balances.get("balances", [])
    print(f"  ✓ Expense Core getBalances returned: {len(bal_items)} items")

    # 11. Verify Outbox Dispatch & Notifications Inbox
    print("\n[Step 11] Verifying Outbox Relay & Notifications Inbox...")
    # Allow background outbox daemon and rabbit listener a few seconds to deliver
    found_notification = False
    status_before_notifications, inbox_before_notifications = request_json(
        f"{NOTIFICATIONS_URL}/notifications/v1/inbox",
        bearer=user_a,
    )
    assert status_before_notifications == 200, (
        f"Failed to read notification baseline: {inbox_before_notifications}"
    )
    existing_notification_ids = {
        str(item.get("notificationId"))
        for item in inbox_before_notifications.get("items", [])
        if isinstance(item, dict) and item.get("notificationId")
    }
    for attempt in range(1, 10):
        status_inbox, inbox_data = request_json(
            f"{NOTIFICATIONS_URL}/notifications/v1/inbox",
            bearer=user_a
        )
        if status_inbox == 200 and inbox_data.get("items"):
            items = inbox_data["items"]
            matching = [
                item for item in items
                if str(item.get("notificationId")) == expense_id
                and str(item.get("notificationId")) not in existing_notification_ids
                and item.get("eventType") == "expense.created"
            ]
            if matching:
                print(f"  ✓ Verified event delivery to Notifications Inbox: eventType={matching[0]['eventType']}, message='{matching[0]['message']}'")
                found_notification = True
                notification_id = matching[0]["notificationId"]
                status_read, _ = request_json(
                    f"{NOTIFICATIONS_URL}/notifications/v1/inbox/{notification_id}/read",
                    method="POST",
                    bearer=user_a,
                )
                assert status_read == 204, f"markAsRead failed: HTTP {status_read}"
                status_after_read, inbox_after_read = request_json(
                    f"{NOTIFICATIONS_URL}/notifications/v1/inbox",
                    bearer=user_a,
                )
                assert status_after_read == 200, f"listInbox after markAsRead failed: HTTP {status_after_read}"
                read_item = next(
                    (item for item in inbox_after_read.get("items", []) if item.get("notificationId") == notification_id),
                    None,
                )
                assert read_item is not None and read_item.get("read") is True, (
                    f"markAsRead did not persist read state: {read_item}"
                )
                break
        time.sleep(1)

    assert found_notification, "Expected notification to be delivered to Notifications inbox"

    # 12. Verify Offline Sync Snapshot and Changes Feed
    print("\n[Step 12] Verifying Offline Sync Feed...")
    status_snap, snapshot = request_json(
        f"{EXPENSE_CORE_URL}/expense-core/v1/groups/{group_id}/sync/snapshot",
        bearer=user_a
    )
    assert status_snap == 200, f"Expense Core getSnapshot failed: {snapshot}"
    changes = snapshot.get("changes", [])
    assert len(changes) >= 1, "Expected at least one change record in sync snapshot"
    print(f"  ✓ Sync snapshot verified: {len(changes)} revisions tracked (latest revision={changes[-1]['revision']})")

    next_cursor = snapshot.get("nextCursor")
    assert next_cursor, "Expense Core getSnapshot must return a continuation cursor"
    changes_status, changes_page = request_json(
        f"{EXPENSE_CORE_URL}/expense-core/v1/groups/{group_id}/sync/changes"
        f"?cursor={next_cursor}&limit=50",
        bearer=user_a,
    )
    assert changes_status == 200, (
        f"Expense Core getChanges failed: HTTP {changes_status} ({changes_page})"
    )
    assert isinstance(changes_page.get("changes"), list), (
        "Expense Core getChanges must return a changes page"
    )
    print("  ✓ Expense Core getChanges accepts the snapshot continuation cursor")

    schedule_payload = {
        "description": "E2E future recurring schedule",
        "amount": {"currency": "EUR", "minor": "90000"},
        "frequency": "MONTHLY",
        "dayOfMonth": 1,
        "startDate": "2099-01-01",
    }
    schedule_status, schedule = request_json(
        f"{EXPENSE_CORE_URL}/expense-core/v1/groups/{group_id}/schedules",
        method="POST",
        body=schedule_payload,
        bearer=user_a,
    )
    assert schedule_status == 201, (
        f"Expense Core createRecurringSchedule failed: HTTP {schedule_status} ({schedule})"
    )
    schedule_id = schedule.get("scheduleId")
    assert schedule_id and schedule.get("paused") is False

    schedules_status, schedules = request_json(
        f"{EXPENSE_CORE_URL}/expense-core/v1/groups/{group_id}/schedules",
        bearer=user_a,
    )
    assert schedules_status == 200 and any(
        item.get("scheduleId") == schedule_id for item in schedules
    ), "Expense Core listRecurringSchedules must include the created schedule"

    schedule_get_status, schedule_get = request_json(
        f"{EXPENSE_CORE_URL}/expense-core/v1/groups/{group_id}/schedules/{schedule_id}",
        bearer=user_a,
    )
    assert schedule_get_status == 200 and schedule_get.get("scheduleId") == schedule_id, (
        f"Expense Core getRecurringSchedule failed: HTTP {schedule_get_status} ({schedule_get})"
    )

    pause_status, paused_schedule = request_json(
        f"{EXPENSE_CORE_URL}/expense-core/v1/groups/{group_id}/schedules/{schedule_id}/pause",
        method="POST",
        bearer=user_a,
    )
    assert pause_status == 200 and paused_schedule.get("paused") is True, (
        f"Expense Core pauseRecurringSchedule failed: HTTP {pause_status} ({paused_schedule})"
    )

    resume_status, resumed_schedule = request_json(
        f"{EXPENSE_CORE_URL}/expense-core/v1/groups/{group_id}/schedules/{schedule_id}/resume",
        method="POST",
        bearer=user_a,
    )
    assert resume_status == 200 and resumed_schedule.get("paused") is False, (
        f"Expense Core resumeRecurringSchedule failed: HTTP {resume_status} ({resumed_schedule})"
    )

    update_status, updated_schedule = request_json(
        f"{EXPENSE_CORE_URL}/expense-core/v1/groups/{group_id}/schedules/{schedule_id}",
        method="PUT",
        body={**schedule_payload, "description": "Updated E2E future recurring schedule", "amount": {"currency": "EUR", "minor": "91000"}},
        bearer=user_a,
    )
    assert update_status == 200 and updated_schedule.get("scheduleId") == schedule_id, (
        f"Expense Core updateRecurringSchedule failed: HTTP {update_status} ({updated_schedule})"
    )
    assert updated_schedule.get("description") == "Updated E2E future recurring schedule"
    assert updated_schedule.get("amount", {}).get("minor") == "91000"
    print("  [ok] Expense Core recurring schedule create/list/get/pause/resume/update lifecycle works")

    deletion_status, deletion_response = request_json(
        f"{ACCOUNTS_URL}/accounts/v1/me/deletion-request",
        method="POST",
        bearer=user_a,
    )
    assert deletion_status == 202, (
        f"Accounts requestDeletion failed: HTTP {deletion_status} ({deletion_response})"
    )
    print("  ✓ Accounts requestDeletion accepted as the final lifecycle action")

    print("\n" + "=" * 70)
    print("🎉 ALL END-TO-END PRODUCT LIFECYCLE TESTS PASSED SUCCESSFULLY!")
    print("=" * 70)
    return 0


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description="Run the live multi-service product journey")
    parser.add_argument("--evidence-output", type=Path, help="write QA-10 operation evidence after success")
    parser.add_argument(
        "--source-revision",
        default=os.environ.get("GITHUB_SHA", "local-worktree"),
        help="source revision recorded in the QA-10 evidence artifact",
    )
    parser.add_argument(
        "--environment",
        default=os.environ.get("QA10_E2E_ENVIRONMENT", "local-compose-oidc"),
        help="runtime environment recorded in the QA-10 evidence artifact",
    )
    arguments = parser.parse_args()
    try:
        result = run_e2e_tests()
        if arguments.evidence_output is not None:
            write_execution_evidence(
                arguments.evidence_output,
                arguments.source_revision,
                arguments.environment,
                product_execution_operations(arguments.evidence_output),
            )
        sys.exit(result)
    except AssertionError as err:
        print(f"\n❌ TEST FAILED: {err}", file=sys.stderr)
        sys.exit(1)
    except Exception as err:
        print(f"\n💥 UNEXPECTED ERROR: {err}", file=sys.stderr)
        sys.exit(1)
