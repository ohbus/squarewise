"""Verify login admission is shared by two Accounts processes through Redis."""

from __future__ import annotations

import json
import os
import subprocess
import time
import uuid
from concurrent.futures import ThreadPoolExecutor
from collections.abc import Mapping
from typing import Final
from urllib.error import HTTPError
from urllib.request import Request, urlopen

from tests.http_constants import CONTENT_TYPE

REPLICA_URLS: Final[tuple[str, str]] = (
    os.environ.get("SQUAREWISE_ACCOUNTS_REPLICA_A_URL", "http://localhost:8084"),
    os.environ.get("SQUAREWISE_ACCOUNTS_REPLICA_B_URL", "http://localhost:8085"),
)
COMPOSE_FILE: Final[str] = os.environ.get("SQUAREWISE_COMPOSE_FILE", "infra/local/docker-compose.dev.yml")
COMPOSE_PROJECT: Final[str] = os.environ.get("SQUAREWISE_COMPOSE_PROJECT", "")
REDIS_PASSWORD: Final[str] = os.environ.get("REDIS_PASSWORD", "squarewise-redis-local-only")


def request_json(url: str, body: Mapping[str, object]) -> tuple[int, object]:
    """Start one passwordless login request against one replica."""
    request = Request(
        f"{url}/accounts/v1/auth/login/start",
        data=json.dumps(body).encode("utf-8"),
        headers={CONTENT_TYPE: "application/json"},
        method="POST",
    )
    try:
        with urlopen(request, timeout=10) as response:
            return response.status, json.loads(response.read().decode("utf-8"))
    except HTTPError as error:
        raw = error.read().decode("utf-8")
        try:
            return error.code, json.loads(raw)
        except json.JSONDecodeError:
            return error.code, raw


def compose(*arguments: str) -> str:
    """Run a Compose command against the selected disposable topology."""
    command: list[str] = ["docker", "compose"]
    if COMPOSE_PROJECT:
        command.extend(("--project-name", COMPOSE_PROJECT))
    command.extend(("-f", COMPOSE_FILE, *arguments))
    completed = subprocess.run(command, check=True, capture_output=True, text=True)
    return completed.stdout.strip()


def wait_for_redis() -> None:
    """Wait until Redis accepts authenticated commands after restart."""
    for _ in range(20):
        try:
            if compose(
                "exec", "-T", "redis", "redis-cli", "-a", REDIS_PASSWORD,
                "--no-auth-warning", "PING"
            ) == "PONG":
                return
        except subprocess.CalledProcessError:
            time.sleep(0.5)
    raise AssertionError("Redis did not recover for the Accounts replica probe")


def main() -> int:
    """Prove sequential and concurrent login windows are global across two instances."""
    assert os.environ.get("SQUAREWISE_AUTH_LOGIN_RESEND_COOLDOWN_SECONDS") == "0", (
        "set SQUAREWISE_AUTH_LOGIN_RESEND_COOLDOWN_SECONDS=0 for this live probe"
    )
    recipient = f"qa-login-replica-{uuid.uuid4()}@example.com"
    statuses = [
        request_json(
            REPLICA_URLS[index % len(REPLICA_URLS)],
            {"email": recipient, "channel": "CODE", "clientKind": "NATIVE"},
        )[0]
        for index in range(6)
    ]
    assert statuses.count(202) == 5, f"expected five global admissions, observed {statuses}"
    assert statuses.count(429) == 1, f"expected one global denial, observed {statuses}"
    print(f"  [ok] shared login admission across two replicas: {statuses}")

    concurrent_recipient = f"qa-login-concurrent-{uuid.uuid4()}@example.com"
    with ThreadPoolExecutor(max_workers=10) as executor:
        futures = [
            executor.submit(
                request_json,
                REPLICA_URLS[index % len(REPLICA_URLS)],
                {"email": concurrent_recipient, "channel": "CODE", "clientKind": "NATIVE"},
            )
            for index in range(10)
        ]
        concurrent_statuses = [future.result()[0] for future in futures]
    assert concurrent_statuses.count(202) == 5, (
        f"expected five atomic concurrent admissions, observed {concurrent_statuses}"
    )
    assert concurrent_statuses.count(429) == 5, (
        f"expected five atomic concurrent denials, observed {concurrent_statuses}"
    )
    print(f"  [ok] atomic concurrent login admission across two replicas: {concurrent_statuses}")

    outage_recipient = f"qa-login-outage-{uuid.uuid4()}@example.com"
    try:
        compose("stop", "redis")
        outage_statuses = [
            request_json(
                REPLICA_URLS[index],
                {"email": outage_recipient, "channel": "CODE", "clientKind": "NATIVE"},
            )[0]
            for index in range(len(REPLICA_URLS))
        ]
        assert outage_statuses == [429] * len(REPLICA_URLS), (
            f"expected fail-closed login admission from every replica, observed {outage_statuses}"
        )
        print(f"  [ok] login admission outage across both replicas: {outage_statuses}")
    finally:
        compose("start", "redis")
        wait_for_redis()

    recovery_statuses = [
        request_json(
            REPLICA_URLS[index],
            {"email": f"qa-login-recovery-{uuid.uuid4()}@example.com", "channel": "CODE", "clientKind": "NATIVE"},
        )[0]
        for index in range(len(REPLICA_URLS))
    ]
    assert recovery_statuses == [202] * len(REPLICA_URLS), (
        f"expected login admission recovery on every replica, observed {recovery_statuses}"
    )
    print(f"  [ok] login admission recovery across both replicas: {recovery_statuses}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
