"""Exercise Redis rate-limit eviction, outage, and restart behavior."""

from __future__ import annotations

import json
import io
import os
import re
import subprocess
import sys
import time
import uuid
from urllib.error import HTTPError, URLError
from urllib.request import Request, urlopen
from typing import Final


COMPOSE_FILE: Final[str] = os.environ.get(
    "SQUAREWISE_COMPOSE_FILE", "infra/local/docker-compose.dev.yml"
)
COMPOSE_PROJECT: Final[str] = os.environ.get("SQUAREWISE_COMPOSE_PROJECT", "")
REDIS_PASSWORD: Final[str] = os.environ.get(
    "REDIS_PASSWORD", "squarewise-redis-local-only"
)
ACCOUNTS_URL: Final[str] = os.environ.get(
    "ACCOUNTS_URL", os.environ.get("SQUAREWISE_ACCOUNTS_URL", "http://localhost:28081")
)
BEARER_TOKEN: Final[str] = os.environ.get("BEARER_TOKEN", "")
PROMETHEUS_PATH: Final[str] = "/actuator/prometheus"
BEARER_TOKEN: Final[str] = os.environ.get("BEARER_TOKEN", "")
PROMETHEUS_PATH: Final[str] = "/actuator/prometheus"
REFRESH_PATH: Final[str] = "/accounts/v1/auth/token/refresh"
LOGIN_START_PATH: Final[str] = "/accounts/v1/auth/login/start"
PROBE_TOKEN: Final[str] = "auth-cache-resilience-invalid-refresh"
RATE_LIMIT_KEY_PATTERN: Final[str] = "squarewise:rl:v1:*"


def compose(*arguments: str) -> str:
    """Run a Compose command against the explicitly selected local topology."""
    command: list[str] = ["docker", "compose"]
    if COMPOSE_PROJECT:
        command.extend(("--project-name", COMPOSE_PROJECT))
    command.extend(("-f", COMPOSE_FILE, *arguments))
    completed = subprocess.run(
        command,
        check=True,
        capture_output=True,
        text=True,
    )
    return completed.stdout.strip()


def redis_cli(*arguments: str) -> str:
    """Run redis-cli inside the dedicated Compose Redis container."""
    return compose(
        "exec",
        "-T",
        "redis",
        "redis-cli",
        "-a",
        REDIS_PASSWORD,
        "--no-auth-warning",
        *arguments,
    )


def refresh_status(timeout: float = 8.0) -> int:
    """Return the Accounts refresh response status for an intentionally invalid token."""
    body = json.dumps({"refreshToken": PROBE_TOKEN}).encode("utf-8")
    request = Request(
        f"{ACCOUNTS_URL}{REFRESH_PATH}",
        data=body,
        headers={"Content-Type": "application/json"},
        method="POST",
    )
    try:
        with urlopen(request, timeout=timeout) as response:
            return response.status
    except HTTPError as error:
        return error.code


def login_start_status(email: str, timeout: float = 8.0) -> int:
    """Return the passwordless login-start response status for a fresh probe email."""
    body = json.dumps({"email": email}).encode("utf-8")
    request = Request(
        f"{ACCOUNTS_URL}{LOGIN_START_PATH}",
        data=body,
        headers={"Content-Type": "application/json"},
        method="POST",
    )
    try:
        with urlopen(request, timeout=timeout) as response:
            return response.status
    except HTTPError as error:
        return error.code


def evict_rate_limit_keys() -> int:
    """Delete only Squarewise rate-limit keys and return the number removed."""
    raw_keys = redis_cli("--scan", "--pattern", RATE_LIMIT_KEY_PATTERN)
    keys = [key for key in raw_keys.splitlines() if key]
    if keys:
        redis_cli("DEL", *keys)
    return len(keys)


def rate_limit_store_error_counts() -> dict[str, int]:
    """Read bounded login/refresh store-error counters when a probe token is available."""
    if not BEARER_TOKEN:
        return {}
    request = Request(
        f"{ACCOUNTS_URL}{PROMETHEUS_PATH}",
        headers={"Authorization": f"Bearer {BEARER_TOKEN}"},
    )
    with urlopen(request, timeout=10) as response:
        payload = response.read().decode("utf-8")
    counts: dict[str, int] = {}
    for line in payload.splitlines():
        if "squarewise_rate_limit_decisions_total" not in line:
            continue
        if 'outcome="store_error"' not in line:
            continue
        policy_match = re.search(r'policy="([^"]+)"', line)
        value_match = re.search(r"\s([0-9]+(?:\.[0-9]+)?)$", line)
        if policy_match and value_match:
            counts[policy_match.group(1)] = int(float(value_match.group(1)))
    return counts


def wait_for_redis() -> None:
    """Wait until Redis accepts authenticated commands after a restart."""
    deadline = time.monotonic() + 20.0
    while time.monotonic() < deadline:
        try:
            if redis_cli("PING") == "PONG":
                return
        except subprocess.CalledProcessError:
            time.sleep(0.5)
    raise TimeoutError("Redis did not become ready after restart")


def expect(label: str, actual: int, expected: int) -> None:
    """Assert and print one HTTP result from the cache-resilience matrix."""
    if actual != expected:
        raise AssertionError(f"{label}: expected HTTP {expected}, got {actual}")
    print(f"  ✓ {label}: HTTP {actual}")


def main() -> int:
    """Verify eviction resets admission, outage fails closed, and restart recovers."""
    if isinstance(sys.stdout, io.TextIOWrapper):
        sys.stdout.reconfigure(encoding="utf-8")
    print("Running authentication cache resilience checks")
    probe_email = f"redis-outage-{uuid.uuid4()}@squarewise.local"
    evict_rate_limit_keys()
    try:
        for _ in range(10):
            expect("refresh admission before rate limit", refresh_status(), 401)
        expect("refresh admission rate limit", refresh_status(), 429)

        removed = evict_rate_limit_keys()
        if removed == 0:
            raise AssertionError("rate-limit eviction removed no active cache key")
        expect("refresh after rate-limit cache eviction", refresh_status(), 401)

        metric_before = rate_limit_store_error_counts()
        compose("stop", "redis")
        expect("refresh with Redis unavailable", refresh_status(), 429)
        expect("login start with Redis unavailable", login_start_status(probe_email), 429)
        metric_after = rate_limit_store_error_counts()
        if BEARER_TOKEN:
            for policy in ("auth-refresh", "auth-login"):
                if metric_after.get(policy, 0) <= metric_before.get(policy, 0):
                    raise AssertionError(
                        f"{policy} store_error metric did not increase during outage"
                    )

        compose("start", "redis")
        wait_for_redis()
        expect("refresh after Redis restart", refresh_status(), 401)
        expect("login start after Redis restart", login_start_status(probe_email), 202)
    finally:
        compose("start", "redis")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
