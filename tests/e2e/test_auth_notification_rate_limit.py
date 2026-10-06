"""Verify deployed auth-email delivery admission, suppression, and recovery."""

from __future__ import annotations

import json
import os
import subprocess
import time
from collections.abc import Mapping
from typing import Final, cast
from urllib.error import HTTPError, URLError
from urllib.request import Request, urlopen

from tests.http_constants import CONTENT_TYPE

COMPOSE_FILE: Final[str] = os.environ.get(
    "SQUAREWISE_COMPOSE_FILE", "infra/local/docker-compose.dev.yml"
)
COMPOSE_PROJECT: Final[str] = os.environ.get("SQUAREWISE_COMPOSE_PROJECT", "")
REDIS_PASSWORD: Final[str] = os.environ.get(
    "REDIS_PASSWORD", "squarewise-redis-local-only"
)
ACCOUNTS_URL: Final[str] = os.environ.get("SQUAREWISE_ACCOUNTS_URL", "http://localhost:28081")
NOTIFICATIONS_URL: Final[str] = os.environ.get(
    "SQUAREWISE_NOTIFICATIONS_URL", "http://localhost:28083"
)
MAILPIT_URL: Final[str] = os.environ.get("SQUAREWISE_MAILPIT_URL", "http://localhost:28025")
RATE_LIMIT_KEY_PATTERN: Final[str] = "squarewise:rl:v1:*"


def compose(*arguments: str) -> str:
    """Run a Compose command against the explicitly selected local topology."""
    command: list[str] = ["docker", "compose"]
    if COMPOSE_PROJECT:
        command.extend(("--project-name", COMPOSE_PROJECT))
    command.extend(("-f", COMPOSE_FILE, *arguments))
    completed = subprocess.run(command, check=True, capture_output=True, text=True)
    return completed.stdout.strip()


def clear_rate_limit_namespace() -> None:
    """Clear disposable limiter keys before running the isolated delivery test."""
    try:
        raw_keys = compose(
            "exec",
            "-T",
            "redis",
            "redis-cli",
            "-a",
            REDIS_PASSWORD,
            "--no-auth-warning",
            "--scan",
            "--pattern",
            RATE_LIMIT_KEY_PATTERN,
        )
        keys = [key for key in raw_keys.splitlines() if key]
        if keys:
            compose(
                "exec",
                "-T",
                "redis",
                "redis-cli",
                "-a",
                REDIS_PASSWORD,
                "--no-auth-warning",
                "DEL",
                *keys,
            )
    except (subprocess.CalledProcessError, FileNotFoundError):
        pass


def wait_for_redis(timeout_seconds: float = 30.0) -> None:
    """Wait until Redis accepts authenticated commands after a restart or outage."""
    deadline = time.monotonic() + timeout_seconds
    while time.monotonic() < deadline:
        try:
            output = compose(
                "exec",
                "-T",
                "redis",
                "redis-cli",
                "-a",
                REDIS_PASSWORD,
                "--no-auth-warning",
                "PING",
            )
            if output == "PONG":
                return
        except subprocess.CalledProcessError:
            pass
        time.sleep(0.5)
    raise AssertionError("Redis did not become ready within the bounded probe timeout")


def wait_for_service_readiness(timeout_seconds: float = 60.0) -> None:
    """Wait until Accounts, Notifications, and Redis have established readiness."""
    wait_for_redis(min(timeout_seconds, 30.0))
    deadline = time.monotonic() + timeout_seconds
    urls = (
        f"{ACCOUNTS_URL}/actuator/health/readiness",
        f"{NOTIFICATIONS_URL}/actuator/health/readiness",
    )
    while time.monotonic() < deadline:
        try:
            ready = True
            for url in urls:
                with urlopen(url, timeout=3) as response:
                    ready = ready and response.status == 200
            if ready:
                return
        except (HTTPError, OSError, URLError):
            pass
        time.sleep(0.5)
    raise AssertionError("Services did not reach readiness before auth notification limit test")


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


def recent_mailpit_messages() -> list[str]:
    """Return diagnostic summary of recent Mailpit messages."""
    status, payload = request_json(f"{MAILPIT_URL}/api/v1/messages?limit=20")
    if status != 200 or not isinstance(payload, Mapping):
        return []
    messages = payload.get("messages")
    if not isinstance(messages, list):
        return []
    summaries: list[str] = []
    for message in messages:
        if isinstance(message, Mapping):
            recipients = message.get("To")
            addrs = []
            if isinstance(recipients, list):
                for item in recipients:
                    if isinstance(item, Mapping) and item.get("Address"):
                        addrs.append(str(item.get("Address")))
            summaries.append(f"To: {','.join(addrs)}")
    return summaries


def rabbitmq_queue_info(queue: str) -> str:
    """Return ready and unacknowledged count for a RabbitMQ queue for diagnostics."""
    try:
        output = compose(
            "exec",
            "-T",
            "rabbitmq",
            "rabbitmqctl",
            "list_queues",
            "-q",
            "name",
            "messages_ready",
            "messages_unacknowledged",
        )
        for line in output.splitlines():
            fields = line.split()
            if len(fields) == 3 and fields[0] == queue:
                return f"{queue}: ready={fields[1]}, unacked={fields[2]}"
    except (subprocess.CalledProcessError, FileNotFoundError):
        pass
    return f"{queue}: unknown"


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


def wait_for_count(recipient: str, expected: int, timeout_seconds: float = 45.0) -> int:
    """Wait for an expected Mailpit delivery count, failing with bounded evidence."""
    deadline = time.monotonic() + timeout_seconds
    observed = 0
    while time.monotonic() < deadline:
        observed = recipient_count(recipient)
        if observed >= expected:
            return observed
        time.sleep(0.5)
    recent = recent_mailpit_messages()
    q_main = rabbitmq_queue_info("squarewise.auth-email.v2")
    q_dlq = rabbitmq_queue_info("squarewise.auth-email.v2.dlq")
    raise AssertionError(
        f"Mailpit delivery count did not reach {expected}; observed {observed} for {recipient}. "
        f"Recent mailpit: {recent}. Queue state: {q_main}, {q_dlq}"
    )


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

    wait_for_service_readiness()
    clear_rate_limit_namespace()

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
