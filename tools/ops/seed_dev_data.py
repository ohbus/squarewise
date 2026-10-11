#!/usr/bin/env python3
"""Seed rich realistic development data into the Squarewise ecosystem.

Provisions default personas (Alice, Bob, Charlie, Dave, Eve) with matching profiles
and identities in Accounts, and creates realistic groups, invitations, memberships,
multi-participant expenses (with EQUAL, EXACT, and PERCENTAGE splits), recurring
expense schedules, settlements, and notification inboxes.

Usage:
    uv run python3 tools/ops/seed_dev_data.py [--large] [--reset] [--groups N] [--expenses M]
"""

from __future__ import annotations

import argparse
import base64
import json
import os
import subprocess
import sys
import time
from urllib.error import HTTPError, URLError
from urllib.parse import urlencode
from urllib.request import Request, urlopen
import uuid
from dataclasses import dataclass
from typing import Any, Final

from tests.http_constants import ACCEPT, APPLICATION_JSON, AUTHORIZATION, CONTENT_TYPE

BFF_URL: Final[str] = os.environ.get("SQUAREWISE_BFF_URL", "http://localhost:28080")
ACCOUNTS_URL: Final[str] = os.environ.get("SQUAREWISE_ACCOUNTS_URL", "http://localhost:28081")
EXPENSE_CORE_URL: Final[str] = os.environ.get("SQUAREWISE_EXPENSE_CORE_URL", "http://localhost:28082")
NOTIFICATIONS_URL: Final[str] = os.environ.get("SQUAREWISE_NOTIFICATIONS_URL", "http://localhost:28083")
KEYCLOAK_URL: Final[str] = os.environ.get("SQUAREWISE_KEYCLOAK_URL", "http://localhost:28090")
POSTGRES_CONTAINER: Final[str] = os.environ.get("SQUAREWISE_POSTGRES_CONTAINER", "local-postgres-1")
POSTGRES_USER: Final[str] = os.environ.get("POSTGRES_USER", "squarewise")
POSTGRES_HOST: Final[str] = os.environ.get("SQUAREWISE_POSTGRES_HOST", os.environ.get("PGHOST", ""))
POSTGRES_PORT: Final[str] = os.environ.get("SQUAREWISE_POSTGRES_PORT", os.environ.get("PGPORT", "25432"))
POSTGRES_PASSWORD: Final[str] = os.environ.get("POSTGRES_PASSWORD", "squarewise-local-only")


@dataclass(frozen=True)
class Persona:
    name: str
    email: str
    keycloak_client: str | None
    keycloak_secret: str | None
    account_id: str
    subject: str


PERSONAS: Final[list[Persona]] = [
    Persona(
        name="alice",
        email="alice@squarewise.local",
        keycloak_client="squarewise-ci",
        keycloak_secret="squarewise-ci-local-only",
        account_id="a224e36b-4efb-4de5-8f45-8d938b395502",
        subject="sqw:a224e36b-4efb-4de5-8f45-8d938b395502",
    ),
    Persona(
        name="bob",
        email="bob@squarewise.local",
        keycloak_client="squarewise-ci-e2e-bob",
        keycloak_secret="squarewise-ci-e2e-bob-local-only",
        account_id="50f8a0a2-6e60-4b5d-918f-8e5c4fa46e8d",
        subject="sqw:50f8a0a2-6e60-4b5d-918f-8e5c4fa46e8d",
    ),
    Persona(
        name="charlie",
        email="charlie@squarewise.local",
        keycloak_client="squarewise-ci-e2e-nonmember",
        keycloak_secret="squarewise-ci-e2e-nonmember-local-only",
        account_id="12b0b6fc-b8fd-45ec-b46a-f9d682e6c1f3",
        subject="sqw:12b0b6fc-b8fd-45ec-b46a-f9d682e6c1f3",
    ),
    Persona(
        name="dave",
        email="dave@squarewise.local",
        keycloak_client=None,
        keycloak_secret=None,
        account_id="7e12c85b-b153-4819-bf91-a15e6e8bb241",
        subject="sqw:7e12c85b-b153-4819-bf91-a15e6e8bb241",
    ),
    Persona(
        name="eve",
        email="eve@squarewise.local",
        keycloak_client=None,
        keycloak_secret=None,
        account_id="8f23d96c-c264-492a-c0a2-b26f7f9cc352",
        subject="sqw:8f23d96c-c264-492a-c0a2-b26f7f9cc352",
    ),
]


