# SEC-01F: Make security operations, telemetry, supply chain, and release evidence executable

## Status

Done. Commit: see progress ledger.

## Objective

Resolve findings SEC-010 (secrets/key rotation), SEC-011 (supply chain and deployment),
SEC-012 (security telemetry & audit trails), and SEC-013 (endpoint matrix completeness).

1. **Key Rotation & Incident Operations (SEC-010):**
   - Provide runbooks and verification tests for asymmetric signing key rotation,
     credential hashing secret rotation, and session invalidation.
2. **Supply Chain & Deployment Hardening (SEC-011):**
   - Verify SBOM generation, dependency vulnerability checks, action pinning, and
     deployment configuration hardening.
3. **Security Telemetry & Structured Audit Events (SEC-012):**
   - Emit sanitized security events for authentication lifecycle, token refresh replay,
     authorization failures, and administrative actions without logging secrets or PII.
4. **Endpoint Matrix & Production Readiness Evidence (SEC-013):**
   - Reconcile `docs/quality/public-interface-operation-matrix.md` with complete evidence
     for authentication, authorization, and failure modes across all 45+ endpoints.
   - Evaluate lifting of the public production NO-GO gate upon complete verification.

## Owned Paths

- `docs/operations/`
- `docs/security/`
- `docs/quality/`
- `.github/workflows/`
- `tools/ops/`
- `docs/tasks/details/SEC-01F.md`
- `app/accounts/src/main/kotlin/com/subhrodip/squarewise/accounts/auth/audit/`
- `app/accounts/src/main/kotlin/com/subhrodip/squarewise/accounts/auth/session/TokenSessionService.kt`
- `app/accounts/src/main/kotlin/com/subhrodip/squarewise/accounts/auth/login/LoginVerificationService.kt`
- `app/accounts/src/main/kotlin/com/subhrodip/squarewise/accounts/auth/login/LoginStartService.kt`
- `app/accounts/src/main/kotlin/com/subhrodip/squarewise/accounts/auth/jwks/DefaultRsaKeyProvider.kt`

## Implementation

### SEC-012: SecurityAuditLogger (structured, redacted security events)

Created `SecurityAuditEvent` enum (11 event types) and `SecurityAuditLogger` in
`app/accounts/src/main/kotlin/com/subhrodip/squarewise/accounts/auth/audit/`.

The logger writes to the dedicated `security.audit` logger name so operators can route
events to an immutable audit sink independently of the main application log stream.
No credentials, raw tokens, email addresses, or request bodies are emitted.

Events wired into the following services:
- `TokenSessionService`: `LOGIN_SUCCESS`, `TOKEN_REUSE_DETECTED`, `SESSION_DENIED_DELETION_REQUESTED`,
  `SESSION_SUBJECT_MISMATCH`, `TOKEN_REFRESHED`, `SESSION_REVOKED`
- `LoginVerificationService`: `LOGIN_FAILURE`, `IDENTITY_ENROLLED`
- `LoginStartService`: `LOGIN_RATE_LIMITED`
- `DefaultRsaKeyProvider`: `KEY_ROTATED` (includes new kid in detail; no key material)

Tests: `SecurityAuditLoggerTest` (13 cases, all event types verified, truncation verified).

### SEC-010: Key rotation runbook and overlap tests

Created `docs/operations/key-rotation-runbook.md` covering:
- RSA signing key overlap rotation procedure (old key retained in JWKS for verification window)
- Credential-digest HMAC secret rotation (with emergency compromise variant)
- Auth-email envelope key rotation (with drain-first requirement)
- Emergency global session revocation (per-account and full-system)
- Verification commands and operator authorization requirements

Extended `DefaultRsaKeyProviderTest` (from 2 to 8 tests):
- Overlap window test: old public key available in JWKS immediately after rotation
- Multi-rotation accumulation test: three generations all retained
- Fail-closed guard: public-only key rejected, active key unchanged
- Fail-closed guard: blank kid rejected, active key unchanged
- JWKSource consistency: source returns same keys as publicJwkSet

Added `KEY_ROTATED` audit event emission to `DefaultRsaKeyProvider.rotateKey`.

### SEC-011: Supply chain evidence document

Created `docs/security/supply-chain-evidence.md` documenting:
- Dependency centralization via `libs.versions.toml`
- CycloneDX SBOM generation with local evidence
- Security hygiene scan (1,035 files clean)
- Image hardening baseline (multi-stage, non-root, resource limits)
- Explicit enumeration of remaining release-gated controls (container scanning,
  image signing, dependency renovation, license policy, admission control)

The CI workflow now pins every non-GitHub-owned action to the full commit SHA
behind its documented major tag: Gradle setup, test-summary, Docker login,
Buildx, metadata, and Build/Push. A repository regression test rejects future
floating third-party action tags while retaining major tags for `actions/*`.

Sonar secret-detector findings for the two Base64 values in the reusable CI
workflow are intentional false positives. They are deterministic, public
local-fixture keys used only to boot test services; they are not deployment
credentials, are not stored in GitHub secrets, and production/staging sanity
guards reject these predictable values. Suppress those two findings as false
positives in Sonar with this rationale; rotating or hiding them would not add
security and would make the CI fixture less reproducible.

### SEC-013: Endpoint matrix update

Rewrote `docs/quality/public-interface-operation-matrix.md` to:
- Annotate each auth-related endpoint row with SEC-01A through SEC-01F evidence
- Add a `SecurityAuditLogger` event column annotation for auth endpoints
- Add a `## SEC-01 security finding closure summary` table mapping all 13 findings
  (SEC-001 through SEC-013) to workstream, code evidence, and final status
- Update `groupChanged` WebSocket row with SEC-01D revocation evidence

## Acceptance Criteria: Verification

1. ✅ Asymmetric key rotation procedures are documented with overlap tests verified.
2. ✅ Structured security audit events are logged for key security events without secret leakage.
3. ✅ Supply chain hygiene checks pass cleanly.
4. ✅ Endpoint operation matrix updated with SEC-01 verified evidence across all security dimensions.
5. ✅ All P0 and P1 security findings (SEC-001 through SEC-013) are verified closed at code/test level.

## Verification Commands

- `./gradlew :app:accounts:test --no-daemon`: BUILD SUCCESSFUL (19 tasks)
- `uv run python tools/contracts/validate.py`: valid (207 tasks)
- `uv run python tools/ops/check_security_hygiene.py`: passed
- `uv run python tools/ops/check_architecture.py`: passed
- `uv run python tools/ops/validate_sbom_baseline.py`: passed

## Known Limitations

- Immutable audit-log retention (CloudWatch/object lock or equivalent) requires a
  production environment and is explicitly release-gated under QA-08/OPS-20.
- Container image vulnerability scanning and signing are release-gated.
- Production-scale managed-provider (Keycloak/Auth0) evidence is release-gated.
- Key rotation runbook is verified with unit tests; production rehearsal evidence
  requires a deployed environment.
