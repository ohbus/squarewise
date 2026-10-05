#!/usr/bin/env python3
"""
E2E Concurrency & Real-Time GraphQL Subscription Invalidation Test Suite

Verifies:
1. GraphQL Real-time WebSocket subscriptions:
   - Client establishes WebSocket connection to BFF (/graphql) using 'graphql-transport-ws'.
   - Client initiates 'subscription { groupChanged(groupId: ID!) { groupId revision changeId } }'.
   - Mutations performed by another user trigger live invalidation events delivered over WS.
2. Concurrent Member Edit Conflict Resolution:
   - Alice and Bob attempt concurrent update/patch of the same expense version.
   - Race condition execution: exactly one succeeds (version increments to 2), and the
     competing update receives HTTP 409 Conflict with stale version error.
   - Stale client re-reads latest version and re-applies changes smoothly.
"""

import base64
import argparse
import json
import io
import os
from pathlib import Path
import socket
import struct
import sys
import threading
import time
from urllib.error import HTTPError, URLError
from urllib.request import Request, urlopen
import uuid
from tests.http_constants import APPLICATION_JSON, AUTHORIZATION, BEARER_PREFIX, CONTENT_TYPE, GRAPHQL_PATH
from concurrent.futures import ThreadPoolExecutor
from typing import Any
from tests.e2e.qa10_evidence import write_execution_evidence

BFF_URL = os.environ.get("SQUAREWISE_BFF_URL", "http://localhost:28080")
ACCOUNTS_URL = os.environ.get("SQUAREWISE_ACCOUNTS_URL", "http://localhost:28081")
EXPENSE_CORE_URL = os.environ.get("SQUAREWISE_EXPENSE_CORE_URL", "http://localhost:28082")
HTTP_TIMEOUT_SECONDS = 10
SOCKET_SETUP_TIMEOUT_SECONDS = 10
WS_HOST = "localhost"
WS_PORT = int(os.environ.get("SQUAREWISE_BFF_PORT", "28080"))




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
        with urlopen(req, timeout=HTTP_TIMEOUT_SECONDS) as resp:
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


