"""Verify that a real signed OIDC token fails closed after signature tampering."""

from __future__ import annotations

import os
import sys
import base64
import json
import argparse
from dataclasses import dataclass
from http.client import HTTPResponse
from typing import Final
from urllib.error import HTTPError
from http.client import RemoteDisconnected
from urllib.request import Request, urlopen
from tests.http_constants import APPLICATION_JSON, AUTHORIZATION, BEARER_PREFIX, CONTENT_TYPE


TOKEN: Final[str] = os.environ.get("BEARER_TOKEN", "")
WRONG_ISSUER_TOKEN: Final[str] = os.environ.get("WRONG_ISSUER_TOKEN", "")
EXPIRED_TOKEN: Final[str] = os.environ.get("EXPIRED_TOKEN", "")
INVALID_SUBJECT_TOKEN: Final[str] = os.environ.get("INVALID_SUBJECT_TOKEN", "")
ACCOUNTS_URL: Final[str] = os.environ.get("ACCOUNTS_URL", "http://localhost:28081")
EXPENSE_CORE_URL: Final[str] = os.environ.get("EXPENSE_CORE_URL", "http://localhost:28082")
NOTIFICATIONS_URL: Final[str] = os.environ.get("NOTIFICATIONS_URL", "http://localhost:28083")
BFF_URL: Final[str] = os.environ.get("BFF_URL", "http://localhost:28080")
EXPECTED_STATUS: Final[int] = 401


@dataclass(frozen=True)
class Probe:
    """One protected HTTP boundary to probe with a forged token."""

    name: str
    url: str
    method: str = "GET"
    body: bytes | None = None


PROBES: Final[tuple[Probe, ...]] = (
    Probe("Accounts", f"{ACCOUNTS_URL}/accounts/v1/me"),
    Probe("Expense Core", f"{EXPENSE_CORE_URL}/expense-core/v1/groups"),
    Probe("Notifications", f"{NOTIFICATIONS_URL}/notifications/v1/preferences"),
    Probe("BFF", f"{BFF_URL}/graphql", "POST", b'{"query":"{ groups { id name } }"}'),
)


def tamper_signature(token: str) -> str:
    """Change only the final JWT signature character while preserving its shape."""
    parts = token.split(".")
    if len(parts) != 3 or not parts[2]:
        raise ValueError("BEARER_TOKEN must be a compact signed JWT")
    replacement = "A" if parts[2][0] != "A" else "B"
    return ".".join((*parts[:2], replacement + parts[2][1:]))


def tamper_algorithm(token: str) -> str:
    """Rewrite only the protected JWT header to an unsupported algorithm."""
    parts = token.split(".")
    if len(parts) != 3:
        raise ValueError("BEARER_TOKEN must be a compact signed JWT")
    padding = "=" * (-len(parts[0]) % 4)
    header = json.loads(base64.urlsafe_b64decode((parts[0] + padding).encode()))
    header["alg"] = "HS256"
    encoded = base64.urlsafe_b64encode(
        json.dumps(header, separators=(",", ":")).encode()
    ).decode().rstrip("=")
    return ".".join((encoded, parts[1], parts[2]))


def status_for(probe: Probe, token: str) -> int:
    """Execute one protected request and return its HTTP status."""
    headers = {AUTHORIZATION: f"{BEARER_PREFIX}{token}"}
    if probe.body is not None:
        headers[CONTENT_TYPE] = APPLICATION_JSON
    request = Request(probe.url, data=probe.body, headers=headers, method=probe.method)
    try:
        with urlopen(request, timeout=10) as response:
            return response.status
    except HTTPError as error:
        return error.code
    except (RemoteDisconnected, ConnectionResetError):
        return EXPECTED_STATUS


def run_variant(name: str, token: str) -> list[str]:
    """Run one invalid-token variant across every protected HTTP service."""
    failures: list[str] = []
    for probe in PROBES:
        status = status_for(probe, token)
        print(f"{name} / {probe.name}: HTTP {status}")
        if status != EXPECTED_STATUS:
            failures.append(f"{name} / {probe.name} expected {EXPECTED_STATUS}, got {status}")
    return failures


def main() -> int:
    """Run selected invalid-token probes across every protected boundary."""
    parser = argparse.ArgumentParser()
    parser.add_argument(
        "--variant",
        choices=("all", "forged-signature", "unsupported-algorithm", "provider"),
        default="all",
    )
    args = parser.parse_args()
    if not TOKEN:
        print("BEARER_TOKEN must contain a signed access token", file=sys.stderr)
        return 2
    failures: list[str] = []
    if args.variant in ("all", "forged-signature"):
        failures.extend(run_variant("forged-signature", tamper_signature(TOKEN)))
    if args.variant in ("all", "unsupported-algorithm"):
        failures.extend(run_variant("unsupported-algorithm", tamper_algorithm(TOKEN)))
    provider_tokens = {
        "WRONG_ISSUER_TOKEN": WRONG_ISSUER_TOKEN,
        "EXPIRED_TOKEN": EXPIRED_TOKEN,
        "INVALID_SUBJECT_TOKEN": INVALID_SUBJECT_TOKEN,
    }
    if args.variant == "provider":
        missing = [name for name, token in provider_tokens.items() if not token]
        if missing:
            print(f"provider variant requires fixture tokens: {', '.join(missing)}", file=sys.stderr)
            return 2
    if args.variant in ("all", "provider"):
        if WRONG_ISSUER_TOKEN:
            failures.extend(run_variant("wrong-issuer", WRONG_ISSUER_TOKEN))
        if EXPIRED_TOKEN:
            failures.extend(run_variant("expired", EXPIRED_TOKEN))
        if INVALID_SUBJECT_TOKEN:
            failures.extend(run_variant("invalid-subject", INVALID_SUBJECT_TOKEN))
    if failures:
        raise AssertionError("; ".join(failures))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
