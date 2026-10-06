"""Verify auth-email delivery fails closed when Notifications loses Redis."""

from __future__ import annotations

import json
import os
import subprocess
import time
from collections.abc import Mapping
from typing import Final, cast
from urllib.error import HTTPError
from urllib.request import Request, urlopen

from tests.http_constants import CONTENT_TYPE

COMPOSE_FILE: Final[str] = os.environ.get(
    "SQUAREWISE_COMPOSE_FILE", "infra/local/docker-compose.dev.yml"
)
COMPOSE_ENV_FILE: Final[str] = os.environ.get("SQUAREWISE_COMPOSE_ENV_FILE", "")
COMPOSE_PROJECT: Final[str] = os.environ.get("SQUAREWISE_COMPOSE_PROJECT", "")
REDIS_PASSWORD: Final[str] = os.environ.get("REDIS_PASSWORD", "squarewise-redis-local-only")
ACCOUNTS_URL: Final[str] = os.environ.get("SQUAREWISE_ACCOUNTS_URL", "http://localhost:28081")
NOTIFICATIONS_URL: Final[str] = os.environ.get("SQUAREWISE_NOTIFICATIONS_URL", "http://localhost:28083")
MAILPIT_URL: Final[str] = os.environ.get("SQUAREWISE_MAILPIT_URL", "http://localhost:28025")
AUTH_EMAIL_QUEUE: Final[str] = "squarewise.auth-email.v2"


def compose(*arguments: str) -> str:
    """Run a Compose command against the selected local topology."""
    command = ["docker", "compose"]
    if COMPOSE_PROJECT:
        command.extend(("--project-name", COMPOSE_PROJECT))
    if COMPOSE_ENV_FILE:
        command.extend(("--env-file", COMPOSE_ENV_FILE))
    command.extend(("-f", COMPOSE_FILE, *arguments))
    completed = subprocess.run(command, check=True, capture_output=True, text=True)
    return completed.stdout.strip()


def request_json(
    url: str,
    method: str = "GET",
    body: Mapping[str, object] | None = None,
) -> tuple[int, object]:
    """Make one bounded JSON request without logging credentials or payloads."""
    request = Request(
        url,
        data=json.dumps(body).encode("utf-8") if body is not None else None,
        headers={CONTENT_TYPE: "application/json"},
        method=method,
    )
    try:
        with urlopen(request, timeout=10) as response:
            raw = response.read().decode("utf-8")
            return response.status, cast(object, json.loads(raw) if raw else {})
    except HTTPError as error:
        raw = error.read().decode("utf-8")
        return error.code, cast(object, json.loads(raw) if raw else {})


def start_login(recipient: str) -> None:
    """Queue one real passwordless auth-email event through Accounts."""
    status, response = request_json(
        f"{ACCOUNTS_URL}/accounts/v1/auth/login/start",
        method="POST",
        body={"email": recipient, "channel": "CODE", "clientKind": "NATIVE"},
    )
    if status != 202:
        raise AssertionError(f"login-start did not accept the test event: HTTP {status} ({response})")


def recipient_count(recipient: str) -> int:
    """Count Mailpit messages addressed to one unique recipient."""
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
        if any(
            isinstance(item, Mapping) and item.get("Address") == recipient
            for item in recipients
        ):
            count += 1
    return count


def queue_count(queue: str) -> int:
    """Return ready plus unacknowledged messages for one RabbitMQ queue."""
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
            return int(fields[1]) + int(fields[2])
    return 0


def container_running(service: str) -> bool:
    """Return the authoritative running state for one Compose service."""
    container_id = compose("ps", "-q", service)
    if not container_id:
        return False
    completed = subprocess.run(
        ["docker", "inspect", "--format={{.State.Running}}", container_id],
        check=True,
        capture_output=True,
        text=True,
    )
    return completed.stdout.strip().lower() == "true"


def start_container(service: str) -> None:
    """Start one existing container without implicitly starting dependencies."""
    container_id = compose("ps", "-aq", service)
    if not container_id:
        raise AssertionError(f"Compose service {service} has no existing container")
    subprocess.run(["docker", "start", container_id], check=True, capture_output=True, text=True)


def wait_for_container_state(service: str, expected: bool, timeout_seconds: float = 30.0) -> None:
    """Wait for a Compose service to reach the requested process state."""
    deadline = time.monotonic() + timeout_seconds
    while time.monotonic() < deadline:
        if container_running(service) == expected:
            return
        time.sleep(0.5)
    raise AssertionError(f"Compose service {service} did not reach running={expected}")


def wait_for_queue_increase(queue: str, baseline: int, timeout_seconds: float = 30.0) -> int:
    """Wait for one queued/dead-lettered event without assuming an empty broker."""
    deadline = time.monotonic() + timeout_seconds
    observed = baseline
    while time.monotonic() < deadline:
        observed = queue_count(queue)
        if observed > baseline:
            return observed
        time.sleep(0.5)
    raise AssertionError(f"{queue} did not increase above {baseline}; observed {observed}")


