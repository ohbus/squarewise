"""Verify concurrent refresh rotation and refresh-family reuse revocation."""

from __future__ import annotations

import json
import os
import re
import time
from concurrent.futures import Future, ThreadPoolExecutor
from collections.abc import Mapping
from typing import cast
from urllib.error import HTTPError
from urllib.request import Request, urlopen

from tests.http_constants import CONTENT_TYPE

ACCOUNTS_URL = os.environ.get("SQUAREWISE_ACCOUNTS_URL", "http://localhost:28081")
MAILPIT_URL = os.environ.get("SQUAREWISE_MAILPIT_URL", "http://localhost:28025")


def request_json(
    url: str,
    method: str = "GET",
    body: Mapping[str, object] | None = None,
) -> tuple[int, object]:
    """Execute one JSON request and return status plus decoded response."""
    data = json.dumps(body).encode("utf-8") if body is not None else None
    request = Request(url, data=data, headers={CONTENT_TYPE: "application/json"}, method=method)
    try:
        with urlopen(request, timeout=10) as response:
            raw = response.read().decode("utf-8")
            return response.status, cast(object, json.loads(raw) if raw else {})
    except HTTPError as error:
        raw = error.read().decode("utf-8")
        try:
            return error.code, cast(object, json.loads(raw) if raw else {})
        except json.JSONDecodeError:
            return error.code, raw


def wait_for_credential(recipient: str) -> str:
    """Read the unique Mailpit login code without printing its value."""
    deadline = time.monotonic() + 30
    while time.monotonic() < deadline:
        status, payload = request_json(f"{MAILPIT_URL}/api/v1/messages?limit=100")
        if status == 200 and isinstance(payload, Mapping):
            messages = payload.get("messages")
            if isinstance(messages, list):
                for message in messages:
                    if not isinstance(message, Mapping):
                        continue
                    recipients = message.get("To")
                    if not isinstance(recipients, list):
                        continue
                    addresses = {
                        str(item.get("Address"))
                        for item in recipients
                        if isinstance(item, Mapping) and item.get("Address") is not None
                    }
                    if recipient not in addresses or not message.get("ID"):
                        continue
                    detail_status, detail = request_json(
                        f"{MAILPIT_URL}/api/v1/message/{message['ID']}"
                    )
                    if detail_status == 200 and isinstance(detail, Mapping):
                        text = str(detail.get("Text") or detail.get("text") or "")
                        match = re.search(r"one-time Squarewise sign-in code is:\s*(\S+)", text)
                        if match:
                            return match.group(1)
        time.sleep(0.5)
    raise AssertionError("Mailpit did not receive a usable refresh-concurrency login code")


def refresh(refresh_token: str) -> tuple[int, object]:
    """Submit one refresh request using the same presented token."""
    return request_json(
        f"{ACCOUNTS_URL}/accounts/v1/auth/token/refresh",
        method="POST",
        body={"refreshToken": refresh_token},
    )


def main() -> int:
    """Require one successful rotation, one reuse rejection, and family revocation."""
    recipient = f"qa-refresh-race-{int(time.time() * 1000)}@example.com"
    start_status, _ = request_json(
        f"{ACCOUNTS_URL}/accounts/v1/auth/login/start",
        method="POST",
        body={"email": recipient, "channel": "CODE", "clientKind": "NATIVE"},
    )
    assert start_status == 202, f"login-start failed: HTTP {start_status}"
    credential = wait_for_credential(recipient)
    verify_status, tokens = request_json(
        f"{ACCOUNTS_URL}/accounts/v1/auth/login/verify",
        method="POST",
        body={"credential": credential, "clientKind": "NATIVE"},
    )
    assert verify_status == 200 and isinstance(tokens, Mapping), (
        f"login verification failed: HTTP {verify_status} ({tokens})"
    )
    refresh_token = tokens.get("refreshToken")
    assert isinstance(refresh_token, str) and refresh_token

    with ThreadPoolExecutor(max_workers=2) as executor:
        futures: tuple[Future[tuple[int, object]], Future[tuple[int, object]]] = (
            executor.submit(refresh, refresh_token),
            executor.submit(refresh, refresh_token),
        )
        results = [future.result() for future in futures]

    statuses = [status for status, _ in results]
    assert statuses.count(200) == 1 and statuses.count(401) == 1, (
        f"concurrent refresh did not produce one success and one reuse rejection: {results}"
    )
    winning = next(payload for status, payload in results if status == 200)
    assert isinstance(winning, Mapping)
    child_refresh = winning.get("refreshToken")
    assert isinstance(child_refresh, str) and child_refresh
    family_status, _ = refresh(child_refresh)
    assert family_status == 401, (
        f"refresh-token reuse must revoke the entire family; observed HTTP {family_status}"
    )
    print(f"  [ok] concurrent refresh rotation produced one 200, one 401, then family 401: {statuses}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
