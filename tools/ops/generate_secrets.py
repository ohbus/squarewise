#!/usr/bin/env python3
"""Generate cryptographically secure secrets and keys for Squarewise."""

from __future__ import annotations

import argparse
import base64
import secrets
import sys
from collections.abc import Sequence


def generate_secrets() -> dict[str, str]:
    """Generate high-entropy secrets for all configurable security parameters."""
    return {
        "SQUAREWISE_SECURITY_CREDENTIAL_DIGEST_SECRET": base64.b64encode(
            secrets.token_bytes(32)
        ).decode("ascii"),
        "SQUAREWISE_SECURITY_AUTH_EMAIL_ENVELOPE_KEY": base64.b64encode(
            secrets.token_bytes(32)
        ).decode("ascii"),
        "REDIS_PASSWORD": secrets.token_urlsafe(24),
        "POSTGRES_PASSWORD": secrets.token_urlsafe(24),
        "RABBITMQ_DEFAULT_PASS": secrets.token_urlsafe(24),
        "POSTGRES_REPLICATION_PASSWORD": secrets.token_urlsafe(24),
        "RABBITMQ_ERLANG_COOKIE": secrets.token_urlsafe(24),
        "KEYCLOAK_ADMIN_PASSWORD": secrets.token_urlsafe(24),
    }


def main(arguments: Sequence[str] | None = None) -> int:
    """Print generated secrets as environment variables or export commands."""
    parser = argparse.ArgumentParser(
        description="Generate cryptographically secure secrets for Squarewise CI and production."
    )
    parser.add_argument(
        "--format",
        choices=("env", "export", "json"),
        default="env",
        help="Output format: 'env' (KEY=VALUE), 'export' (export KEY=VALUE), or 'json'.",
    )
    args = parser.parse_args(arguments)
    generated = generate_secrets()

    if args.format == "export":
        for key, value in generated.items():
            print(f"export {key}={value}")
    elif args.format == "json":
        import json

        print(json.dumps(generated, indent=2))
    else:
        for key, value in generated.items():
            print(f"{key}={value}")

    return 0


if __name__ == "__main__":
    raise SystemExit(main())
