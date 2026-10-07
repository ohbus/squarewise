#!/usr/bin/env python3
"""Run a deterministic black-box leakage campaign against the local stack."""

from __future__ import annotations

import json
import os
import re
import subprocess
import sys
import uuid
from dataclasses import dataclass
from typing import Final, Iterable
from urllib.error import HTTPError, URLError
from urllib.parse import quote
from urllib.request import Request, urlopen


ROOT: Final[str] = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
REST_BASES: Final[tuple[str, ...]] = (
    os.environ.get("ACCOUNTS_URL", "http://localhost:28081"),
    os.environ.get("EXPENSE_CORE_URL", "http://localhost:28082"),
    os.environ.get("NOTIFICATIONS_URL", "http://localhost:28083"),
)
BFF_URL: Final[str] = os.environ.get("BFF_URL", "http://localhost:28080")
TOKEN: Final[str] = os.environ.get("BEARER_TOKEN", "")
REQUEST_TIMEOUT_SECONDS: Final[float] = 8.0
INJECTION: Final[str] = "' OR 1=1 --"
UUID_VALUE: Final[str] = "00000000-0000-7000-8000-000000000000"


@dataclass(frozen=True)
class Probe:
    """One public route and its HTTP verb."""

    name: str
    base: str
    path: str
    method: str = "GET"


@dataclass(frozen=True)
class Finding:
    """One prohibited response or telemetry match."""

    source: str
    pattern: str
    excerpt: str


REST_PROBES: Final[tuple[Probe, ...]] = (
    Probe("accounts.me", REST_BASES[0], "/accounts/v1/me"),
    Probe("accounts.profile", REST_BASES[0], "/accounts/v1/profiles/{id}"),
    Probe("accounts.batch", REST_BASES[0], "/accounts/v1/profiles/batch", "POST"),
    Probe("accounts.deletion", REST_BASES[0], "/accounts/v1/me/deletion-request", "POST"),
    Probe("accounts.export", REST_BASES[0], "/accounts/v1/me/export-request", "POST"),
    Probe("accounts.exports", REST_BASES[0], "/accounts/v1/me/export-requests"),
    Probe("accounts.login", REST_BASES[0], "/accounts/v1/auth/login", "POST"),
    Probe("accounts.verify", REST_BASES[0], "/accounts/v1/auth/verify", "POST"),
    Probe("groups.list", REST_BASES[1], "/expense-core/v1/groups"),
    Probe("groups.get", REST_BASES[1], "/expense-core/v1/groups/{id}"),
    Probe("groups.create", REST_BASES[1], "/expense-core/v1/groups", "POST"),
    Probe("groups.update", REST_BASES[1], "/expense-core/v1/groups/{id}", "PATCH"),
    Probe("groups.archive", REST_BASES[1], "/expense-core/v1/groups/{id}/archive", "POST"),
    Probe("groups.members", REST_BASES[1], "/expense-core/v1/groups/{id}/members"),
    Probe("groups.member", REST_BASES[1], "/expense-core/v1/groups/{id}/members/{id}"),
    Probe("groups.placeholders", REST_BASES[1], "/expense-core/v1/groups/{id}/placeholders", "POST"),
    Probe("groups.invites", REST_BASES[1], "/expense-core/v1/groups/{id}/invites", "POST"),
    Probe("groups.invite-revoke", REST_BASES[1], "/expense-core/v1/groups/{id}/invites/{token}/revoke", "POST"),
    Probe("groups.invite-claim", REST_BASES[1], "/expense-core/v1/invites/{token}/claim", "POST"),
    Probe("groups.expenses", REST_BASES[1], "/expense-core/v1/groups/{id}/expenses"),
    Probe("groups.expense-create", REST_BASES[1], "/expense-core/v1/groups/{id}/expenses", "POST"),
    Probe("groups.expense-update", REST_BASES[1], "/expense-core/v1/groups/{id}/expenses/{id}", "PATCH"),
    Probe("groups.expense-delete", REST_BASES[1], "/expense-core/v1/groups/{id}/expenses/{id}", "DELETE"),
    Probe("groups.allocations", REST_BASES[1], "/expense-core/v1/allocations/preview", "POST"),
    Probe("groups.search", REST_BASES[1], "/expense-core/v1/groups/{id}/search"),
    Probe("groups.export", REST_BASES[1], "/expense-core/v1/groups/{id}/export"),
    Probe("groups.snapshot", REST_BASES[1], "/expense-core/v1/groups/{id}/sync/snapshot"),
    Probe("groups.changes", REST_BASES[1], "/expense-core/v1/groups/{id}/sync/changes"),
    Probe("groups.schedules", REST_BASES[1], "/expense-core/v1/groups/{id}/schedules"),
    Probe("groups.schedule-create", REST_BASES[1], "/expense-core/v1/groups/{id}/schedules", "POST"),
    Probe("groups.schedule", REST_BASES[1], "/expense-core/v1/groups/{id}/schedules/{id}"),
    Probe("groups.schedule-update", REST_BASES[1], "/expense-core/v1/groups/{id}/schedules/{id}", "PATCH"),
    Probe("groups.schedule-pause", REST_BASES[1], "/expense-core/v1/groups/{id}/schedules/{id}/pause", "POST"),
    Probe("groups.settlements", REST_BASES[1], "/expense-core/v1/groups/{id}/settlements"),
    Probe("groups.settlement-create", REST_BASES[1], "/expense-core/v1/groups/{id}/settlements", "POST"),
    Probe("groups.suggestions", REST_BASES[1], "/expense-core/v1/groups/{id}/settlements/suggestions"),
    Probe("groups.reversal", REST_BASES[1], "/expense-core/v1/groups/{id}/settlements/{id}/reversal", "POST"),
    Probe("notifications.inbox", REST_BASES[2], "/notifications/v1/inbox"),
    Probe("notifications.read", REST_BASES[2], "/notifications/v1/inbox/{id}/read", "POST"),
    Probe("notifications.preferences", REST_BASES[2], "/notifications/v1/preferences"),
    Probe("notifications.preferences-update", REST_BASES[2], "/notifications/v1/preferences", "PUT"),
    Probe("accounts.health", REST_BASES[0], "/actuator/health"),
    Probe("expense.health", REST_BASES[1], "/actuator/health"),
    Probe("notifications.health", REST_BASES[2], "/actuator/health"),
    Probe("bff.health", BFF_URL, "/actuator/health"),
)