class SimpleGraphQLWSClient:
    """Minimal, self-contained client for RFC 6455 WebSockets + graphql-transport-ws protocol."""

    def __init__(self, host: str, port: int, path: str, token: str) -> None:
        self.host = host
        self.port = port
        self.path = path
        self.token = token
        self.sock: socket.socket | None = None
        self.events: list[dict[str, Any]] = []
        self.errors: list[dict[str, Any]] = []
        self.messages: list[dict[str, Any]] = []
        self.running = False
        self._listener_thread: threading.Thread | None = None

    def connect(self) -> None:
        """Open the socket and retry transient startup races before subscribing."""
        for attempt in range(3):
            try:
                self._connect_once()
                return
            except (TimeoutError, ConnectionResetError, ConnectionError):
                self.close()
                if attempt == 2:
                    raise
                time.sleep(1)

    def _connect_once(self) -> None:
        """Perform one WebSocket handshake and GraphQL connection acknowledgement."""
        self.sock = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
        self.sock.settimeout(SOCKET_SETUP_TIMEOUT_SECONDS)
        self.sock.connect((self.host, self.port))
        key = base64.b64encode(os.urandom(16)).decode()
        handshake = (
            f"GET {self.path} HTTP/1.1\r\n"
            f"Host: {self.host}:{self.port}\r\n"
            "Upgrade: websocket\r\n"
            "Connection: Upgrade\r\n"
            f"Authorization: Bearer {self.token}\r\n"
            f"Sec-WebSocket-Key: {key}\r\n"
            "Sec-WebSocket-Version: 13\r\n"
            "Sec-WebSocket-Protocol: graphql-transport-ws\r\n\r\n"
        )
        self.sock.sendall(handshake.encode("utf-8"))
        res = self.sock.recv(4096).decode("utf-8", errors="replace")
        if "101 Switching Protocols" not in res:
            raise ConnectionError(f"WebSocket handshake failed:\n{res}")

        # Connection Init
        self._send_frame(json.dumps({"type": "connection_init"}))
        ack = self._recv_frame()
        ack_data = json.loads(ack)
        if ack_data.get("type") != "connection_ack":
            raise ConnectionError(f"Expected connection_ack, got: {ack}")

        self.sock.settimeout(None)
        self.running = True
        self._listener_thread = threading.Thread(target=self._listen_loop, daemon=True)
        self._listener_thread.start()

    def subscribe(self, sub_id: str, query: str, variables: dict[str, Any] | None = None) -> None:
        msg = {
            "id": sub_id,
            "type": "subscribe",
            "payload": {
                "query": query,
                "variables": variables or {}
            }
        }
        self._send_frame(json.dumps(msg))

    def complete(self, sub_id: str) -> None:
        """Stop one GraphQL subscription without closing the WebSocket."""
        self._send_frame(json.dumps({"id": sub_id, "type": "complete"}))

    def _send_frame(self, payload_str: str) -> None:
        data = payload_str.encode("utf-8")
        length = len(data)
        mask_key = os.urandom(4)
        header = bytearray([0x81])  # FIN + opcode 1 (text)
        if length < 126:
            header.append(0x80 | length)
        elif length < 65536:
            header.append(0x80 | 126)
            header.extend(struct.pack("!H", length))
        else:
            header.append(0x80 | 127)
            header.extend(struct.pack("!Q", length))
        header.extend(mask_key)
        masked_data = bytearray(b ^ mask_key[i % 4] for i, b in enumerate(data))
        sock = self.sock
        if sock is None:
            raise ConnectionError("WebSocket is not connected")
        sock.sendall(header + masked_data)

    def _recv_frame(self) -> str:
        head = self._recv_exact(2)
        b1, b2 = head[0], head[1]
        masked = bool(b2 & 0x80)
        length = b2 & 0x7F
        if length == 126:
            length = struct.unpack("!H", self._recv_exact(2))[0]
        elif length == 127:
            length = struct.unpack("!Q", self._recv_exact(8))[0]
        if masked:
            mask_key = self._recv_exact(4)
            raw = self._recv_exact(length)
            return bytes(b ^ mask_key[i % 4] for i, b in enumerate(raw)).decode("utf-8")
        else:
            return self._recv_exact(length).decode("utf-8")

    def _recv_exact(self, num_bytes: int) -> bytes:
        buf = bytearray()
        while len(buf) < num_bytes:
            sock = self.sock
            if sock is None:
                raise ConnectionError("WebSocket is not connected")
            chunk = sock.recv(num_bytes - len(buf))
            if not chunk:
                raise ConnectionError("Socket closed while reading frame")
            buf.extend(chunk)
        return bytes(buf)

    def _listen_loop(self) -> None:
        while self.running:
            try:
                frame_text = self._recv_frame()
                msg = json.loads(frame_text)
                self.messages.append(msg)
                if msg.get("type") == "next":
                    payload = msg.get("payload", {})
                    self.events.append(payload)
                    if payload.get("errors"):
                        self.errors.append(payload)
                elif msg.get("type") == "error":
                    self.errors.append(msg.get("payload", {}))
                elif msg.get("type") == "ping":
                    self._send_frame(json.dumps({"type": "pong"}))
            except Exception:
                break

    def close(self) -> None:
        self.running = False
        if self.sock:
            try:
                self.sock.close()
            except Exception:
                pass


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


