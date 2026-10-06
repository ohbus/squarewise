"""Verify deployed auth-email delivery admission, suppression, and recovery."""

from __future__ import annotations

import json
import os
import time
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
    """Make one bounded JSON request without exposing credentials or payloads."""
    headers = {CONTENT_TYPE: "application/json"}
    data = json.dumps(body).encode("utf-8") if body is not None else None
    request = Request(url, data=data, headers=headers, method=method)
    try:
        with urlopen(request, timeout=10) as response:
            content = response.read().decode("utf-8")
            return response.status, cast(object, json.loads(content) if content else {})
    except HTTPError as error:
        content = error.read().decode("utf-8")
        return error.code, cast(object, json.loads(content) if content else {})


def recipient_count(recipient: str) -> int:
    """Return the Mailpit message count for one unique recipient."""
    status, payload = request_json(f"{MAILPIT_URL}/api/v1/messages?limit=100")
    if status != 200 or not isinstance(payload, Mapping):
        return 0
    messages = payload.get("messages")
    if not isinstance(messages, list):
        return 0
    count = 0
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
        count += int(recipient in addresses)
    return count


def wait_for_count(recipient: str, expected: int, timeout_seconds: float = 30.0) -> int:
    """Wait for an expected Mailpit delivery count, failing with bounded evidence."""
    deadline = time.monotonic() + timeout_seconds
    observed = 0
    while time.monotonic() < deadline:
        observed = recipient_count(recipient)
        if observed >= expected:
            return observed
        time.sleep(0.5)
    raise AssertionError(f"Mailpit delivery count did not reach {expected}; observed {observed}")


def start_login(recipient: str) -> None:
    """Emit one real Accounts login-start event for the recipient."""
    status, response = request_json(
        f"{ACCOUNTS_URL}/accounts/v1/auth/login/start",
        method="POST",
        body={"email": recipient, "channel": "CODE", "clientKind": "NATIVE"},
    )
    assert status == 202, f"login-start did not accept the test event: HTTP {status} ({response})"


def main() -> int:
    """Prove one-window suppression and post-window recovery through the real stack."""
    assert os.environ.get("SQUAREWISE_AUTH_LOGIN_RESEND_COOLDOWN_SECONDS") == "0", (
        "set SQUAREWISE_AUTH_LOGIN_RESEND_COOLDOWN_SECONDS=0 for this live probe"
    )
    assert os.environ.get("SQUAREWISE_NOTIFICATIONS_DELIVERY_MAX_PERMITS") == "1", (
        "set SQUAREWISE_NOTIFICATIONS_DELIVERY_MAX_PERMITS=1 for this live probe"
    )
    assert os.environ.get("SQUAREWISE_NOTIFICATIONS_DELIVERY_WINDOW_SECONDS") == "5", (
        "set SQUAREWISE_NOTIFICATIONS_DELIVERY_WINDOW_SECONDS=5 for this live probe"
    )

    recipient = f"qa-notification-limit-{int(time.time() * 1000)}@example.com"
    start_login(recipient)
    start_login(recipient)
    assert wait_for_count(recipient, 1) == 1
    assert recipient_count(recipient) == 1, "second same-recipient event bypassed delivery admission"

    time.sleep(5)
    start_login(recipient)
    assert wait_for_count(recipient, 2) == 2
    print("  [ok] auth-email delivery was admitted, suppressed at the Redis limit, and recovered")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
