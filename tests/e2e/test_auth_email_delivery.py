"""Deployed passwordless authentication and session-revocation journey."""

from __future__ import annotations

import argparse
import json
import os
from pathlib import Path
import re
import time
from typing import Any
from urllib.error import HTTPError
from urllib.request import Request, urlopen

from tests.http_constants import AUTHORIZATION, CONTENT_TYPE
from tests.e2e.qa10_evidence import write_execution_evidence

ACCOUNTS_URL = os.environ.get("SQUAREWISE_ACCOUNTS_URL", "http://localhost:28081")
MAILPIT_URL = os.environ.get("SQUAREWISE_MAILPIT_URL", "http://localhost:28025")


def request_json(
    url: str,
    method: str = "GET",
    body: Any = None,
    bearer: str | None = None,
) -> tuple[int, Any]:
    """Make a JSON request and return its status and decoded response."""
    headers = {CONTENT_TYPE: "application/json"}
    if bearer:
        headers[AUTHORIZATION] = f"Bearer {bearer}"
    data = json.dumps(body).encode("utf-8") if body is not None else None
    request = Request(url, data=data, headers=headers, method=method)
    try:
        with urlopen(request, timeout=10) as response:
            content = response.read().decode("utf-8")
            return response.status, json.loads(content) if content else {}
    except HTTPError as error:
        content = error.read().decode("utf-8")
        try:
            return error.code, json.loads(content) if content else {}
        except json.JSONDecodeError:
            return error.code, {"raw": content}


def wait_for_credential(recipient: str) -> str:
    """Read the unique delivered login code from Mailpit without logging it."""
    deadline = time.monotonic() + 30
    while time.monotonic() < deadline:
        status, listing = request_json(f"{MAILPIT_URL}/api/v1/messages?limit=50")
        if status == 200 and isinstance(listing, dict):
            for message in listing.get("messages", []):
                recipients = message.get("To", [])
                addresses = {
                    str(item.get("Address", ""))
                    for item in recipients
                    if isinstance(item, dict)
                }
                if recipient not in addresses:
                    continue
                message_id = message.get("ID")
                if not message_id:
                    continue
                detail_status, detail = request_json(
                    f"{MAILPIT_URL}/api/v1/message/{message_id}"
                )
                if detail_status != 200 or not isinstance(detail, dict):
                    continue
                text = str(detail.get("Text") or detail.get("text") or "")
                match = re.search(r"one-time Squarewise sign-in code is:\s*(\S+)", text)
                if match:
                    return match.group(1)
        time.sleep(1)
    raise AssertionError("Mailpit did not receive a usable passwordless login code")


