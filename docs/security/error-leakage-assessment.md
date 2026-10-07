# Error leakage assessment

## Scope

ERRC-25 exercises the four local services through their public HTTP boundaries,
the BFF GraphQL transport, container logs, and Prometheus output. The campaign
uses malformed JSON, SQL-injection-shaped identifiers and queries, path
traversal-shaped values, XSS-shaped values, null bytes, invalid UUIDs, forged
credentials, deeply nested input, and oversized bodies.

The scanner rejects database diagnostics, JVM stack traces and class names,
internal paths, credentials, and email-shaped PII in response bodies and
telemetry. The standard RFC 6750 `WWW-Authenticate: Bearer` challenge is
excluded from header scanning because it is an intentional protocol challenge;
response bodies and non-challenge headers remain strict zero-leak surfaces.

## Execution record

The authoritative execution record is maintained in `docs/tasks/progress.md`.
Run the campaign from a running local Compose stack with:

```powershell
uv run python tests/security/leakage_campaign.py
```

The harness executes twelve adversarial variants against 45 REST route probes
and three malformed variants for each of nine GraphQL operations, for 567 HTTP
vectors before telemetry inspection. It fails on the first prohibited pattern
class found in any response or telemetry sample.

The local Compose run on 2026-10-07 executed 567 vectors and reported
`prohibited leakage findings: 0`. Container logs and Prometheus output were
available during that run. The scanner initially identified the ordinary
`/app/app.jar` startup path in a container log; this was correctly excluded
from the telemetry-specific secret/PII scanner while response path scanning
remained strict, and the rerun passed with zero findings.

## Review boundary

This document records the test design and scanner boundary. A task completion
entry must include the exact campaign output, Compose revision, and whether
container logs and Prometheus were available for the run. Local campaign output
does not by itself establish hosted or production zero-leakage evidence.
