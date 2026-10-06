"""Verify general notification delivery admission through RabbitMQ and Mailpit."""

from __future__ import annotations

import base64
import json
import os
import time
import uuid
from collections.abc import Mapping
from typing import cast
from urllib.error import HTTPError
from urllib.request import Request, urlopen

from tests.http_constants import CONTENT_TYPE

MANAGEMENT_URL = os.environ.get("SQUAREWISE_RABBITMQ_MANAGEMENT_URL", "http://localhost:28673")
MAILPIT_URL = os.environ.get("SQUAREWISE_MAILPIT_URL", "http://localhost:28025")
RABBIT_USER = os.environ.get("RABBITMQ_DEFAULT_USER", "squarewise")
RABBIT_PASSWORD = os.environ.get("RABBITMQ_DEFAULT_PASS", "squarewise-local-only")
EVENT_EXCHANGE = os.environ.get("SQUAREWISE_EVENTS_EXCHANGE", "squarewise.events")


def request_json(
    url: str,
    method: str = "GET",
    body: Mapping[str, object] | None = None,
    headers: Mapping[str, str] | None = None,
) -> tuple[int, object]:
    """Make one bounded JSON request and return its status and decoded body."""
    request_headers = {CONTENT_TYPE: "application/json"}
    if headers:
        request_headers.update(headers)
    payload = json.dumps(body).encode("utf-8") if body is not None else None
    request = Request(url, data=payload, headers=request_headers, method=method)
    try:
        with urlopen(request, timeout=10) as response:
            content = response.read().decode("utf-8")
            return response.status, cast(object, json.loads(content) if content else {})
    except HTTPError as error:
        content = error.read().decode("utf-8", errors="replace")
        return error.code, content


def management_headers() -> dict[str, str]:
    """Build RabbitMQ management authentication headers without logging credentials."""
    encoded = base64.b64encode(f"{RABBIT_USER}:{RABBIT_PASSWORD}".encode("utf-8")).decode("ascii")
    return {"Authorization": f"Basic {encoded}"}


def publish_general_event(recipient: str) -> None:
    """Publish one valid domain envelope to the durable notification exchange."""
    group_id = str(uuid.uuid4())
    envelope: dict[str, object] = {
        "eventId": str(uuid.uuid4()),
        "eventType": "expense.created",
        "schemaVersion": 1,
        "aggregateId": str(uuid.uuid4()),
        "groupId": group_id,
        "groupRevision": 1,
        "occurredAt": "2026-10-06T00:00:00Z",
        "payload": {"subject": recipient, "message": "general notification admission probe"},
    }
    body: dict[str, object] = {
        "properties": {"content_type": "application/json"},
        "routing_key": "expense.created.v1",
        "payload": json.dumps(envelope, separators=(",", ":")),
        "payload_encoding": "string",
    }
    status, response = request_json(
        f"{MANAGEMENT_URL}/api/exchanges/%2F/{EVENT_EXCHANGE}/publish",
        method="POST",
        body=body,
        headers=management_headers(),
    )
    assert status == 200 and isinstance(response, Mapping) and response.get("routed") is True, (
        f"RabbitMQ did not route the general notification event: HTTP {status}"
    )


def recipient_count(recipient: str) -> int:
    """Count Mailpit messages addressed to one unique probe recipient."""
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
        count += sum(
            1
            for address in recipients
            if isinstance(address, Mapping) and address.get("Address") == recipient
        )
    return count


def wait_for_count(recipient: str, expected: int, timeout_seconds: float = 20.0) -> int:
    """Wait for a provider delivery count while bounding the live probe."""
    deadline = time.monotonic() + timeout_seconds
    while time.monotonic() < deadline:
        observed = recipient_count(recipient)
        if observed >= expected:
            return observed
        time.sleep(0.5)
    raise AssertionError(f"Mailpit delivery count did not reach {expected}")


def main() -> int:
    """Prove general notification suppression and post-window recovery."""
    assert os.environ.get("SQUAREWISE_NOTIFICATIONS_DELIVERY_MAX_PERMITS") == "1", (
        "set SQUAREWISE_NOTIFICATIONS_DELIVERY_MAX_PERMITS=1 for this live probe"
    )
    assert os.environ.get("SQUAREWISE_NOTIFICATIONS_DELIVERY_WINDOW_SECONDS") == "2", (
        "set SQUAREWISE_NOTIFICATIONS_DELIVERY_WINDOW_SECONDS=2 for this live probe"
    )

    recipient = f"qa-general-notification-{uuid.uuid4().hex}@example.com"
    publish_general_event(recipient)
    first = wait_for_count(recipient, 1)

    publish_general_event(recipient)
    time.sleep(1)
    second = recipient_count(recipient)
    assert second == first, "second general notification bypassed delivery admission"

    time.sleep(2)
    publish_general_event(recipient)
    third = wait_for_count(recipient, 2)
    assert third == 2, f"general notification did not recover after expiry: {third}"
    print("  [ok] general notification delivery was admitted, suppressed, and recovered")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