def request_json(
    url: str,
    method: str = "GET",
    body: Any = None,
    bearer: str | None = None,
    timeout: float = 10.0,
) -> tuple[int, Any]:
    headers: dict[str, str] = {
        ACCEPT: APPLICATION_JSON,
        CONTENT_TYPE: APPLICATION_JSON,
    }
    if bearer:
        headers[AUTHORIZATION] = f"Bearer {bearer}"

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


def graphql_mutate(
    query: str,
    variables: dict[str, Any] | None = None,
    bearer: str | None = None,
) -> dict[str, Any]:
    payload: dict[str, Any] = {"query": query}
    if variables:
        payload["variables"] = variables
    status, res = request_json(f"{BFF_URL}/graphql", method="POST", body=payload, bearer=bearer)
    if status != 200:
        raise RuntimeError(f"GraphQL HTTP status {status}: {res}")
    if isinstance(res, dict) and "errors" in res and res["errors"]:
        raise RuntimeError(f"GraphQL returned errors: {res['errors']}")
    return res.get("data", {}) if isinstance(res, dict) else {}


def acquire_keycloak_token(client_id: str, secret: str) -> str:
    """Acquire token from Keycloak using client credentials."""
    url = f"{KEYCLOAK_URL}/realms/squarewise/protocol/openid-connect/token"
    payload = urlencode({
        "grant_type": "client_credentials",
        "client_id": client_id,
        "client_secret": secret,
    }).encode("utf-8")
    req = Request(
        url,
        data=payload,
        headers={
            "Host": "idp-keycloak:8080",
            CONTENT_TYPE: "application/x-www-form-urlencoded",
        },
    )
    with urlopen(req, timeout=10) as resp:
        data = json.loads(resp.read().decode("utf-8"))
        token = data.get("access_token")
        if not isinstance(token, str):
            raise ValueError(f"Failed to extract access_token from Keycloak response: {data}")
        return token


def extract_jwt_sub(token: str) -> str:
    """Extract sub claim from JWT token."""
    parts = token.split(".")
    if len(parts) >= 2:
        payload = parts[1]
        payload += "=" * (-len(payload) % 4)
        data = json.loads(base64.urlsafe_b64decode(payload))
        return str(data.get("sub", ""))
    return ""


def exec_psql(db: str, query: str) -> None:
    """Execute SQL query inside the local Postgres container or via direct TCP connection."""
    if POSTGRES_HOST:
        cmd = [
            "psql",
            "-h",
            POSTGRES_HOST,
            "-p",
            POSTGRES_PORT,
            "-U",
            POSTGRES_USER,
            "-d",
            db,
            "-c",
            query,
        ]
        env = dict(os.environ, PGPASSWORD=POSTGRES_PASSWORD)
        subprocess.run(cmd, env=env, check=True, stdout=subprocess.DEVNULL, stderr=subprocess.PIPE)
    else:
        cmd = [
            "docker",
            "exec",
            POSTGRES_CONTAINER,
            "psql",
            "-U",
            POSTGRES_USER,
            "-d",
            db,
            "-c",
            query,
        ]
        subprocess.run(cmd, check=True, stdout=subprocess.DEVNULL, stderr=subprocess.PIPE)


