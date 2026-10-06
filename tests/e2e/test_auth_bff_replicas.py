"""Prove BFF GraphQL admission is shared across disposable replicas."""

from __future__ import annotations

import os
import socket
import subprocess
from typing import Final
from urllib.error import HTTPError
from urllib.request import Request, urlopen

COMPOSE_FILE: Final[str] = os.environ.get("SQUAREWISE_COMPOSE_FILE", "infra/local/docker-compose.dev.yml")
COMPOSE_PROJECT: Final[str] = os.environ.get("SQUAREWISE_COMPOSE_PROJECT", "")
REDIS_PASSWORD: Final[str] = os.environ.get("REDIS_PASSWORD", "squarewise-redis-local-only")
BEARER_TOKEN: Final[str] = os.environ.get("BEARER_TOKEN", "")
HTTP_REPLICAS: Final[tuple[str, ...]] = (
    os.environ.get("BFF_REPLICA_A_URL", "http://localhost:8086"),
    os.environ.get("BFF_REPLICA_B_URL", "http://localhost:8087"),
)
WEBSOCKET_REPLICAS: Final[tuple[tuple[str, int], ...]] = (
    ("localhost", int(os.environ.get("BFF_REPLICA_A_WS_PORT", "8086"))),
    ("localhost", int(os.environ.get("BFF_REPLICA_B_WS_PORT", "8087"))),
)
GRAPHQL_HTTP_LIMIT: Final[int] = 120
GRAPHQL_WEBSOCKET_LIMIT: Final[int] = 20
RATE_LIMIT_KEY_PATTERN: Final[str] = "squarewise:rl:v1:*"


def compose(*arguments: str) -> str:
    """Run a Compose command against the explicitly selected topology."""
    command: list[str] = ["docker", "compose"]
    if COMPOSE_PROJECT:
        command.extend(("--project-name", COMPOSE_PROJECT))
    command.extend(("-f", COMPOSE_FILE, *arguments))
    completed = subprocess.run(command, check=True, capture_output=True, text=True)
    return completed.stdout.strip()


def clear_rate_limit_namespace() -> None:
    """Delete only disposable Squarewise limiter keys."""
    raw_keys = compose("exec", "-T", "redis", "redis-cli", "-a", REDIS_PASSWORD,
                       "--no-auth-warning", "--scan", "--pattern", RATE_LIMIT_KEY_PATTERN)
    keys = [key for key in raw_keys.splitlines() if key]
    if keys:
        compose("exec", "-T", "redis", "redis-cli", "-a", REDIS_PASSWORD,
                "--no-auth-warning", "DEL", *keys)


def graphql_http_status(base_url: str) -> int:
    """Return one authenticated GraphQL HTTP admission status."""
    if not BEARER_TOKEN:
        raise RuntimeError("BEARER_TOKEN is required for the replica probe")
    request = Request(f"{base_url}/graphql", data=b'{"query":"{ me { id } }"}',
                      headers={"Authorization": f"Bearer {BEARER_TOKEN}",
                               "Content-Type": "application/json"}, method="POST")
    try:
        with urlopen(request, timeout=10) as response:
            return response.status
    except HTTPError as error:
        return error.code


def websocket_handshake_status(host: str, port: int) -> int:
    """Return one authenticated GraphQL WebSocket admission status."""
    if not BEARER_TOKEN:
        raise RuntimeError("BEARER_TOKEN is required for the replica probe")
    with socket.create_connection((host, port), timeout=10) as connection:
        request = ("GET /graphql HTTP/1.1\r\n" f"Host: {host}:{port}\r\n"
                   "Upgrade: websocket\r\nConnection: Upgrade\r\n"
                   f"Authorization: Bearer {BEARER_TOKEN}\r\n"
                   "Sec-WebSocket-Key: YXV0aC0wOS1yZXBsaWNh\r\n"
                   "Sec-WebSocket-Version: 13\r\n"
                   "Sec-WebSocket-Protocol: graphql-transport-ws\r\n\r\n")
        connection.sendall(request.encode("ascii"))
        response = b""
        while b"\r\n\r\n" not in response:
            chunk = connection.recv(4096)
            if not chunk:
                break
            response += chunk
    first_line = response.split(b"\r\n", 1)[0].decode("ascii", errors="replace")
    parts = first_line.split(" ", 2)
    if len(parts) < 2 or not parts[1].isdigit():
        raise AssertionError(f"Malformed WebSocket response: {first_line!r}")
    return int(parts[1])


def expect_replica_admission(label: str, statuses: list[int], limit: int) -> None:
    """Require all requests through the replicas to share one Redis window."""
    if any(status == 429 for status in statuses[:limit]):
        raise AssertionError(f"{label} was rate limited before the shared cap")
    if statuses[limit] != 429:
        raise AssertionError(f"{label} expected HTTP 429 at cap + 1, got {statuses[limit]}")
    print(f"  {label}: {limit} alternating admissions, then HTTP 429")


def main() -> int:
    """Verify HTTP and WebSocket admission across two BFF processes."""
    clear_rate_limit_namespace()
    http_statuses = [graphql_http_status(HTTP_REPLICAS[index % 2]) for index in range(GRAPHQL_HTTP_LIMIT + 1)]
    expect_replica_admission("GraphQL HTTP replicas", http_statuses, GRAPHQL_HTTP_LIMIT)
    clear_rate_limit_namespace()
    websocket_statuses = [websocket_handshake_status(*WEBSOCKET_REPLICAS[index % 2]) for index in range(GRAPHQL_WEBSOCKET_LIMIT + 1)]
    expect_replica_admission("GraphQL WebSocket replicas", websocket_statuses, GRAPHQL_WEBSOCKET_LIMIT)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
