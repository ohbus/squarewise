# ERRC-25: Security and privacy leakage campaign

## Context & Parent Milestone

- **Parent Workstream**: [`ERRC-01: Design six-digit domain error-code migration`](ERRC-01.md)
- **Phase**: Phase 5 — Clients, acceptance, scale, rollout
- **Implementation Strategy**: [`docs/architecture/error-code-refactoring.md`](../../architecture/error-code-refactoring.md)

## Objective

Execute an adversarial security and privacy leakage testing campaign across all public and internal interfaces (REST, GraphQL, WebSocket, RabbitMQ logs, and Prometheus metrics). Subject the system to intentional malformed inputs, SQL injections, oversized payloads, nested exceptions, and simulated database faults to verify that zero internal stack traces, database schema details, PII, auth secrets, or raw exception messages ever escape to clients.

## Dependencies

- Preceding: [`ERRC-24: Update client fixtures, Bruno, and end-to-end acceptance`](ERRC-24.md)

## Owned Paths

- `docs/tasks/details/ERRC-25.md`
- `tests/security/fuzzing/`
- `tests/security/leakage_campaign.py`
- `docs/security/error-leakage-assessment.md`

## Architecture & Design Patterns

- **Adversarial Negative Testing Pattern**: Explicitly craft malicious and pathological payloads (SQL injection strings, XSS vectors, null bytes, circular JSON, 10MB payloads) to attack error handlers.
- **Defense in Depth**: Verify sanitization at multiple independent layers: controller advice, security filter chain, container error pages, log sanitizers, and metric tag guards.
- **Data Minimization & Privacy by Design**: Confirm zero PII (email addresses, hashed passwords, session tokens, JWTs, invitation codes) appears in client responses or telemetry labels.
- **Fail-Closed Verification**: Verify that unexpected catastrophic faults (e.g. database disconnect during transaction) cleanly emit generic 500 ProblemDetails without leaking SQL state.

## Common Libraries & Framework Integration

- **`libs/security`**: Entry points, token validators, and challenge headers.
- **`libs/errors`**: ProblemDetails sanitization and exception shielding.
- **Python / pytest**: Automated fuzzing harness and payload generator.

## Technical Requirements & Deliverables

1. **Adversarial Leakage Harness (`tests/security/leakage_campaign.py`)**:
   - Executes fuzzing against all 45 REST endpoints and 9 GraphQL operations.
   - Tests categories of attacks:
     - SQL Injection strings in IDs, filters, search terms (`' OR 1=1 --`, `UNION SELECT`).
     - Malformed JSON tokens (unclosed quotes, invalid unicode escapes, deeply nested objects).
     - Oversized request bodies exceeding HTTP buffer thresholds.
     - Invalid bearer tokens and expired JWT signatures.
     - Concurrent race conditions designed to trigger lock timeouts.
2. **Automated Leakage Scanner**:
   - Inspects response bodies and headers against prohibited regex patterns:
     - Database error patterns: `org.postgresql`, `PSQLException`, `syntax error at or near`, `table`, `column`.
     - JVM internal traces: `Exception in thread`, `at com.subhrodip`, `NullPointerException`, `StackTrace`.
     - Secrets / PII patterns: `eyJh`, `Bearer `, `secret`, `password`, `@`, `api_key`.
3. **Log & Telemetry Redaction Audit**:
   - Inspects container stdout logs and `/actuator/prometheus` scrape output during attack runs to confirm zero secret leakage.
4. **Security Assessment Report (`docs/security/error-leakage-assessment.md`)**:
   - Detailed audit findings documenting test coverage, tested vectors, and zero-leakage proof.

## Acceptance Criteria

1. Automated adversarial campaign executes >500 pathological test vectors against all services.
2. Exactly 0 occurrences of stack traces, class names, SQL syntax, or internal file paths in any response body.
3. Anti-enumeration responses consistently mask whether users or groups exist on unauthorized requests.
4. Container log streams and Prometheus metrics remain clean of raw secrets and PII.
5. Assessment report is complete and review-approved.
6. Validation commands execute cleanly.

## Validation Commands

```powershell
uv run python tests/security/leakage_campaign.py
uv run python tools/contracts/validate.py
git diff --check
```

## Evidence Expectations

- Leakage scanner test log verifying 0 matches on prohibited leakage regexes across all tests.
- Formally signed-off assessment document in `docs/security/error-leakage-assessment.md`.

## Implementation Notes and Evidence

- Added a typed black-box campaign covering 45 REST route probes and all nine GraphQL operations with SQL-injection-shaped values, malformed JSON, oversized/deep input, path traversal, XSS, null bytes, invalid identifiers, and credential-shaped inputs.
- The live campaign executed 567 HTTP vectors and scanned response bodies, non-challenge headers, container logs, and Prometheus output.
- The 2026-10-07 local Compose run completed with `prohibited leakage findings: 0`. The RFC 6750 `WWW-Authenticate: Bearer` challenge is intentionally excluded from header scanning; all other response and telemetry surfaces remain strict.
- The assessment is recorded in [`docs/security/error-leakage-assessment.md`](../../security/error-leakage-assessment.md), with the local-versus-hosted evidence boundary documented.

## Rollout & Rollback Strategy

- Security evaluation milestone.
- Zero production code impact.
- Rollback: If leaks are identified, block production promotion and file blocking remediation tasks under `ERRC-15`/`ERRC-16`.