def ensure_postgres_personas(personas: list[Persona] | None = None) -> None:
    """Ensure all personas exist in squarewise_accounts account_profiles & account_identities."""
    target_personas = personas if personas is not None else PERSONAS
    print("Provisioning account profiles and identities...")
    for p in target_personas:
        profile_sql = f"""
        INSERT INTO account_profiles (account_id, subject, display_name, timezone, default_currency, deletion_requested)
        VALUES ('{p.account_id}', '{p.subject}', '{p.name}', 'UTC', 'EUR', false)
        ON CONFLICT (subject) DO UPDATE SET display_name = EXCLUDED.display_name;
        """
        identity_sql = f"""
        INSERT INTO account_identities (identity_id, account_id, issuer, provider_subject, email, email_verified, status)
        VALUES (
            gen_random_uuid(),
            '{p.account_id}',
            'http://idp-keycloak:8080/realms/squarewise',
            '{p.subject}',
            '{p.email}',
            true,
            'ACTIVE'
        )
        ON CONFLICT (issuer, provider_subject) DO NOTHING;
        """
        exec_psql("squarewise_accounts", profile_sql)
        exec_psql("squarewise_accounts", identity_sql)
    print("  Account profiles & identities provisioned.")


def reset_dev_data() -> None:
    """Wipe groups and dependent entities to start fresh."""
    print("Resetting development data in Expense Core & Notifications...")
    exec_psql("squarewise_expense_core", "TRUNCATE TABLE recurring_expense_occurrences, recurring_expense_schedules, settlements, balance_postings, expense_allocations, expense_payers, expenses, expense_idempotency, group_invitations, group_memberships, group_audit, sync_changes, expense_outbox, expense_groups CASCADE;")
    exec_psql("squarewise_notifications", "TRUNCATE TABLE notification_inbox_items, notification_processed_events CASCADE;")
    print("  Reset complete.")


