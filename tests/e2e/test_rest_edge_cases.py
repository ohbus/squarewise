#!/usr/bin/env python3
"""Live public REST edge-case checks for the Accounts and Expense Core APIs."""

from __future__ import annotations

import json
import argparse
import base64
import os
import io
import sys
from pathlib import Path
from urllib.error import HTTPError, URLError
from urllib.request import Request, urlopen
import uuid
from tests.http_constants import ACCEPT, APPLICATION_JSON, AUTHORIZATION, CONTENT_TYPE, IDEMPOTENCY_KEY, TEXT_CSV
from tests.e2e.error_contract import assert_problem_details
from tests.e2e.qa10_evidence import write_execution_evidence


ACCOUNTS_URL = os.environ.get("ACCOUNTS_URL", "http://localhost:28081")
EXPENSE_CORE_URL = os.environ.get("EXPENSE_CORE_URL", "http://localhost:28082")
NOTIFICATIONS_URL = os.environ.get("NOTIFICATIONS_URL", "http://localhost:28083")
TOKEN = os.environ.get("BEARER_TOKEN")
SECONDARY_TOKEN = os.environ.get("SQUAREWISE_E2E_TOKEN_B", TOKEN)
NON_MEMBER_TOKEN = os.environ.get("SQUAREWISE_E2E_TOKEN_NONMEMBER", SECONDARY_TOKEN)
SIGNED_SECONDARY_PERSONAS = frozenset({
    "invite-claim-user",
    "invite-replay-user",
    "revoked-claim-user",
})

ACCOUNTS_ME = "/accounts/v1/me"
ACCOUNTS_DELETION = "/accounts/v1/me/deletion-request"
ACCOUNTS_EXPORT = "/accounts/v1/me/export-request"
ACCOUNTS_EXPORTS = "/accounts/v1/me/export-requests"
ACCOUNTS_PROFILE = "/accounts/v1/profiles/{account_id}"
ACCOUNTS_PROFILES_BATCH = "/accounts/v1/profiles/batch"
EXPENSE_GROUPS = "/expense-core/v1/groups"
EXPENSE_ALLOCATIONS_PREVIEW = "/expense-core/v1/allocations/preview"
EXPENSE_GROUP_MEMBERS = "/expense-core/v1/groups/{group_id}/members"
EXPENSE_GROUP = "/expense-core/v1/groups/{group_id}"
EXPENSE_GROUP_ARCHIVE = "/expense-core/v1/groups/{group_id}/archive"
EXPENSE_GROUP_EXPENSES = "/expense-core/v1/groups/{group_id}/expenses"
EXPENSE_GROUP_PLACEHOLDERS = "/expense-core/v1/groups/{group_id}/placeholders"
EXPENSE_GROUP_MEMBER = "/expense-core/v1/groups/{group_id}/members/{membership_id}"
EXPENSE_GROUP_INVITES = "/expense-core/v1/groups/{group_id}/invites"
EXPENSE_GROUP_INVITE_REVOKE = "/expense-core/v1/groups/{group_id}/invites/{token}/revoke"
EXPENSE_INVITE_CLAIM = "/expense-core/v1/invites/{token}/claim"
EXPENSE_GROUP_SCHEDULES = "/expense-core/v1/groups/{group_id}/schedules"
EXPENSE_GROUP_SCHEDULE = "/expense-core/v1/groups/{group_id}/schedules/{schedule_id}"
EXPENSE_GROUP_SCHEDULE_PAUSE = "/expense-core/v1/groups/{group_id}/schedules/{schedule_id}/pause"
EXPENSE_GROUP_SEARCH = "/expense-core/v1/groups/{group_id}/search"
EXPENSE_GROUP_EXPORT = "/expense-core/v1/groups/{group_id}/export"
EXPENSE_GROUP_SYNC_SNAPSHOT = "/expense-core/v1/groups/{group_id}/sync/snapshot"
EXPENSE_GROUP_SYNC_CHANGES = "/expense-core/v1/groups/{group_id}/sync/changes"
EXPENSE_GROUP_SETTLEMENTS = "/expense-core/v1/groups/{group_id}/settlements"
EXPENSE_GROUP_SETTLEMENT_SUGGESTIONS = "/expense-core/v1/groups/{group_id}/settlements/suggestions"
EXPENSE_GROUP_SETTLEMENT_REVERSAL = "/expense-core/v1/groups/{group_id}/settlements/{settlement_id}/reversal"
NOTIFICATIONS_INBOX = "/notifications/v1/inbox"
NOTIFICATIONS_MARK_READ = "/notifications/v1/inbox/{notification_id}/read"
NOTIFICATIONS_PREFERENCES = "/notifications/v1/preferences"