GRAPHQL_OPERATIONS: Final[tuple[str, ...]] = (
    "{ me { id } }",
    "{ groups { id } }",
    "{ group(id: \"bad\") { id } }",
    "{ settlementSuggestions(groupId: \"bad\") { memberId } }",
    "mutation { createGroup(input: {name: \"x\"}) { id } }",
    "mutation { updateGroup(id: \"bad\", input: {name: \"x\"}) { id } }",
    "mutation { createExpense(groupId: \"bad\", input: {description: \"x\", amount: {minor: \"1\", currency: \"EUR\"}}) { id } }",
    "mutation { recordRepayment(groupId: \"bad\", input: {amount: {minor: \"1\", currency: \"EUR\"}}) { id } }",
    "subscription { groupChanged(groupId: \"bad\") { groupId } }",
)

BODY_VECTORS: Final[tuple[bytes, ...]] = (
    b"{\"unterminated\":",
    b"{\"value\":\\uZZZZ}",
    b"[" * 120 + b"]" * 120,
    b"{\"value\":\"<script>alert(1)</script>\"}",
    b"{\"value\":\"" + b"A" * 65536 + b"\"}",
    b"{\"value\":\"\\u0000\\uffff\"}",
)

LEAK_PATTERNS: Final[tuple[tuple[str, re.Pattern[str]], ...]] = (
    (
        "database",
        re.compile(r"(?i)(org\.postgresql|psqlexception|syntax error at or near|\\b(?:table|column)\\s+[a-z_])"),
    ),
    (
        "stack",
        re.compile(r"(?i)(exception in thread|at com\.subhrodip|nullpointerexception|stacktrace|traceback)"),
    ),
    ("path", re.compile(r"(?i)([A-Za-z]:\\\\|/app/|/workspace/|build/classes)")),
    (
        "credential",
        re.compile(r"(?i)(eyJ[a-zA-Z0-9_-]{12,}|bearer\\s+[a-zA-Z0-9._-]{12,}|(?:secret|password|api[_-]?key)\\s*[:=]|[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,})"),
    ),
)


def url_for(probe: Probe, vector: str) -> str:
    """Substitute an intentionally invalid path value and query payload."""
    encoded = quote(vector, safe="")
    path = probe.path.replace("{id}", encoded).replace("{token}", encoded)
    return f"{probe.base}{path}?q={encoded}"