def seed_group_with_expenses(
    group_name: str,
    group_kind: str,
    currency: str,
    primary_token: str,
    secondary_token: str,
    extra_participants: list[str],
    expense_specs: list[dict[str, Any]],
    schedules: list[dict[str, Any]],
    record_settlement: bool = True,
) -> str:
    """Create a group, invite and claim members, record expenses, schedules, and settlement."""
    print(f"\nSeeding group: '{group_name}' ({group_kind}, {currency})...")

    # 1. Create Group via REST
    status, group_res = request_json(
        f"{EXPENSE_CORE_URL}/expense-core/v1/groups",
        method="POST",
        body={"name": group_name, "kind": group_kind, "currency": currency},
        bearer=primary_token,
    )
    if status != 201:
        raise RuntimeError(f"Failed to create group '{group_name}': HTTP {status} {group_res}")
    group_id = str(group_res["groupId"])
    print(f"  ✓ Created group {group_id}")

    # 2. Invite & claim secondary member
    status, invite_res = request_json(
        f"{EXPENSE_CORE_URL}/expense-core/v1/groups/{group_id}/invites",
        method="POST",
        body={"expiresInHours": 48},
        bearer=primary_token,
    )
    if status != 201:
        raise RuntimeError(f"Failed to create invite for group '{group_name}': HTTP {status} {invite_res}")
    invite_token = invite_res["token"]

    status, claim_res = request_json(
        f"{EXPENSE_CORE_URL}/expense-core/v1/invites/{invite_token}/claim",
        method="POST",
        body={},
        bearer=secondary_token,
    )
    if status != 200:
        raise RuntimeError(f"Failed to claim invite for group '{group_name}': HTTP {status} {claim_res}")

    # 3. Add extra participants directly to memberships
    persona_map = {p.name: p for p in PERSONAS}
    for p_name in extra_participants:
        p_sub = persona_map[p_name].subject if p_name in persona_map else f"placeholder:{uuid.uuid4()}"
        p_mem_sql = f"""
        INSERT INTO group_memberships (membership_id, group_id, subject, display_name, is_placeholder, status)
        VALUES (gen_random_uuid(), '{group_id}', '{p_sub}', '{p_name}', false, 'ACTIVE');
        """
        exec_psql("squarewise_expense_core", p_mem_sql)

    # 4. Fetch group members
    status, members_list = request_json(
        f"{EXPENSE_CORE_URL}/expense-core/v1/groups/{group_id}/members",
        bearer=primary_token,
    )
    if status != 200 or not isinstance(members_list, list):
        raise RuntimeError(f"Failed to list members for {group_id}: HTTP {status} {members_list}")

    members_by_name: dict[str, str] = {}
    for m in members_list:
        name = m.get("displayName") or m.get("subject")
        members_by_name[name] = str(m["membershipId"])

    primary_sub = extract_jwt_sub(primary_token)
    secondary_sub = extract_jwt_sub(secondary_token)

    primary_mem_id = ""
    secondary_mem_id = ""
    for m in members_list:
        sub = m.get("subject")
        if sub == primary_sub:
            primary_mem_id = str(m["membershipId"])
        elif sub == secondary_sub:
            secondary_mem_id = str(m["membershipId"])

    print(f"  ✓ Members enrolled: {len(members_list)} participants")

    # 5. Record Expenses
    create_expense_mutation = """
    mutation CreateExpense($groupId: ID!, $input: CreateExpenseInput!, $idempotencyKey: String!) {
        createExpense(groupId: $groupId, input: $input, idempotencyKey: $idempotencyKey) {
            id
            description
            amount {
                currency
                minor
            }
        }
    }
    """

    for spec in expense_specs:
        exp_id = str(uuid.uuid4())
        desc = spec["description"]
        total_minor = str(spec["minor"])
        split_mode = spec.get("mode", "EQUAL")

        payer_id = primary_mem_id if spec.get("payer") == "primary" else secondary_mem_id

        if split_mode == "EQUAL":
            allocation_items = [
                {"participantId": primary_mem_id, "value": "1"},
                {"participantId": secondary_mem_id, "value": "1"},
            ]
            for extra in extra_participants:
                if extra in members_by_name:
                    allocation_items.append({"participantId": members_by_name[extra], "value": "1"})
        elif split_mode == "EXACT":
            allocation_items = spec["allocations"]
        else:
            allocation_items = [
                {"participantId": primary_mem_id, "value": "5000"},
                {"participantId": secondary_mem_id, "value": "5000"},
            ]

        exp_input: dict[str, Any] = {
            "expenseId": exp_id,
            "description": desc,
            "amount": {"currency": currency, "minor": total_minor},
            "payers": [{"participantId": payer_id, "amount": {"currency": currency, "minor": total_minor}}],
            "allocation": {
                "mode": split_mode,
                "items": allocation_items,
            },
        }

        graphql_mutate(
            create_expense_mutation,
            variables={
                "groupId": group_id,
                "input": exp_input,
                "idempotencyKey": f"seed-{uuid.uuid4()}",
            },
            bearer=primary_token,
        )
        print(f"  ✓ Expense: '{desc}' ({int(total_minor)/100:.2f} {currency}, {split_mode} split)")

    # 6. Create Recurring Schedules
    for sched in schedules:
        sched_body = {
            "description": sched["description"],
            "amount": {"currency": currency, "minor": sched["minor"]},
            "frequency": sched["frequency"],
            "startDate": sched["startDate"],
            "dayOfMonth": sched.get("dayOfMonth", 1),
        }
        status, sched_res = request_json(
            f"{EXPENSE_CORE_URL}/expense-core/v1/groups/{group_id}/schedules",
            method="POST",
            body=sched_body,
            bearer=primary_token,
        )
        if status == 201:
            print(f"  ✓ Recurring Schedule: '{sched['description']}' ({sched['frequency']}, {sched['minor']/100:.2f} {currency})")

    # 7. Record Settlement if requested
    if record_settlement:
        sugg_data = graphql_mutate(
            """
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
            """,
            variables={"groupId": group_id},
            bearer=primary_token,
        )
        suggestions = sugg_data.get("settlementSuggestions", [])
        if suggestions:
            top_sugg = suggestions[0]
            repay_input = {
                "groupId": group_id,
                "fromParticipantId": top_sugg["fromParticipantId"],
                "toParticipantId": top_sugg["toParticipantId"],
                "amount": top_sugg["amount"],
                "reason": "Seed settlement / partial balance resolution",
                "idempotencyKey": f"seed-repayment-{uuid.uuid4()}",
            }
            graphql_mutate(
                """
                mutation RecordRepayment($input: RepaymentInput!) {
                    recordRepayment(input: $input) {
                        id
                        status
                    }
                }
                """,
                variables={"input": repay_input},
                bearer=secondary_token,
            )
            print(f"  ✓ Recorded repayment: {int(top_sugg['amount']['minor'])/100:.2f} {currency}")

    return group_id