def request_json(url: str, method: str = "GET", body: object | None = None, token: str | None = TOKEN,
                headers: dict[str, str] | None = None) -> tuple[int, object]:
    if token in SIGNED_SECONDARY_PERSONAS:
        token = SECONDARY_TOKEN
    elif token == "non-member":
        token = NON_MEMBER_TOKEN
    request_headers = {ACCEPT: APPLICATION_JSON, CONTENT_TYPE: APPLICATION_JSON}
    if token is not None:
        request_headers[AUTHORIZATION] = f"Bearer {token}"
    if headers:
        request_headers.update(headers)
    data = json.dumps(body).encode() if body is not None else None
    request = Request(url, data=data, headers=request_headers, method=method)
    try:
        with urlopen(request, timeout=5) as response:
            raw = response.read()
            return response.status, json.loads(raw) if raw else {}
    except HTTPError as error:
        raw = error.read()
        try:
            return error.code, json.loads(raw) if raw else {}
        except json.JSONDecodeError:
            return error.code, raw.decode(errors="replace")


def expect(label: str, actual: int, *allowed: int) -> None:
    if actual not in allowed:
        raise AssertionError(f"{label}: expected HTTP {allowed}, got {actual}")
    print(f"  ✓ {label}: HTTP {actual}")


def main(evidence_output: Path | None = None, source_revision: str = "local-worktree", environment: str = "local-compose-oidc") -> None:
    """Run live REST edge checks with deterministic UTF-8 console output."""
    if not TOKEN:
        raise RuntimeError("BEARER_TOKEN must contain a signed access token for authenticated checks")
    if isinstance(sys.stdout, io.TextIOWrapper):
        sys.stdout.reconfigure(encoding="utf-8")
    print("Running live REST edge-case checks")

    status, _ = request_json(f"{ACCOUNTS_URL}{ACCOUNTS_ME}", token=None)
    expect("Accounts rejects missing authentication", status, 401)

    status, _ = request_json(f"{ACCOUNTS_URL}{ACCOUNTS_DELETION}", method="POST", token=None)
    expect("Accounts rejects unauthenticated deletion request", status, 401)

    status, _ = request_json(f"{ACCOUNTS_URL}{ACCOUNTS_EXPORT}", method="POST", token=None)
    expect("Accounts rejects unauthenticated export request", status, 401)

    status, _ = request_json(f"{ACCOUNTS_URL}{ACCOUNTS_EXPORTS}", token=None)
    expect("Accounts rejects unauthenticated export listing", status, 401)

    status, body = request_json(
        f"{ACCOUNTS_URL}{ACCOUNTS_ME}", method="PATCH", body={}
    )
    expect("Accounts rejects empty profile patch", status, 422)
    assert_problem_details("Accounts empty profile patch", status, body, 422, "VALIDATION_ERROR")

    status, _ = request_json(
        f"{ACCOUNTS_URL}{ACCOUNTS_ME}", method="PATCH",
        body={"defaultCurrency": "not-a-currency"},
    )
    expect("Accounts rejects invalid profile currency", status, 422)

    status, _ = request_json(
        f"{ACCOUNTS_URL}{ACCOUNTS_PROFILE.format(account_id='not-a-uuid')}"
    )
    expect("Accounts rejects malformed profile identifier", status, 422)

    status, _ = request_json(
        f"{ACCOUNTS_URL}{ACCOUNTS_PROFILES_BATCH}", method="POST", body={"accountIds": []}
    )
    expect("Accounts rejects empty profile batch", status, 422)

    status, _ = request_json(
        f"{ACCOUNTS_URL}{ACCOUNTS_PROFILES_BATCH}", method="POST",
        body={"accountIds": ["not-a-uuid"]},
    )
    expect("Accounts rejects malformed profile batch identifier", status, 422)

    status, _ = request_json(f"{NOTIFICATIONS_URL}{NOTIFICATIONS_INBOX}", token=None)
    expect("Notifications rejects unauthenticated inbox listing", status, 401)

    status, _ = request_json(
        f"{NOTIFICATIONS_URL}{NOTIFICATIONS_INBOX}?limit=0"
    )
    expect("Notifications rejects zero inbox limit", status, 422)

    status, _ = request_json(
        f"{NOTIFICATIONS_URL}{NOTIFICATIONS_INBOX}?limit=101"
    )
    expect("Notifications rejects inbox limit above maximum", status, 422)

    status, _ = request_json(
        f"{NOTIFICATIONS_URL}{NOTIFICATIONS_INBOX}?cursor=not-a-valid-cursor"
    )
    expect("Notifications rejects malformed inbox cursor", status, 422)

    status, _ = request_json(
        f"{NOTIFICATIONS_URL}{NOTIFICATIONS_MARK_READ.format(notification_id=uuid.uuid4())}",
        method="POST",
    )
    expect("Notifications hides unknown notification", status, 404)

    status, _ = request_json(
        f"{NOTIFICATIONS_URL}{NOTIFICATIONS_MARK_READ.format(notification_id=uuid.uuid4())}",
        method="POST", token=None,
    )
    expect("Notifications rejects unauthenticated mark-read", status, 401)

    status, _ = request_json(
        f"{NOTIFICATIONS_URL}{NOTIFICATIONS_MARK_READ.format(notification_id='not-a-uuid')}",
        method="POST",
    )
    expect("Notifications rejects malformed mark-read identifier", status, 422)

    status, _ = request_json(f"{NOTIFICATIONS_URL}{NOTIFICATIONS_PREFERENCES}", token=None)
    expect("Notifications rejects unauthenticated preference read", status, 401)

    status, _ = request_json(
        f"{NOTIFICATIONS_URL}{NOTIFICATIONS_PREFERENCES}",
        method="PUT", body={"emailEnabled": False, "pushEnabled": True}, token=None,
    )
    expect("Notifications rejects unauthenticated preference update", status, 401)

    status, alice_preferences = request_json(
        f"{NOTIFICATIONS_URL}{NOTIFICATIONS_PREFERENCES}",
    )
    expect("Notifications getPreferences returns authenticated defaults", status, 200)
    if alice_preferences != {"emailEnabled": True, "pushEnabled": True}:
        raise AssertionError(f"getPreferences returned unexpected defaults: {alice_preferences}")

    status, _ = request_json(
        f"{NOTIFICATIONS_URL}{NOTIFICATIONS_PREFERENCES}",
        method="PUT", body={"emailEnabled": False, "pushEnabled": True},
    )
    expect("Notifications updatePreferences persists authenticated settings", status, 204)

    status, alice_preferences = request_json(
        f"{NOTIFICATIONS_URL}{NOTIFICATIONS_PREFERENCES}",
    )
    expect("Notifications getPreferences returns updated settings", status, 200)
    if alice_preferences != {"emailEnabled": False, "pushEnabled": True}:
        raise AssertionError(f"getPreferences returned unexpected update: {alice_preferences}")

    status, bob_preferences = request_json(
        f"{NOTIFICATIONS_URL}{NOTIFICATIONS_PREFERENCES}", token=SECONDARY_TOKEN,
    )
    expect("Notifications getPreferences isolates signed subjects", status, 200)
    if bob_preferences != {"emailEnabled": True, "pushEnabled": True}:
        raise AssertionError(f"getPreferences leaked Alice settings: {bob_preferences}")

    status, _ = request_json(
        f"{EXPENSE_CORE_URL}{EXPENSE_GROUPS}",
        method="POST",
        body={"name": "Invalid group", "kind": "NOT_A_GROUP_KIND", "currency": "EUR"},
    )
    expect("group creation rejects invalid kind", status, 422)

    status, _ = request_json(
        f"{EXPENSE_CORE_URL}{EXPENSE_GROUPS}",
        method="POST",
        body={"name": "Unauthenticated group", "kind": "TRIP", "currency": "EUR"},
        token=None,
    )
    expect("group creation rejects missing authentication", status, 401)

    status, _ = request_json(f"{EXPENSE_CORE_URL}{EXPENSE_GROUPS}", token=None)
    expect("group listing rejects missing authentication", status, 401)

    status, group = request_json(
        f"{EXPENSE_CORE_URL}{EXPENSE_GROUPS}",
        method="POST",
        body={"name": f"REST edge {uuid.uuid4().hex[:8]}", "kind": "TRIP", "currency": "EUR"},
    )
    expect("create isolated group", status, 201)
    group_id = group["groupId"]

    status, _ = request_json(
        f"{EXPENSE_CORE_URL}{EXPENSE_GROUP_MEMBERS.format(group_id=group_id)}", token=None
    )
    expect("Expense Core rejects unauthenticated membership read", status, 401)

    status, _ = request_json(
        f"{EXPENSE_CORE_URL}{EXPENSE_GROUP.format(group_id=group_id)}", token=None
    )
    expect("Expense Core rejects unauthenticated group read", status, 401)

    status, _ = request_json(
        f"{EXPENSE_CORE_URL}{EXPENSE_GROUP_EXPENSES.format(group_id=group_id)}", token=None
    )
    expect("Expense Core rejects unauthenticated expense listing", status, 401)

    status, _ = request_json(
        f"{EXPENSE_CORE_URL}{EXPENSE_GROUP.format(group_id=group_id)}/balances", token=None
    )
    expect("Expense Core rejects unauthenticated balance read", status, 401)

    status, _ = request_json(
        f"{EXPENSE_CORE_URL}{EXPENSE_GROUP.format(group_id=group_id)}", token="non-member"
    )
    expect("Expense Core hides group from non-member", status, 404)

    status, _ = request_json(
        f"{EXPENSE_CORE_URL}{EXPENSE_GROUP_MEMBERS.format(group_id=group_id)}", token="non-member"
    )
    expect("Expense Core hides members from non-member", status, 404)

    status, _ = request_json(
        f"{EXPENSE_CORE_URL}{EXPENSE_GROUP.format(group_id=group_id)}",
        method="PATCH", body={"name": "Unauthorized rename"}, token="non-member",
    )
    expect("non-member group update is hidden", status, 404)

    status, updated_group = request_json(
        f"{EXPENSE_CORE_URL}{EXPENSE_GROUP.format(group_id=group_id)}",
        method="PATCH", body={"name": "REST edge updated"},
    )
    expect("authorized group update persists", status, 200)
    if not isinstance(updated_group, dict) or updated_group.get("name") != "REST edge updated":
        raise AssertionError(f"authorized group update returned unexpected body: {updated_group}")

    status, _ = request_json(
        f"{EXPENSE_CORE_URL}{EXPENSE_GROUP.format(group_id=group_id)}",
        method="PATCH", body={"name": "   "},
    )
    expect("group update rejects blank name", status, 422)

    status, _ = request_json(
        f"{EXPENSE_CORE_URL}{EXPENSE_GROUP_ARCHIVE.format(group_id=group_id)}",
        method="POST", token="non-member",
    )
    expect("non-member group archive is hidden", status, 404)

    status, _ = request_json(
        f"{EXPENSE_CORE_URL}{EXPENSE_GROUP_ARCHIVE.format(group_id=group_id)}",
        method="POST", token=None,
    )
    expect("group archive rejects missing authentication", status, 401)

    status, _ = request_json(
        f"{EXPENSE_CORE_URL}{EXPENSE_GROUP_PLACEHOLDERS.format(group_id=group_id)}",
        method="POST", body={"name": "Unauthorized placeholder"}, token="non-member",
    )
    expect("non-member placeholder creation is hidden", status, 404)

    status, _ = request_json(
        f"{EXPENSE_CORE_URL}{EXPENSE_GROUP_PLACEHOLDERS.format(group_id=group_id)}",
        method="POST", body={"name": "   "},
    )
    expect("placeholder creation rejects blank name", status, 422)

    status, _ = request_json(
        f"{EXPENSE_CORE_URL}{EXPENSE_GROUP_MEMBER.format(group_id=group_id, membership_id=uuid.uuid4())}",
        method="DELETE", token="non-member",
    )
    expect("non-member member removal is hidden", status, 404)

    status, _ = request_json(
        f"{EXPENSE_CORE_URL}{EXPENSE_GROUP_MEMBER.format(group_id=group_id, membership_id='not-a-uuid')}",
        method="DELETE",
    )
    expect("member removal rejects malformed identifier", status, 422)

    status, _ = request_json(
        f"{EXPENSE_CORE_URL}{EXPENSE_GROUP_INVITES.format(group_id=group_id)}",
        method="POST", body={"expiresInHours": 24}, token="non-member",
    )
    expect("non-member invite creation is hidden", status, 404)

    status, _ = request_json(
        f"{EXPENSE_CORE_URL}{EXPENSE_GROUP_INVITE_REVOKE.format(group_id=group_id, token='not-a-real-token')}",
        method="POST", token="non-member",
    )
    expect("non-member invite revocation is hidden", status, 404)

    status, _ = request_json(
        f"{EXPENSE_CORE_URL}{EXPENSE_GROUP_INVITE_REVOKE.format(group_id=group_id, token='not-a-real-token')}",
        method="POST",
    )
    expect("member cannot revoke unknown invite", status, 409)

    status, _ = request_json(
        f"{EXPENSE_CORE_URL}{EXPENSE_INVITE_CLAIM.format(token='not-a-real-invite')}",
        method="POST",
    )
    expect("invalid invite claim is rejected", status, 409)
    status, _ = request_json(
        f"{EXPENSE_CORE_URL}{EXPENSE_INVITE_CLAIM.format(token='not-a-real-invite')}",
        method="POST", token=None,
    )
    expect("unauthenticated invite claim is rejected", status, 401)

    for invalid_hours in (0, 169):
        status, _ = request_json(
            f"{EXPENSE_CORE_URL}{EXPENSE_GROUP_INVITES.format(group_id=group_id)}",
            method="POST", body={"expiresInHours": invalid_hours},
        )
        expect(f"invite expiry {invalid_hours} hours is rejected", status, 422)

    status, invite = request_json(
        f"{EXPENSE_CORE_URL}{EXPENSE_GROUP_INVITES.format(group_id=group_id)}",
        method="POST", body={"expiresInHours": 24},
    )
    expect("member can create claimable invite", status, 201)
    invite_token = invite["token"]
    status, _ = request_json(
        f"{EXPENSE_CORE_URL}{EXPENSE_INVITE_CLAIM.format(token=invite_token)}",
        method="POST", token="invite-claim-user",
    )
    expect("authenticated user can claim invite", status, 200)
    status, _ = request_json(
        f"{EXPENSE_CORE_URL}{EXPENSE_INVITE_CLAIM.format(token=invite_token)}",
        method="POST", token="invite-replay-user",
    )
    expect("claimed invite replay is rejected", status, 409)
    status, _ = request_json(
        f"{EXPENSE_CORE_URL}{EXPENSE_GROUP.format(group_id=group_id)}",
        token="invite-claim-user",
    )
    expect("claimed user can read group", status, 200)

    status, revoked_invite = request_json(
        f"{EXPENSE_CORE_URL}{EXPENSE_GROUP_INVITES.format(group_id=group_id)}",
        method="POST", body={"expiresInHours": 24},
    )
    expect("member can create revocable invite", status, 201)
    revoked_token = revoked_invite["token"]
    status, _ = request_json(
        f"{EXPENSE_CORE_URL}{EXPENSE_GROUP_INVITE_REVOKE.format(group_id=group_id, token=revoked_token)}",
        method="POST",
    )
    expect("Expense Core revokeInvite succeeds for an authorized member", status, 204)
    status, _ = request_json(
        f"{EXPENSE_CORE_URL}{EXPENSE_INVITE_CLAIM.format(token=revoked_token)}",
        method="POST", token="revoked-claim-user",
    )
    expect("revoked invite claim is rejected", status, 409)

    schedule_id = str(uuid.uuid4())
    status, _ = request_json(
        f"{EXPENSE_CORE_URL}{EXPENSE_GROUP_SCHEDULES.format(group_id=group_id)}",
        token="non-member",
    )
    expect("non-member schedule listing is hidden", status, 404)

    schedule_payload = {
        "description": "Unauthorized schedule",
        "amount": {"currency": "EUR", "minor": "100"},
        "frequency": "MONTHLY",
        "dayOfMonth": 1,
        "startDate": "2026-01-01",
    }
    status, _ = request_json(
        f"{EXPENSE_CORE_URL}{EXPENSE_GROUP_SCHEDULES.format(group_id=group_id)}",
        method="POST", body=schedule_payload, token="non-member",
    )
    expect("non-member schedule creation is hidden", status, 404)

    status, _ = request_json(
        f"{EXPENSE_CORE_URL}{EXPENSE_GROUP_SCHEDULE.format(group_id=group_id, schedule_id=schedule_id)}",
        token="non-member",
    )
    expect("non-member schedule lookup is hidden", status, 404)

    status, _ = request_json(
        f"{EXPENSE_CORE_URL}{EXPENSE_GROUP_SCHEDULE_PAUSE.format(group_id=group_id, schedule_id=schedule_id)}",
        method="POST", token="non-member",
    )
    expect("non-member schedule pause is hidden", status, 404)

    settlement_participant = str(uuid.uuid4())
    settlement_payload = {
        "fromParticipantId": settlement_participant,
        "toParticipantId": str(uuid.uuid4()),
        "amountMinor": "100",
        "currency": "EUR",
    }
    status, _ = request_json(
        f"{EXPENSE_CORE_URL}{EXPENSE_GROUP_SETTLEMENTS.format(group_id=group_id)}",
        method="POST", body=settlement_payload, token="non-member",
        headers={IDEMPOTENCY_KEY: str(uuid.uuid4())},
    )
    expect("non-member settlement recording is hidden", status, 404)

    status, _ = request_json(
        f"{EXPENSE_CORE_URL}{EXPENSE_GROUP_SETTLEMENT_SUGGESTIONS.format(group_id=group_id)}",
        token="non-member",
    )
    expect("non-member settlement suggestions are hidden", status, 404)

    status, _ = request_json(
        f"{EXPENSE_CORE_URL}{EXPENSE_GROUP_SETTLEMENT_REVERSAL.format(group_id=group_id, settlement_id=uuid.uuid4())}",
        method="POST", body={"reason": "unauthorized"}, token="non-member",
    )
    expect("non-member settlement reversal is hidden", status, 404)

    status, _ = request_json(
        f"{EXPENSE_CORE_URL}{EXPENSE_GROUP.format(group_id='not-a-uuid')}"
    )
    expect("malformed group identifier is rejected", status, 400, 404)

    status, body = request_json(
        f"{EXPENSE_CORE_URL}{EXPENSE_ALLOCATIONS_PREVIEW}",
        method="POST",
        body={"totalMinor": "-1", "participantIds": ["alice"]},
    )
    expect("allocation validation rejects negative totals", status, 422)
    assert_problem_details("negative allocation", status, body, 422, "VALIDATION_ERROR")

    status, _ = request_json(
        f"{EXPENSE_CORE_URL}{EXPENSE_ALLOCATIONS_PREVIEW}",
        method="POST", body={"totalMinor": "100", "participantIds": ["alice"]}, token=None,
    )
    expect("allocation preview rejects missing authentication", status, 401)

    status, _ = request_json(
        f"{EXPENSE_CORE_URL}{EXPENSE_GROUP_SEARCH.format(group_id=group_id)}?limit=0"
    )
    expect("search pagination rejects zero limit", status, 422)

    status, _ = request_json(
        f"{EXPENSE_CORE_URL}{EXPENSE_GROUP_SEARCH.format(group_id=group_id)}?cursor=%25%25%25invalid%25%25%25"
    )
    expect("search rejects malformed cursor", status, 422)

    status, _ = request_json(
        f"{EXPENSE_CORE_URL}{EXPENSE_GROUP_SEARCH.format(group_id=group_id)}",
        token="non-member",
    )
    expect("non-member search is hidden", status, 404)

    status, _ = request_json(
        f"{EXPENSE_CORE_URL}{EXPENSE_GROUP_SYNC_SNAPSHOT.format(group_id=group_id)}?cursor=not-a-valid-cursor"
    )
    expect("sync snapshot rejects malformed cursor", status, 422)

    status, _ = request_json(
        f"{EXPENSE_CORE_URL}{EXPENSE_GROUP_SYNC_CHANGES.format(group_id=group_id)}?cursor=not-a-valid-cursor"
    )
    expect("sync changes rejects malformed cursor", status, 422)

    status, _ = request_json(
        f"{EXPENSE_CORE_URL}{EXPENSE_GROUP_SYNC_SNAPSHOT.format(group_id=group_id)}",
        token="non-member",
    )
    expect("non-member sync snapshot is hidden", status, 404)

    status, _ = request_json(
        f"{EXPENSE_CORE_URL}{EXPENSE_GROUP_SYNC_CHANGES.format(group_id=group_id)}",
        token="non-member",
    )
    expect("non-member sync changes are hidden", status, 404)

    expired_cursor = base64.urlsafe_b64encode(f"{group_id}|0|0".encode()).decode().rstrip("=")
    status, _ = request_json(
        f"{EXPENSE_CORE_URL}{EXPENSE_GROUP_SYNC_SNAPSHOT.format(group_id=group_id)}?cursor={expired_cursor}"
    )
    expect("sync snapshot rejects expired cursor", status, 422, 410)

    status, _ = request_json(
        f"{EXPENSE_CORE_URL}{EXPENSE_GROUP_SYNC_CHANGES.format(group_id=group_id)}?cursor={expired_cursor}"
    )
    expect("sync changes rejects expired cursor", status, 422, 410)

    status, _ = request_json(
        f"{EXPENSE_CORE_URL}{EXPENSE_GROUP_EXPORT.format(group_id=group_id)}?maxRows=0",
        headers={ACCEPT: TEXT_CSV},
    )
    expect("CSV export rejects zero row limit", status, 422)

    status, _ = request_json(
        f"{EXPENSE_CORE_URL}{EXPENSE_GROUP_EXPORT.format(group_id=group_id)}",
        headers={ACCEPT: APPLICATION_JSON},
    )
    expect("CSV export rejects incompatible media type", status, 406)

    status, _ = request_json(
        f"{EXPENSE_CORE_URL}{EXPENSE_GROUP_EXPORT.format(group_id=group_id)}",
        token="non-member", headers={ACCEPT: TEXT_CSV},
    )
    expect("non-member CSV export is hidden", status, 404)

    status, members = request_json(
        f"{EXPENSE_CORE_URL}{EXPENSE_GROUP_MEMBERS.format(group_id=group_id)}"
    )
    expect("member lookup for financial edge case", status, 200)
    participant = members[0]["membershipId"]
    expense_id = str(uuid.uuid4())
    key = f"rest-edge-{uuid.uuid4()}"
    payload = {
        "expenseId": expense_id,
        "description": "Idempotency edge",
        "amount": {"currency": "EUR", "minor": "100"},
        "payers": [{"participantId": participant, "amount": {"currency": "EUR", "minor": "100"}}],
        "allocation": {"mode": "EQUAL", "items": [{"participantId": participant, "value": "1"}]},
    }
    status, before_rejected_expenses = request_json(
        f"{EXPENSE_CORE_URL}{EXPENSE_GROUP_EXPENSES.format(group_id=group_id)}"
    )
    expect("member expense baseline is readable before rejected writes", status, 200)
    if not isinstance(before_rejected_expenses, list):
        raise AssertionError("expense baseline must be a JSON list")
    status, _ = request_json(
        f"{EXPENSE_CORE_URL}{EXPENSE_GROUP_EXPENSES.format(group_id=group_id)}",
        method="POST", body=payload, token="non-member",
        headers={IDEMPOTENCY_KEY: f"non-member-{uuid.uuid4()}"},
    )
    expect("non-member expense creation is hidden", status, 404)
    status, _ = request_json(
        f"{EXPENSE_CORE_URL}{EXPENSE_GROUP_EXPENSES.format(group_id=group_id)}",
        method="POST", body=payload, token=None,
        headers={IDEMPOTENCY_KEY: f"missing-auth-{uuid.uuid4()}"},
    )
    expect("unauthenticated expense creation is rejected", status, 401)
    status, after_rejected_expenses = request_json(
        f"{EXPENSE_CORE_URL}{EXPENSE_GROUP_EXPENSES.format(group_id=group_id)}"
    )
    expect("member expense state remains readable after rejected writes", status, 200)
    if not isinstance(after_rejected_expenses, list):
        raise AssertionError("expense state after rejected writes must be a JSON list")
    if after_rejected_expenses != before_rejected_expenses:
        raise AssertionError("rejected expense writes must not mutate durable expense state")
    status, first = request_json(
        f"{EXPENSE_CORE_URL}{EXPENSE_GROUP_EXPENSES.format(group_id=group_id)}",
        method="POST", body=payload, headers={IDEMPOTENCY_KEY: key},
    )
    expect("create idempotent expense", status, 201)

    status, _ = request_json(
        f"{EXPENSE_CORE_URL}{EXPENSE_GROUP_EXPENSES.format(group_id=group_id)}", token="non-member"
    )
    expect("non-member expense listing is hidden", status, 404)

    status, _ = request_json(
        f"{EXPENSE_CORE_URL}{EXPENSE_GROUP.format(group_id=group_id)}/balances", token="non-member"
    )
    expect("non-member balances are hidden", status, 404)

    update_payload = dict(payload)
    update_payload["description"] = "Unauthorized update"
    status, before_rejected_mutations = request_json(
        f"{EXPENSE_CORE_URL}{EXPENSE_GROUP_EXPENSES.format(group_id=group_id)}"
    )
    expect("member expense state is readable before rejected update/delete", status, 200)
    if not isinstance(before_rejected_mutations, list):
        raise AssertionError("expense state before rejected update/delete must be a JSON list")
    status, _ = request_json(
        f"{EXPENSE_CORE_URL}{EXPENSE_GROUP_EXPENSES.format(group_id=group_id)}/{expense_id}",
        method="PUT", body={"version": 1, **{key: value for key, value in update_payload.items() if key != "expenseId"}},
        token="non-member",
    )
    expect("non-member expense update is hidden", status, 404)

    status, _ = request_json(
        f"{EXPENSE_CORE_URL}{EXPENSE_GROUP_EXPENSES.format(group_id=group_id)}/{expense_id}",
        method="PUT", body={"version": 1, **{key: value for key, value in update_payload.items() if key != "expenseId"}},
        token=None,
    )
    expect("unauthenticated expense update is rejected", status, 401)

    status, _ = request_json(
        f"{EXPENSE_CORE_URL}{EXPENSE_GROUP_EXPENSES.format(group_id=group_id)}/{expense_id}?version=1",
        method="DELETE", token=None,
    )
    expect("unauthenticated expense deletion is rejected", status, 401)

    status, _ = request_json(
        f"{EXPENSE_CORE_URL}{EXPENSE_GROUP_EXPENSES.format(group_id=group_id)}/{expense_id}?version=1",
        method="DELETE", token="non-member",
    )
    expect("non-member expense deletion is hidden", status, 404)
    status, after_rejected_mutations = request_json(
        f"{EXPENSE_CORE_URL}{EXPENSE_GROUP_EXPENSES.format(group_id=group_id)}"
    )
    expect("member expense state remains readable after rejected update/delete", status, 200)
    if not isinstance(after_rejected_mutations, list):
        raise AssertionError("expense state after rejected update/delete must be a JSON list")
    if after_rejected_mutations != before_rejected_mutations:
        raise AssertionError("rejected expense update/delete must not mutate durable expense state")

    status, replay = request_json(
        f"{EXPENSE_CORE_URL}{EXPENSE_GROUP_EXPENSES.format(group_id=group_id)}",
        method="POST", body=payload, headers={IDEMPOTENCY_KEY: key},
    )
    expect("duplicate expense replay is idempotent", status, 200, 201)
    if isinstance(first, dict) and isinstance(replay, dict) and first.get("expenseId") != replay.get("expenseId"):
        raise AssertionError("duplicate expense replay returned a different expense")

    altered = dict(payload)
    altered["description"] = "tampered replay"
    status, body = request_json(
        f"{EXPENSE_CORE_URL}{EXPENSE_GROUP_EXPENSES.format(group_id=group_id)}",
        method="POST", body=altered, headers={IDEMPOTENCY_KEY: key},
    )
    expect("tampered idempotency replay conflicts", status, 409)
    assert_problem_details("tampered idempotency replay", status, body, 409, "STATE_CONFLICT")

    status, _ = request_json(
        f"{EXPENSE_CORE_URL}{EXPENSE_GROUP_EXPENSES.format(group_id=group_id)}",
        method="POST", body=payload,
    )
    expect("missing idempotency key is rejected", status, 422)

    status, _ = request_json(
        f"{EXPENSE_CORE_URL}{EXPENSE_GROUP.format(group_id='00000000-0000-0000-0000-000000000099')}"
    )
    expect("missing group is not disclosed", status, 404)

    status, _ = request_json(
        f"{EXPENSE_CORE_URL}{EXPENSE_GROUP_ARCHIVE.format(group_id=group_id)}",
        method="POST",
    )
    expect("Expense Core archiveGroup succeeds for an authorized member", status, 200)

    status, body = request_json(f"{EXPENSE_CORE_URL}{EXPENSE_GROUP.format(group_id=group_id)}")
    expect("archived group is hidden from member lookup", status, 404)
    assert_problem_details("archived group lookup", status, body, 404, "NOT_FOUND")

    status, _ = request_json(
        f"{EXPENSE_CORE_URL}{EXPENSE_GROUP_MEMBERS.format(group_id=group_id)}"
    )
    expect("archived group members are hidden", status, 404)

    status, groups = request_json(f"{EXPENSE_CORE_URL}{EXPENSE_GROUPS}")
    expect("member group list remains available after archive", status, 200)
    if isinstance(groups, list) and any(isinstance(item, dict) and item.get("groupId") == group_id for item in groups):
        raise AssertionError("archived group was still returned by member group listing")

    status, _ = request_json(
        f"{EXPENSE_CORE_URL}{EXPENSE_GROUP_ARCHIVE.format(group_id=group_id)}",
        method="POST",
    )
    expect("archive replay is rejected", status, 404, 409)

    status, _ = request_json(
        f"{EXPENSE_CORE_URL}{EXPENSE_GROUP_EXPENSES.format(group_id=group_id)}",
        method="POST", body=payload,
        headers={IDEMPOTENCY_KEY: f"archived-{uuid.uuid4()}"},
    )
    expect("archived group rejects new expense", status, 404, 409)

    print("REST edge-case checks passed")
    if evidence_output is not None:
        artifact = str(evidence_output)
        write_execution_evidence(
            evidence_output,
            source_revision,
            environment,
            [
                {
                    "surface": "REST",
                    "operation": "createGroup",
                    "status": "passed",
                    "artifact": artifact,
                    "assertions": [
                        "authorized member created an isolated group",
                        "invalid-kind and unauthenticated creation were rejected",
                    ],
                },
                {
                    "surface": "REST",
                    "operation": "archiveGroup",
                    "status": "passed",
                    "artifact": artifact,
                    "assertions": [
                        "authorized archive returned HTTP 200",
                        "archived group and members became hidden and archive replay was rejected",
                    ],
                },
                {
                    "surface": "REST",
                    "operation": "updateGroup",
                    "status": "passed",
                    "artifact": artifact,
                    "assertions": [
                        "authorized member rename returned HTTP 200",
                        "updated group name was returned and persisted before archive",
                    ],
                },
                {
                    "surface": "REST",
                    "operation": "revokeInvite",
                    "status": "passed",
                    "artifact": artifact,
                    "assertions": [
                        "authorized invite revocation returned HTTP 204",
                        "revoked invite claim returned HTTP 409",
                    ],
                },
                {
                    "surface": "REST",
                    "operation": "getPreferences",
                    "status": "passed",
                    "artifact": artifact,
                    "assertions": [
                        "authenticated defaults were returned",
                        "updated settings persisted and remained isolated from a second subject",
                    ],
                },
                {
                    "surface": "REST",
                    "operation": "updatePreferences",
                    "status": "passed",
                    "artifact": artifact,
                    "assertions": ["authenticated preference update returned HTTP 204 and persisted settings"],
                },
            ],
        )


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description="Run live REST edge-case checks")
    parser.add_argument("--evidence-output", type=Path, help="write QA-10 operation evidence after success")
    parser.add_argument("--source-revision", default=os.environ.get("GITHUB_SHA", "local-worktree"))
    parser.add_argument(
        "--environment",
        default=os.environ.get("QA10_E2E_ENVIRONMENT", "local-compose-oidc"),
    )
    arguments = parser.parse_args()
    main(arguments.evidence_output, arguments.source_revision, arguments.environment)