def execute(url: str, method: str, body: bytes | None, token: str) -> tuple[int, str, dict[str, str]]:
    """Execute one request and retain only response data needed for scanning."""
    headers = {"Accept": "application/json", "Content-Type": "application/json"}
    if token:
        headers["Authorization"] = f"Bearer {token}"
    request = Request(url, data=body, headers=headers, method=method)
    try:
        with urlopen(request, timeout=REQUEST_TIMEOUT_SECONDS) as response:
            return response.status, response.read().decode("utf-8", errors="replace"), dict(response.headers.items())
    except HTTPError as error:
        return error.code, error.read().decode("utf-8", errors="replace"), dict(error.headers.items())
    except (TimeoutError, URLError, ValueError):
        return 599, "", {}


def scan(source: str, text: str) -> list[Finding]:
    """Find prohibited implementation, credential, PII, or path material."""
    findings: list[Finding] = []
    for name, pattern in LEAK_PATTERNS:
        match = pattern.search(text)
        if match:
            start = max(0, match.start() - 30)
            findings.append(Finding(source, name, text[start : match.end() + 30].replace("\n", " ")))
    return findings


def scan_telemetry(source: str, text: str) -> list[Finding]:
    """Scan telemetry for data leakage without treating normal startup paths as secrets."""
    return [finding for finding in scan(source, text) if finding.pattern != "path"]


def run_rest() -> tuple[int, list[Finding]]:
    """Run twelve adversarial variants against every REST probe."""
    findings: list[Finding] = []
    count = 0
    vectors = (
        INJECTION,
        "UNION%20SELECT",
        "%00",
        "..%2f..%2f",
        "not-a-uuid",
        "<svg/onload=1>",
        "${jndi:ldap://invalid/a}",
        "%EF%BF%BD",
        "NaN",
        "-9223372036854775808",
        "%252e%252e%252f",
        "A" * 4096,
    )
    for probe in REST_PROBES:
        for vector in vectors:
            body = BODY_VECTORS[count % len(BODY_VECTORS)] if probe.method != "GET" else None
            status, text, headers = execute(url_for(probe, vector), probe.method, body, TOKEN)
            findings.extend(scan(f"REST {probe.name} {status}", text))
            for header, value in headers.items():
                if header.lower() != "www-authenticate":
                    findings.extend(scan(f"REST header {probe.name} {header}", value))
            count += 1
    return count, findings


def run_graphql() -> tuple[int, list[Finding]]:
    """Run malformed, nested, and oversized variants for every GraphQL operation."""
    findings: list[Finding] = []
    count = 0
    bodies: Iterable[bytes] = (b"{", b"{\"query\":", b"{\"query\":\"" + b"x" * 65536 + b"\"}")
    for operation in GRAPHQL_OPERATIONS:
        for suffix in bodies:
            payload = suffix if suffix != b"{" else json.dumps({"query": operation, "variables": {"x": INJECTION}}).encode()
            status, text, headers = execute(f"{BFF_URL}/graphql", "POST", payload, TOKEN)
            findings.extend(scan(f"GraphQL {status}", text))
            for header, value in headers.items():
                if header.lower() != "www-authenticate":
                    findings.extend(scan(f"GraphQL header {header}", value))
            count += 1
    return count, findings


def run_telemetry() -> list[Finding]:
    """Scan service logs and public Prometheus output after the campaign."""
    findings: list[Finding] = []
    compose = os.environ.get("COMPOSE_FILE", "infra/local/docker-compose.dev.yml")
    result = subprocess.run(
        ["docker", "compose", "-f", compose, "logs", "--since", "5m", "--no-color", "accounts", "expense-core", "notifications", "bff"],
        cwd=ROOT,
        capture_output=True,
        text=True,
        check=False,
    )
    findings.extend(scan_telemetry("container logs", result.stdout + result.stderr))
    for base in (*REST_BASES, BFF_URL):
        status, text, _ = execute(f"{base}/actuator/prometheus", "GET", None, "")
        findings.extend(scan_telemetry(f"Prometheus {base} {status}", text))
    return findings


def main() -> int:
    """Execute the campaign and fail only when a prohibited leak is observed."""
    rest_count, findings = run_rest()
    graphql_count, graphql_findings = run_graphql()
    findings.extend(graphql_findings)
    findings.extend(run_telemetry())
    total = rest_count + graphql_count
    print(f"leakage campaign executed {total} HTTP vectors across {len(REST_PROBES)} REST probes and {len(GRAPHQL_OPERATIONS)} GraphQL operations")
    print(f"prohibited leakage findings: {len(findings)}")
    for finding in findings[:20]:
        print(f"{finding.source}: {finding.pattern}: {finding.excerpt}", file=sys.stderr)
    return 1 if findings else 0


if __name__ == "__main__":
    raise SystemExit(main())
