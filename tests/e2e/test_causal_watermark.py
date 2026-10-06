#!/usr/bin/env python3
"""Verify command-to-query PostgreSQL LSN propagation across Accounts."""

from __future__ import annotations

import json
import os
from urllib.error import HTTPError, URLError
from urllib.request import Request, urlopen
from typing import Any
from tests.http_constants import ACCEPT, APPLICATION_JSON, AUTHORIZATION, CONTENT_TYPE, REQUIRED_WATERMARK, WRITER_WATERMARK

ACCOUNTS_URL = os.environ.get("SQUAREWISE_ACCOUNTS_URL", "http://localhost:28081")
REQUIRED_HEADER = REQUIRED_WATERMARK
WRITER_HEADER = WRITER_WATERMARK


def request_json(url: str, token: str, method: str, body: dict[str, Any] | None = None, watermark: str | None = None) -> tuple[int, dict[str, str], Any]:
    headers = {ACCEPT: APPLICATION_JSON, AUTHORIZATION: f"Bearer {token}"}
    if body is not None:
        headers[CONTENT_TYPE] = APPLICATION_JSON
    if watermark is not None:
        headers[REQUIRED_HEADER] = watermark
    payload = json.dumps(body).encode("utf-8") if body is not None else None
    request = Request(url, data=payload, headers=headers, method=method)
    try:
        with urlopen(request, timeout=15) as response:
            content = response.read().decode("utf-8")
            return response.status, dict(response.headers.items()), json.loads(content) if content else {}
    except HTTPError as error:
        return error.code, dict(error.headers.items()), error.read().decode("utf-8")


def main() -> int:
    token = os.environ.get("SQUAREWISE_E2E_TOKEN_A", os.environ.get("BEARER_TOKEN"))
    if not token:
        raise RuntimeError("SQUAREWISE_E2E_TOKEN_A or BEARER_TOKEN is required")

    status, headers, profile = request_json(f"{ACCOUNTS_URL}/accounts/v1/me", token, "GET")
    if status != 200:
        raise AssertionError(f"profile bootstrap failed: {status} {profile}")
    account_id = profile["accountId"]

    mutation_status, mutation_headers, _ = request_json(
        f"{ACCOUNTS_URL}/accounts/v1/me",
        token,
        "PATCH",
        {"displayName": "causal-e2e", "timezone": "Europe/Vienna", "defaultCurrency": "EUR"},
    )
    if mutation_status != 200:
        raise AssertionError(f"profile mutation failed: {mutation_status}")
    watermark = mutation_headers.get(WRITER_HEADER)
    if not watermark:
        raise AssertionError(f"mutation did not emit {WRITER_HEADER}: {mutation_headers}")

    query_status, query_headers, queried = request_json(
        f"{ACCOUNTS_URL}/accounts/v1/profiles/{account_id}",
        token,
        "GET",
        watermark=watermark,
    )
    if query_status != 200 or queried.get("accountId") != account_id:
        raise AssertionError(f"causal follow-up query failed: {query_status} {queried}")
    print(f"causal_writer_watermark={watermark}")
    print(f"causal_follow_up_status={query_status}")
    print(f"causal_follow_up_watermark={query_headers.get(WRITER_HEADER, 'absent')}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