def run_concurrency_and_subscriptions_test() -> None:
    """Run concurrency and subscription checks with explicit UTF-8 output."""
    if isinstance(sys.stdout, io.TextIOWrapper):
        sys.stdout.reconfigure(encoding="utf-8")
    if isinstance(sys.stderr, io.TextIOWrapper):
        sys.stderr.reconfigure(encoding="utf-8")
    print("=" * 70)
    print("⚡ Running Concurrent Member Conflicts & GraphQL Subscriptions Test")
    print("=" * 70)

    # Step 1: Provision Users and Group
    print("\n[Step 1] Provisioning test users and active group...")
    user_a = os.environ.get("SQUAREWISE_E2E_TOKEN_A", os.environ.get("BEARER_TOKEN"))
    user_b = os.environ.get("SQUAREWISE_E2E_TOKEN_B", user_a)
    user_nonmember = os.environ.get("SQUAREWISE_E2E_TOKEN_NONMEMBER", user_b)
    if not user_a or not user_b or not user_nonmember:
        raise RuntimeError("E2E persona variables must contain signed tokens")

    status, profile_a = request_json(f"{ACCOUNTS_URL}/accounts/v1/me", bearer=user_a)
    assert status == 200, f"Failed to get profile for Alice: {profile_a}"
    alice_id = profile_a["accountId"]

    status, profile_b = request_json(f"{ACCOUNTS_URL}/accounts/v1/me", bearer=user_b)
    assert status == 200, f"Failed to get profile for Bob: {profile_b}"
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
        variables={"input": {"name": "Concurrency Cabin", "kind": "TRIP", "currency": "EUR"}},
        bearer=user_a
    )
    assert status == 200 and "data" in group_res, f"Group creation failed: {group_res}"
    group_id = group_res["data"]["createGroup"]["id"]
    print(f"  ✓ Group created: {group_id}")

    # Add Bob to group via invite claim
    status, invite = request_json(f"{EXPENSE_CORE_URL}/expense-core/v1/groups/{group_id}/invites", method="POST", body={"expiresInHours": 24}, bearer=user_a)
    assert status == 201, f"Failed to create invite: {invite}"
    status, claim = request_json(f"{EXPENSE_CORE_URL}/expense-core/v1/invites/{invite['token']}/claim", method="POST", bearer=user_b)
    assert status == 200, f"Bob failed to claim invite: {claim}"
    status, members = request_json(f"{EXPENSE_CORE_URL}/expense-core/v1/groups/{group_id}/members", bearer=user_a)
    assert status == 200, f"Failed to list group members: {members}"
    alice_sub = extract_jwt_subject(user_a)
    bob_sub = extract_jwt_subject(user_b)
    alice_id = resolve_membership_id(members, alice_sub, profile_a.get("displayName"), profile_a.get("accountId"))
    bob_id = resolve_membership_id(members, bob_sub, profile_b.get("displayName"), profile_b.get("accountId"))
    print(f"  ✓ Members active: Alice={alice_id}, Bob={bob_id}")

    # Step 2: Establish Real-time GraphQL WebSocket Subscription (groupChanged)
    print("\n[Step 2] Opening GraphQL WebSocket subscription for groupChanged...")
    ws_client = SimpleGraphQLWSClient(WS_HOST, WS_PORT, GRAPHQL_PATH, user_a)
    ws_client.connect()
    print("  ✓ WebSocket connected and authenticated with BFF")

    sub_query = """
    subscription OnGroupChanged($groupId: ID!) {
        groupChanged(groupId: $groupId) {
            groupId
            revision
            changeId
        }
    }
    """
    ws_client.subscribe("sub-1", sub_query, {"groupId": group_id})
    print(f"  ✓ Subscribed to groupChanged for groupId={group_id}")
    time.sleep(0.3)

    # A malformed operation must produce a protocol error without tearing down
    # the authenticated connection or the valid subscription.
    ws_client.subscribe("malformed-sub", "subscription { doesNotExist }", {})
    time.sleep(0.3)
    assert ws_client.errors, f"Expected a GraphQL error frame for malformed subscription; messages={ws_client.messages!r}"
    print("  ✓ Malformed subscription produced a protocol error frame")

    outsider_ws = SimpleGraphQLWSClient(WS_HOST, WS_PORT, GRAPHQL_PATH, user_nonmember)
    outsider_ws.connect()
    outsider_ws.subscribe("outsider-sub", sub_query, {"groupId": group_id})
    time.sleep(0.4)
    assert outsider_ws.errors, "Expected non-member subscription authorization failure"
    outsider_ws.close()
    print("  ✓ Non-member subscription was rejected by upstream authorization")

    # Reconnect and resubscribe before exercising delivery. This verifies that
    # a dropped transport does not prevent a fresh subscription from receiving
    # subsequent invalidations.
    ws_client.close()
    time.sleep(0.2)
    ws_client = SimpleGraphQLWSClient(WS_HOST, WS_PORT, GRAPHQL_PATH, user_a)
    ws_client.connect()
    ws_client.subscribe("reconnected-sub", sub_query, {"groupId": group_id})
    ws_client.subscribe("cancelled-sub", sub_query, {"groupId": group_id})
    time.sleep(0.3)
    ws_client.complete("cancelled-sub")
    time.sleep(0.2)
    print("  ✓ Authenticated subscription re-established after disconnect")

    # Step 3: Trigger Live Mutations and Observe Invalidation Events
    print("\n[Step 3] Triggering mutations and verifying real-time WebSocket invalidations...")
    expense_id = str(uuid.uuid4())
    create_expense_mutation = """
    mutation CreateExpense($groupId: ID!, $input: CreateExpenseInput!, $idempotencyKey: String!) {
        createExpense(groupId: $groupId, input: $input, idempotencyKey: $idempotencyKey) {
            id
            version
            description
            amount { currency minor }
        }
    }
    """
    status, exp_res = graphql_query(
        create_expense_mutation,
        variables={
            "groupId": group_id,
            "idempotencyKey": str(uuid.uuid4()),
            "input": {
                "expenseId": expense_id,
                "description": "Cabin Rental Deposit",
                "amount": {"currency": "EUR", "minor": "10000"},
                "payers": [{"participantId": alice_id, "amount": {"currency": "EUR", "minor": "10000"}}],
                "allocation": {
                    "mode": "EQUAL",
                    "items": [
                        {"participantId": alice_id, "value": "1"},
                        {"participantId": bob_id, "value": "1"}
                    ]
                }
            }
        },
        bearer=user_a
    )
    assert status == 200 and "data" in exp_res, f"Failed to create expense: {exp_res}"
    print(f"  ✓ Expense created: '{exp_res['data']['createExpense']['description']}', version={exp_res['data']['createExpense']['version']}")

    # Wait for subscription event
    time.sleep(0.5)
    assert len(ws_client.events) >= 1, f"Expected at least 1 subscription event after reconnect, got {len(ws_client.events)}"
    assert not any(
        message.get("type") == "next" and message.get("id") == "cancelled-sub"
        for message in ws_client.messages
    ), "Completed subscription must not receive later invalidations"
    first_event = ws_client.events[-1]["data"]["groupChanged"]
    assert first_event["groupId"] == group_id, f"Wrong groupId in event: {first_event}"
    print(f"  ✓ Real-time invalidation received via WebSocket: revision={first_event['revision']}, changeId={first_event['changeId']}")

    # Step 4: Test Concurrent Member Edits (Optimistic Locking & Race Handling)
    print("\n[Step 4] Simulating concurrent member edit conflict on expense version 1...")
    # Both Alice and Bob attempt to update the same expense at version 1 simultaneously
    url_update = f"{EXPENSE_CORE_URL}/expense-core/v1/groups/{group_id}/expenses/{expense_id}"

    payload_alice = {
        "description": "Cabin Rental (Alice updated split)",
        "amount": {"currency": "EUR", "minor": "10000"},
        "version": 1,
        "payers": [{"participantId": alice_id, "amount": {"currency": "EUR", "minor": "10000"}}],
        "allocation": {
            "mode": "EQUAL",
            "items": [
                {"participantId": alice_id, "value": "1"},
                {"participantId": bob_id, "value": "1"}
            ]
        }
    }

    payload_bob = {
        "description": "Cabin Rental (Bob updated description)",
        "amount": {"currency": "EUR", "minor": "12000"},
        "version": 1,
        "payers": [{"participantId": bob_id, "amount": {"currency": "EUR", "minor": "12000"}}],
        "allocation": {
            "mode": "EQUAL",
            "items": [
                {"participantId": alice_id, "value": "1"},
                {"participantId": bob_id, "value": "1"}
            ]
        }
    }

    results = []

    def perform_update(user_token: str, payload: dict[str, Any]) -> tuple[str, int, dict[str, Any]]:
        status_code, resp_body = request_json(url_update, method="PUT", body=payload, bearer=user_token)
        return (user_token, status_code, resp_body)

    with ThreadPoolExecutor(max_workers=2) as executor:
        f1 = executor.submit(perform_update, user_a, payload_alice)
        f2 = executor.submit(perform_update, user_b, payload_bob)
        results.append(f1.result())
        results.append(f2.result())

    status_codes = [r[1] for r in results]
    print(f"  ✓ Concurrent update status codes: {status_codes}")

    # Invariant: Exactly one should succeed (200 OK) and the other should fail with 409 Conflict
    assert 200 in status_codes, f"Neither request succeeded with 200 OK: {results}"
    assert 409 in status_codes, f"Neither request received 409 Conflict: {results}"

    winner = next(r for r in results if r[1] == 200)
    loser = next(r for r in results if r[1] == 409)

    print(f"  ✓ Winner mutation accepted: new version={winner[2]['version']}")
    assert loser[2].get("code") in ("CONFLICT", "ERR_06", "ERR-06"), f"Unexpected conflict error format: {loser[2]}"
    print(f"  ✓ Competing mutation rejected cleanly: HTTP 409 ({loser[2].get('code')} - {loser[2].get('detail')})")

    # Step 5: Stale Client Recovers and Re-applies with Latest Version
    print("\n[Step 5] Stale client re-reads latest version and resolves conflict...")
    # The loser queries the expense list to get the updated version
    status, exp_list = request_json(f"{EXPENSE_CORE_URL}/expense-core/v1/groups/{group_id}/expenses", bearer=loser[0])
    assert status == 200, f"Failed to fetch expense list: {exp_list}"
    latest_exp = next(e for e in exp_list if e["expenseId"] == expense_id)
    current_version = latest_exp["version"]
    assert current_version == 2, f"Expected version 2, got {current_version}"
    print(f"  ✓ Client fetched latest expense state: version={current_version}")

    # Re-apply update with version 2
    resolved_payload = {
        "description": "Cabin Rental (Mutually agreed)",
        "amount": {"currency": "EUR", "minor": "11000"},
        "version": current_version,
        "payers": [{"participantId": alice_id, "amount": {"currency": "EUR", "minor": "11000"}}],
        "allocation": {
            "mode": "EQUAL",
            "items": [
                {"participantId": alice_id, "value": "1"},
                {"participantId": bob_id, "value": "1"}
            ]
        }
    }
    status_resolved, resolved_res = request_json(url_update, method="PUT", body=resolved_payload, bearer=loser[0])
    assert status_resolved == 200, f"Failed to apply conflict-resolved update: {resolved_res}"
    assert resolved_res["version"] == 3, f"Expected version 3 after resolution, got {resolved_res['version']}"
    print(f"  ✓ Conflict-resolved update successfully saved: version={resolved_res['version']}")

    # Clean up WebSocket
    ws_client.close()
    print("\n======================================================================")
    print("🎉 ALL CONCURRENCY & SUBSCRIPTION TESTS PASSED SUCCESSFULLY!")
    print("======================================================================")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description="Run live concurrency and GraphQL subscription checks")
    parser.add_argument(
        "--evidence-output",
        type=Path,
        help="write QA-10 operation evidence after all assertions pass",
    )
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
        run_concurrency_and_subscriptions_test()
        if arguments.evidence_output is not None:
            write_execution_evidence(
                arguments.evidence_output,
                arguments.source_revision,
                arguments.environment,
                [{
                    "surface": "GraphQL Subscription",
                    "operation": "groupChanged",
                    "status": "passed",
                    "artifact": str(arguments.evidence_output),
                    "assertions": [
                        "authenticated graphql-transport-ws subscription received a matching group invalidation",
                        "malformed and non-member subscriptions were rejected",
                        "disconnect, resubscribe, and completed-subscription filtering were verified",
                        "concurrent stale-version conflict and subsequent recovery were verified",
                    ],
                }],
            )
    except Exception as e:
        print(f"\n❌ TEST SUITE FAILED: {e}", file=sys.stderr)
        import traceback
        traceback.print_exc()
        sys.exit(1)
