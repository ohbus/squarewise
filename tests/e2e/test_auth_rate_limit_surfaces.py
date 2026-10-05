"""Exercise GraphQL HTTP and WebSocket rate limits through the public BFF path."""

from __future__ import annotations

import os
import socket
import subprocess
from typing import Final
from urllib.error import HTTPError
from urllib.request import Request, urlopen


COMPOSE_FILE: Final[str] = os.environ.get(
    "SQUAREWISE_COMPOSE_FILE", "infra/local/docker-compose.dev.yml"
)
COMPOSE_PROJECT: Final[str] = os.environ.get("SQUAREWISE_COMPOSE_PROJECT", "")
REDIS_PASSWORD: Final[str] = os.environ.get(
    "REDIS_PASSWORD", "squarewise-redis-local-only"
)
BFF_URL: Final[str] = os.environ.get("BFF_URL", "http://localhost:8080")
BEARER_TOKEN: Final[str] = os.environ.get("BEARER_TOKEN", "")
GRAPHQL_HTTP_LIMIT: Final[int] = 120
GRAPHQL_WEBSOCKET_LIMIT: Final[int] = 20
RATE_LIMIT_KEY_PATTERN: Final[str] = "squarewise:rl:v1:*"


def compose(*arguments: str) -> str:
    """Run a Compose command against the explicitly selected local topology."""
    command: list[str] = ["docker", "compose"]
    if COMPOSE_PROJECT:
        command.extend(("--project-name", COMPOSE_PROJECT))
    command.extend(("-f", COMPOSE_FILE, *arguments))
    completed = subprocess.run(command, check=True, capture_output=True, text=True)
    return completed.stdout.strip()


def clear_rate_limit_namespace() -> int:
    """Delete only Squarewise limiter keys and return the number removed."""
    raw_keys = compose(
        "exec", "-T", "redis", "redis-cli", "-a", REDIS_PASSWORD,
        "--no-auth-warning", "--scan", "--pattern", RATE_LIMIT_KEY_PATTERN,
    )
    keys = [key for key in raw_keys.splitlines() if key]
    if keys:
        compose(
            "exec", "-T", "redis", "redis-cli", "-a", REDIS_PASSWORD,
            "--no-auth-warning", "DEL", *keys,
        )
    return len(keys)


def graphql_http_status() -> int:
    """Return the unauthenticated GraphQL HTTP response status."""
    headers = {"Content-Type": "application/json"}
    if BEARER_TOKEN:
        headers["Authorization"] = f"Bearer {BEARER_TOKEN}"
    request = Request(
        f"{BFF_URL}/graphql",
        data=b'{"query":"{ me { id } }"}',
        headers=headers,
        method="POST",
    )
    try:
        with urlopen(request, timeout=10) as response:
            return response.status
    except HTTPError as error:
        return error.code


def websocket_handshake_status() -> int:
    """Return the public GraphQL WebSocket handshake status, then close it."""
    if not BEARER_TOKEN:
        raise RuntimeError("BEARER_TOKEN is required for the WebSocket surface probe")
    with socket.create_connection(("localhost", 8080), timeout=10) as connection:
        request = (
            "GET /graphql HTTP/1.1\r\n"
            "Host: localhost:8080\r\n"
            "Upgrade: websocket\r\n"
            "Connection: Upgrade\r\n"
            f"Authorization: Bearer {BEARER_TOKEN}\r\n"
            "Sec-WebSocket-Key: YXV0aC0wOS1zdXJmYWNl\r\n"
            "Sec-WebSocket-Version: 13\r\n"
            "Sec-WebSocket-Protocol: graphql-transport-ws\r\n\r\n"
        )
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


def expect(label: str, actual: int, expected: int) -> None:
    """Assert and print one public-surface response status."""
    if actual != expected:
        raise AssertionError(f"{label}: expected HTTP {expected}, got {actual}")
    print(f"  {label}: HTTP {actual}")


def main() -> int:
    """Verify GraphQL HTTP and WebSocket admission limits independently."""
    clear_rate_limit_namespace()
    for index in range(1, GRAPHQL_HTTP_LIMIT + 1):
        status = graphql_http_status()
        if status == 429:
            raise AssertionError(f"GraphQL HTTP request {index} was rate limited early")
    expect("GraphQL HTTP request at cap + 1", graphql_http_status(), 429)

    clear_rate_limit_namespace()
    for index in range(1, GRAPHQL_WEBSOCKET_LIMIT + 1):
        status = websocket_handshake_status()
        if status == 429:
            raise AssertionError(f"WebSocket handshake {index} was rate limited early")
    expect("GraphQL WebSocket handshake at cap + 1", websocket_handshake_status(), 429)

    clear_rate_limit_namespace()
    compose("stop", "redis")
    try:
        expect("GraphQL HTTP with Redis unavailable", graphql_http_status(), 429)
        expect("GraphQL WebSocket with Redis unavailable", websocket_handshake_status(), 429)
    finally:
        compose("start", "redis")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