def generate_large_scale_data(
    alice_token: str,
    bob_token: str,
    charlie_token: str,
    num_groups: int,
    expenses_per_group: int,
) -> None:
    """Generate large volume dummy data across multiple groups."""
    print(f"\nGenerating large scale dataset ({num_groups} groups, ~{expenses_per_group} expenses each)...")

    group_templates = [
        ("Community Garden Project", "HOUSEHOLD", "EUR"),
        ("Iceland Ring Road Trip", "TRIP", "EUR"),
        ("Tokyo Tech Conference", "TRIP", "USD"),
        ("Berlin Apartment Share", "HOUSEHOLD", "EUR"),
        ("Weekend Camping", "TRIP", "EUR"),
        ("Couple Food & Groceries", "COUPLE", "EUR"),
        ("Startup Founders Shared Flat", "HOUSEHOLD", "USD"),
        ("Lake Tahoe Ski Chalet", "TRIP", "USD"),
    ]

    expense_catalog = [
        ("Whole Foods Groceries", 14550),
        ("High-speed Rail Tickets", 19800),
        ("Airbnb Reservation", 65000),
        ("Restaurant Dinner & Wine", 12400),
        ("Gas & Toll Booths", 8650),
        ("Coffee & Bakery Breakfast", 2450),
        ("Museum & Exhibition Tickets", 4800),
        ("Household Cleaning Supplies", 3590),
        ("Internet & Fiber Bill", 4999),
        ("Electricity & Gas Utility", 11200),
    ]

    for i in range(num_groups):
        template = group_templates[i % len(group_templates)]
        g_name = f"{template[0]} #{i + 1}"
        g_kind = template[1]
        currency = template[2]

        specs: list[dict[str, Any]] = []
        for j in range(expenses_per_group):
            cat = expense_catalog[(i + j) % len(expense_catalog)]
            specs.append({
                "description": f"{cat[0]} ({j + 1})",
                "minor": cat[1] + (j * 100),
                "payer": "primary" if j % 2 == 0 else "secondary",
                "mode": "EQUAL",
            })

        schedules: list[dict[str, Any]] = []
        if g_kind == "HOUSEHOLD":
            schedules.append({
                "description": "Monthly Maintenance Fee",
                "minor": 15000,
                "frequency": "MONTHLY",
                "startDate": "2026-10-01",
                "dayOfMonth": 1,
            })

        seed_group_with_expenses(
            group_name=g_name,
            group_kind=g_kind,
            currency=currency,
            primary_token=alice_token,
            secondary_token=bob_token,
            extra_participants=["charlie", "dave"],
            expense_specs=specs,
            schedules=schedules,
            record_settlement=(i % 2 == 0),
        )