def wait_for_queue_at_most(queue: str, maximum: int, timeout_seconds: float = 30.0) -> int:
    """Wait until a queued event has been consumed or otherwise settled."""
    deadline = time.monotonic() + timeout_seconds
    observed = maximum + 1
    while time.monotonic() < deadline:
        observed = queue_count(queue)
        if observed <= maximum:
            return observed
        time.sleep(0.5)
    raise AssertionError(f"{queue} did not settle at or below {maximum}; observed {observed}")


def wait_for_delivery(recipient: str, expected: int, timeout_seconds: float = 30.0) -> int:
    """Wait for the expected Mailpit delivery count."""
    deadline = time.monotonic() + timeout_seconds
    observed = 0
    while time.monotonic() < deadline:
        observed = recipient_count(recipient)
        if observed >= expected:
            return observed
        time.sleep(0.5)
    raise AssertionError(f"Mailpit delivery did not reach {expected}; observed {observed}")


def wait_for_redis(timeout_seconds: float = 30.0) -> None:
    """Wait until authenticated Redis commands succeed after restart."""
    deadline = time.monotonic() + timeout_seconds
    while time.monotonic() < deadline:
        try:
            if compose(
                "exec",
                "-T",
                "redis",
                "redis-cli",
                "-a",
                REDIS_PASSWORD,
                "--no-auth-warning",
                "PING",
            ) == "PONG":
                return
        except subprocess.CalledProcessError:
            pass
        time.sleep(0.5)
    raise AssertionError("Redis did not recover within the bounded probe timeout")


def wait_for_accounts(timeout_seconds: float = 30.0) -> None:
    """Wait until Accounts has re-established its Redis-backed readiness path."""
    deadline = time.monotonic() + timeout_seconds
    while time.monotonic() < deadline:
        try:
            with urlopen(f"{ACCOUNTS_URL}/actuator/health/readiness", timeout=5) as response:
                if response.status == 200:
                    return
        except (HTTPError, OSError):
            pass
        time.sleep(0.5)
    raise AssertionError("Accounts did not recover Redis-backed readiness within the probe timeout")


def wait_for_notifications_liveness(timeout_seconds: float = 60.0) -> None:
    """Wait until restarted Notifications completes application startup."""
    deadline = time.monotonic() + timeout_seconds
    while time.monotonic() < deadline:
        try:
            with urlopen(f"{NOTIFICATIONS_URL}/actuator/health/liveness", timeout=5) as response:
                if response.status == 200:
                    return
        except (HTTPError, OSError):
            pass
        time.sleep(0.5)
    raise AssertionError("Notifications did not reach liveness within the probe timeout")


def clear_rate_limit_namespace() -> None:
    """Clear only disposable limiter keys before the isolated local probe."""
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
        "squarewise:rl:v1:*",
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


def main() -> int:
    """Prove no auth email is dispatched during Redis outage and recovery works."""
    outage_recipient = f"qa-notification-redis-outage-{int(time.time() * 1000)}@example.com"
    recovery_recipient = f"qa-notification-redis-recovery-{int(time.time() * 1000)}@example.com"
    baseline_queue = queue_count(AUTH_EMAIL_QUEUE)
    try:
        wait_for_redis()
        wait_for_accounts()
        clear_rate_limit_namespace()
        time.sleep(1)
        # Kill the consumer so the queued event cannot be acknowledged during
        # graceful shutdown before Redis is taken offline.
        compose("kill", "notifications")
        wait_for_container_state("notifications", False)
        start_login(outage_recipient)
        wait_for_queue_increase(AUTH_EMAIL_QUEUE, baseline_queue)

        # Stop the existing Redis container while preserving its Compose
        # identity/IP so other services can reconnect during recovery.
        compose("stop", "redis")
        wait_for_container_state("redis", False)
        start_container("notifications")
        wait_for_container_state("notifications", True)
        wait_for_notifications_liveness()
        wait_for_queue_at_most(AUTH_EMAIL_QUEUE, baseline_queue, timeout_seconds=60.0)
        if recipient_count(outage_recipient) != 0:
            raise AssertionError("auth email was dispatched while Notifications Redis was unavailable")

        compose("start", "redis")
        wait_for_redis()
        wait_for_accounts()
        start_login(recovery_recipient)
        wait_for_delivery(recovery_recipient, 1)
        print("  [ok] Notifications dead-lettered auth email during Redis outage without dispatch, then recovered")
        return 0
    finally:
        compose("start", "redis")
        compose("start", "notifications")


if __name__ == "__main__":
    raise SystemExit(main())