def main(evidence_output: Path | None = None, source_revision: str = "local-worktree", environment: str = "local-compose-oidc") -> int:
    """Verify passwordless redemption, verification throttling, and revocation."""
    recipient = f"qa-e2e-{int(time.time() * 1000)}@example.com"
    start_status, start_response = request_json(
        f"{ACCOUNTS_URL}/accounts/v1/auth/login/start",
        method="POST",
        body={"email": recipient, "channel": "CODE", "clientKind": "NATIVE"},
    )
    assert start_status == 202, f"Accounts startLogin failed: HTTP {start_status} ({start_response})"
    assert start_response.get("status") == "ACCEPTED"

    credential = wait_for_credential(recipient)
    verify_status, tokens = request_json(
        f"{ACCOUNTS_URL}/accounts/v1/auth/login/verify",
        method="POST",
        body={"credential": credential, "clientKind": "NATIVE"},
    )
    assert verify_status == 200, f"Accounts verifyLogin failed: HTTP {verify_status} ({tokens})"
    access_token = tokens.get("accessToken")
    refresh_token = tokens.get("refreshToken")
    assert access_token and refresh_token, "verifyLogin must return access and refresh tokens"

    replay_status, _ = request_json(
        f"{ACCOUNTS_URL}/accounts/v1/auth/login/verify",
        method="POST",
        body={"credential": credential, "clientKind": "NATIVE"},
    )
    assert replay_status == 401, "verifyLogin must reject a replayed one-time credential"

    verification_limit = int(os.environ.get("SQUAREWISE_AUTH_LOGIN_VERIFY_MAX_REQUESTS", "5"))
    invalid_credential = f"{credential}-invalid"
    invalid_statuses = [
        request_json(
            f"{ACCOUNTS_URL}/accounts/v1/auth/login/verify",
            method="POST",
            body={"credential": invalid_credential, "clientKind": "NATIVE"},
        )[0]
        for _ in range(verification_limit)
    ]
    assert invalid_statuses == [401] * verification_limit, (
        "invalid verification attempts must remain generic before the limit: "
        f"observed {invalid_statuses}"
    )
    verification_denial_status, verification_denial = request_json(
        f"{ACCOUNTS_URL}/accounts/v1/auth/login/verify",
        method="POST",
        body={"credential": invalid_credential, "clientKind": "NATIVE"},
    )
    assert verification_denial_status == 429, (
        "verification admission must fail closed with HTTP 429 after the configured window is exhausted"
    )
    assert isinstance(verification_denial, dict)
    assert verification_denial.get("code") == "RATE_LIMITED", (
        "verification denial must retain the structured RATE_LIMITED error code"
    )

    logout_status, logout_response = request_json(
        f"{ACCOUNTS_URL}/accounts/v1/auth/logout",
        method="POST",
        body={"refreshToken": refresh_token},
        bearer=access_token,
    )
    assert logout_status == 204, f"Accounts logout failed: HTTP {logout_status} ({logout_response})"

    refresh_status, _ = request_json(
        f"{ACCOUNTS_URL}/accounts/v1/auth/token/refresh",
        method="POST",
        body={"refreshToken": refresh_token},
    )
    assert refresh_status == 401, "logout must revoke the refresh-token family"

    replay_logout_status, replay_logout_response = request_json(
        f"{ACCOUNTS_URL}/accounts/v1/auth/logout",
        method="POST",
        body={"refreshToken": refresh_token},
        bearer=access_token,
    )
    assert replay_logout_status == 204, (
        "replaying logout for an already revoked family must remain idempotent: "
        f"HTTP {replay_logout_status} ({replay_logout_response})"
    )
    print(
        "  [ok] startLogin delivered, verifyLogin redeemed once, replay was rejected, "
        "verification attempts were throttled, logout revoked refresh, and logout replay was idempotent"
    )
    if evidence_output is not None:
        artifact = str(evidence_output)
        write_execution_evidence(
            evidence_output,
            source_revision,
            environment,
            [
                {
                    "surface": "REST",
                    "operation": "startLogin",
                    "status": "passed",
                    "artifact": artifact,
                    "assertions": [
                        "passwordless login request was accepted",
                        "one-time credential was delivered through Mailpit without logging its value",
                    ],
                },
                {
                    "surface": "REST",
                    "operation": "verifyLogin",
                    "status": "passed",
                    "artifact": artifact,
                    "assertions": [
                        "delivered credential returned access and refresh tokens",
                        "replayed one-time credential returned HTTP 401",
                        "invalid verification attempts remained HTTP 401 until the configured cap",
                        "the next verification attempt returned HTTP 429",
                    ],
                },
                {
                    "surface": "REST",
                    "operation": "logout",
                    "status": "passed",
                    "artifact": artifact,
                    "assertions": [
                        "logout returned 204 and revoked the refresh-token family",
                        "replayed logout remained idempotent with HTTP 204",
                    ],
                },
                {
                    "surface": "REST",
                    "operation": "refreshToken",
                    "status": "passed",
                    "artifact": artifact,
                    "assertions": ["refresh after logout returned HTTP 401"],
                },
            ],
        )
    return 0


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description="Run passwordless authentication and session revocation checks")
    parser.add_argument("--evidence-output", type=Path, help="write QA-10 operation evidence after success")
    parser.add_argument("--source-revision", default=os.environ.get("GITHUB_SHA", "local-worktree"))
    parser.add_argument(
        "--environment",
        default=os.environ.get("QA10_E2E_ENVIRONMENT", "local-compose-oidc"),
    )
    arguments = parser.parse_args()
    raise SystemExit(main(arguments.evidence_output, arguments.source_revision, arguments.environment))