def main() -> int:
    parser = argparse.ArgumentParser(description="Seed realistic development dummy data into Squarewise.")
    parser.add_argument("--reset", action="store_true", help="Truncate existing groups/expenses before seeding")
    parser.add_argument("--large", action="store_true", help="Generate extensive large-scale dummy dataset (50 groups)")
    parser.add_argument("--groups", type=int, default=5, help="Number of standard groups to create")
    parser.add_argument("--expenses", type=int, default=4, help="Number of expenses per group")
    args = parser.parse_args()

    print("=" * 70)
    print("🌱 SQUAREWISE DEVELOPMENT DATA SEEDER")
    print("=" * 70)

    # 1. Reset if requested
    if args.reset:
        reset_dev_data()

    # 2. Acquire Keycloak signed tokens for Alice, Bob, and Charlie
    print("\nAcquiring OIDC tokens from Keycloak...")
    alice_token = acquire_keycloak_token("squarewise-ci", "squarewise-ci-local-only")
    bob_token = acquire_keycloak_token("squarewise-ci-e2e-bob", "squarewise-ci-e2e-bob-local-only")
    charlie_token = acquire_keycloak_token("squarewise-ci-e2e-nonmember", "squarewise-ci-e2e-nonmember-local-only")
    print("  ✓ Keycloak tokens acquired for Alice, Bob, and Charlie.")

    # 3. Synchronize actual Keycloak subjects into personas list
    alice_sub = extract_jwt_sub(alice_token) or PERSONAS[0].subject
    bob_sub = extract_jwt_sub(bob_token) or PERSONAS[1].subject
    charlie_sub = extract_jwt_sub(charlie_token) or PERSONAS[2].subject

    active_personas: list[Persona] = [
        Persona(PERSONAS[0].name, PERSONAS[0].email, PERSONAS[0].keycloak_client, PERSONAS[0].keycloak_secret, PERSONAS[0].account_id, alice_sub),
        Persona(PERSONAS[1].name, PERSONAS[1].email, PERSONAS[1].keycloak_client, PERSONAS[1].keycloak_secret, PERSONAS[1].account_id, bob_sub),
        Persona(PERSONAS[2].name, PERSONAS[2].email, PERSONAS[2].keycloak_client, PERSONAS[2].keycloak_secret, PERSONAS[2].account_id, charlie_sub),
        PERSONAS[3],
        PERSONAS[4],
    ]
    PERSONAS.clear()
    PERSONAS.extend(active_personas)

    # 4. Ensure Postgres account profiles & identities with synchronized subjects
    ensure_postgres_personas(PERSONAS)

    # 5. Standard Realistic Groups
    # Group 1: Apartment 4B - Rent & Living (HOUSEHOLD, EUR)
    seed_group_with_expenses(
        group_name="Apartment 4B - Rent & Living",
        group_kind="HOUSEHOLD",
        currency="EUR",
        primary_token=alice_token,
        secondary_token=bob_token,
        extra_participants=["charlie", "dave"],
        expense_specs=[
            {"description": "Organic Supermarket & Fresh Groceries", "minor": 14250, "payer": "primary", "mode": "EQUAL"},
            {"description": "Weekly Apartment Deep Clean", "minor": 8000, "payer": "secondary", "mode": "EQUAL"},
            {"description": "High-Speed Mesh Wi-Fi Hardware", "minor": 18990, "payer": "primary", "mode": "EQUAL"},
            {"description": "Coffee Beans & Kitchen Essentials", "minor": 3420, "payer": "secondary", "mode": "EQUAL"},
        ],
        schedules=[
            {"description": "Monthly Rent Allocation", "minor": 120000, "frequency": "MONTHLY", "startDate": "2026-10-01", "dayOfMonth": 1},
            {"description": "Bi-weekly Cleaner Service", "minor": 8000, "frequency": "WEEKLY", "startDate": "2026-10-05"},
        ],
        record_settlement=True,
    )

    # Group 2: Dolomites Ski Adventure 2026 (TRIP, EUR)
    seed_group_with_expenses(
        group_name="Dolomites Ski Adventure 2026",
        group_kind="TRIP",
        currency="EUR",
        primary_token=alice_token,
        secondary_token=bob_token,
        extra_participants=["charlie", "eve"],
        expense_specs=[
            {"description": "Alpine Chalet 4 Nights", "minor": 145000, "payer": "primary", "mode": "EQUAL"},
            {"description": "SuperSki 3-Day Lift Passes", "minor": 86000, "payer": "secondary", "mode": "EQUAL"},
            {"description": "Mountain Hut Fondue Dinner", "minor": 22400, "payer": "primary", "mode": "EQUAL"},
            {"description": "Snow Chains & Van Rental Fuel", "minor": 11500, "payer": "secondary", "mode": "EQUAL"},
        ],
        schedules=[],
        record_settlement=True,
    )

    # Group 3: California Road Trip (TRIP, USD)
    seed_group_with_expenses(
        group_name="California Road Trip",
        group_kind="TRIP",
        currency="USD",
        primary_token=bob_token,
        secondary_token=alice_token,
        extra_participants=["dave", "eve"],
        expense_specs=[
            {"description": "Convertible Car Rental (SFO to LAX)", "minor": 68000, "payer": "primary", "mode": "EQUAL"},
            {"description": "Big Sur Campsite & National Park Passes", "minor": 12500, "payer": "secondary", "mode": "EQUAL"},
            {"description": "Monterey Seafood Dinner", "minor": 17850, "payer": "primary", "mode": "EQUAL"},
        ],
        schedules=[],
        record_settlement=False,
    )

    # Group 4: Weekend Gaming & Dinners (HOUSEHOLD, EUR)
    seed_group_with_expenses(
        group_name="Weekend Gaming & Dinners",
        group_kind="HOUSEHOLD",
        currency="EUR",
        primary_token=alice_token,
        secondary_token=bob_token,
        extra_participants=["charlie"],
        expense_specs=[
            {"description": "Board Games & Expansions", "minor": 9500, "payer": "primary", "mode": "EQUAL"},
            {"description": "Artisan Pizza Delivery", "minor": 5600, "payer": "secondary", "mode": "EQUAL"},
            {"description": "Snacks, Craft Drinks & Ice Cream", "minor": 3200, "payer": "primary", "mode": "EQUAL"},
        ],
        schedules=[],
        record_settlement=True,
    )

    # 6. Large dataset generation if requested
    if args.large:
        generate_large_scale_data(
            alice_token=alice_token,
            bob_token=bob_token,
            charlie_token=charlie_token,
            num_groups=args.groups if args.groups > 5 else 45,
            expenses_per_group=args.expenses if args.expenses > 4 else 10,
        )

    print("\n" + "=" * 70)
    print("✅ SEEDING COMPLETED SUCCESSFULLY!")
    print("=" * 70)
    print("\nSummary of Available Personas for Local Development:")
    print("  • Alice   : alice@squarewise.local   (Keycloak client: squarewise-ci)")
    print("  • Bob     : bob@squarewise.local     (Keycloak client: squarewise-ci-e2e-bob)")
    print("  • Charlie : charlie@squarewise.local (Keycloak client: squarewise-ci-e2e-nonmember)")
    print("  • Dave    : dave@squarewise.local")
    print("  • Eve     : eve@squarewise.local")
    print("\nEndpoints Ready to Explore:")
    print("  • BFF GraphQL HTTP & WS : http://localhost:28080/graphql")
    print("  • Accounts REST         : http://localhost:28081/accounts/v1/")
    print("  • Expense Core REST     : http://localhost:28082/expense-core/v1/")
    print("  • Notifications REST    : http://localhost:28083/notifications/v1/")
    print("  • Mailpit Web UI        : http://localhost:28025")
    print("  • RabbitMQ Management   : http://localhost:28673 (user: squarewise / pass: squarewise-local-only)")
    print("=" * 70)
    return 0


if __name__ == "__main__":
    sys.exit(main())
