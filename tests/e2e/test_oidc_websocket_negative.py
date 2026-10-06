"""Verify that forged OIDC credentials cannot upgrade the GraphQL WebSocket."""

from __future__ import annotations

import os
import socket
import base64
from typing import Final
from tests.http_constants import GRAPHQL_PATH


TOKEN: Final[str] = os.environ.get("BEARER_TOKEN", "")
INVALID_SUBJECT_TOKEN: Final[str] = os.environ.get("INVALID_SUBJECT_TOKEN", "")
HOST: Final[str] = "localhost"
BFF_URL: Final[str] = os.environ.get("BFF_URL", "http://localhost:28080")
PORT: Final[int] = int(BFF_URL.rsplit(":", 1)[1])
PATH: Final[str] = GRAPHQL_PATH


def tamper_signature(token: str) -> str:
    """Change a decoded signature byte while preserving compact JWT shape."""
    parts = token.split(".")
    if len(parts) != 3 or not parts[2]:
        raise ValueError("BEARER_TOKEN must be a compact signed JWT")
    padding = "=" * (-len(parts[2]) % 4)
    signature = bytearray(base64.urlsafe_b64decode((parts[2] + padding).encode()))
    signature[0] ^= 1
    encoded = base64.urlsafe_b64encode(bytes(signature)).decode().rstrip("=")
    return ".".join((parts[0], parts[1], encoded))


def handshake_status(token: str) -> str:
    """Return the HTTP status line produced by the WebSocket upgrade attempt."""
    key = base64.b64encode(os.urandom(16)).decode()
    request = (
        f"GET {PATH} HTTP/1.1\r\n"
        f"Host: {HOST}:{PORT}\r\n"
        "Upgrade: websocket\r\n"
        "Connection: Upgrade\r\n"
        f"Authorization: Bearer {token}\r\n"
        f"Sec-WebSocket-Key: {key}\r\n"
        "Sec-WebSocket-Version: 13\r\n"
        "Sec-WebSocket-Protocol: graphql-transport-ws\r\n\r\n"
    )
    with socket.create_connection((HOST, PORT), timeout=10) as connection:
        connection.sendall(request.encode("utf-8"))
        try:
            return connection.recv(4096).decode("utf-8", errors="replace").splitlines()[0]
        except ConnectionResetError:
            return "HTTP/1.1 401 Unauthorized"


def main() -> int:
    """Reject a forged-token GraphQL WebSocket upgrade."""
    if not TOKEN:
        raise RuntimeError("BEARER_TOKEN must contain a signed access token")
    for name, token in (("forged", tamper_signature(TOKEN)), ("invalid-subject", INVALID_SUBJECT_TOKEN)):
        if not token:
            continue
        status = handshake_status(token)
        print(f"{name}: {status}")
        if " 101 " in status:
            raise AssertionError(f"{name} token was accepted for WebSocket upgrade")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
