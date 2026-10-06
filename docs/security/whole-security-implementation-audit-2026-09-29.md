# Whole security implementation audit

**Audit date:** 2026-09-29
**Scope:** Accounts, Expense Core, Notifications, GraphQL BFF, shared security
library, persistence, cache, messaging, browser/native sessions, WebSocket
subscriptions, local/deployment configuration, CI evidence, and security
documentation.
**Branch:** `docs/auth-rfc-session-hardening`
**Audit mode:** Evidence review and documentation only. No application fix was
implemented by this audit.

## Executive decision

**Security release decision: NO-GO for public production authentication.**

The resource-server boundary, JWT validation, service-owned group authorization,
refresh-family persistence, browser cookie controls, rate-limit fail-closed
behavior, and negative-token testing are strong local foundations. They do not
yet establish a complete security implementation.

The most important blocker is concrete and reproducible in source: all
production-like Accounts profiles wire `ExternalOidcTokenProvider`, while its
only token-issuance method throws `UnsupportedOperationException`. The
passwordless verification path calls that method after redeeming a credential.
Therefore the repository's documented claim that deployed passwordless
verification issues a usable access token is not supported by the current
main-source wiring. Isolated tests pass because they inject a test-only
`InternalJwtTokenProvider`.

There are also authorization and lifecycle gaps: arbitrary authenticated users
can query profile records by account ID; a valid bearer subject can implicitly
provision a profile; the passwordless subject is derived from email rather than
a stable provider-qualified identity; and an authorized WebSocket subscription
is not re-authorized after membership removal. These must be resolved before
the local evidence can be treated as a production security baseline.

This audit deliberately separates four claims:

1. **Implemented:** directly supported by current source and focused tests.
2. **Locally evidenced:** exercised against the local Compose/test environment.
3. **Documented only:** specified in prose but not demonstrated by executable
   evidence.
4. **Release-gated:** requires production-like or target-environment evidence
   and is not closed by local tests.

## RFC and industry baseline

The recommendations below use the current IETF OAuth security baseline rather
than treating an RFC as a substitute for threat modelling:

| Concern | Baseline | Application to Squarewise |
| --- | --- | --- |
| OAuth security | [RFC 9700](https://www.rfc-editor.org/rfc/rfc9700) | Authorization Code + PKCE, exact redirects, no implicit/password grants, CSRF/mix-up protection, constrained tokens, and secure token handling. |
| PKCE | [RFC 7636](https://www.rfc-editor.org/rfc/rfc7636) | Require `S256` for every public client; bind the verifier to one authorization transaction. |
| Native applications | [RFC 8252](https://www.rfc-editor.org/rfc/rfc8252) | Use the system browser and claimed HTTPS/app redirects; do not embed a secret in a native binary. |
| JWT processing | [RFC 8725](https://www.rfc-editor.org/rfc/rfc8725) | Explicit algorithm allow-list, issuer/audience validation, bounded claims, key separation, and no algorithm confusion. |
| OAuth token exchange | [RFC 8693](https://www.rfc-editor.org/rfc/rfc8693) | Only use the external-provider adapter after implementing authenticated, audience/resource-bound token exchange with bounded timeouts and provider tests. |
| Token revocation | [RFC 7009](https://www.rfc-editor.org/rfc/rfc7009) | Apply only when the provider actually owns the refresh grant; current opaque Squarewise sessions are revoked in PostgreSQL. |
| Authorization-server metadata | [RFC 8414](https://www.rfc-editor.org/rfc/rfc8414) | Discover provider endpoints from a trusted issuer and keep issuer trust deployment-controlled. |
| HTTP bearer usage | RFC 6750 | TLS is mandatory outside loopback; never put bearer material in URLs, logs, referrers, or cacheable responses. |
| HTTP problem responses | [RFC 9457](https://www.rfc-editor.org/rfc/rfc9457) | Authentication, authorization, validation, rate-limit, and dependency failures should have stable problem details without secret disclosure. |
| Proof of possession | [RFC 9449](https://www.rfc-editor.org/rfc/rfc9449) | Consider DPoP for high-value native clients if bearer-token theft remains in the threat model; it is not a replacement for authorization. |

Industry practice additionally requires OWASP ASVS/API Security controls,
least-privilege service identities, centralized secret management and
rotation, dependency/image scanning, auditability, incident response, and
independent review. Those practices are release controls, not all protocol
requirements.

## System security model reviewed

```text
Browser / native client
        |
        | HTTPS, bearer token or BFF cookie boundary
        v
GraphQL BFF :8080  ---- bearer propagation ----> Accounts :8081
        |                                      Expense Core :8082
        |                                      Notifications :8083
        |
        +-- WebSocket handshake and subscription authorization

OIDC issuer -> signed JWT validation in each resource server
PostgreSQL -> identity, profile, refresh-family, revocation authority
Redis      -> rate-limit/cache acceleration only
RabbitMQ   -> outbox delivery and BFF invalidation hints
```

The intended security invariant is: **authentication establishes a
provider-qualified subject; each service independently authorizes that subject
against its own resource state; cache and BFF filtering never grant access.**

## Findings summary

| ID | Severity | Area | Status | Release effect |
| --- | --- | --- | --- | --- |
| SEC-001 | Critical | Passwordless token issuance | Confirmed source defect | Blocks passwordless production release |
| SEC-002 | High | Identity lifecycle and subject model | Confirmed design/implementation gap | Blocks identity assurance until resolved |
| SEC-003 | High | WebSocket authorization lifecycle | Confirmed source gap | Blocks realtime authorization sign-off |
| SEC-004 | High | Profile lookup authorization | Confirmed source/API exposure | Blocks profile privacy sign-off |
| SEC-005 | High | Service-to-service trust boundary | Partially implemented; deployment-dependent | Blocks direct-service exposure without network proof |
| SEC-006 | Medium | Cookie-authenticated state changes | Defense-in-depth gap | Required before browser policy is complete |
| SEC-007 | Medium | Distributed rate-limit partitioning | Confirmed resilience weakness | Can cause unfair throttling or weak abuse control |
| SEC-008 | Medium | Access-token revocation semantics | Known residual design gap | Requires explicit product/security decision |
| SEC-009 | Medium | Provider integration and managed-provider proof | Not implemented/proven | Blocks provider portability claim |
| SEC-010 | Medium | Secrets, key rotation, and incident operations | Partial evidence only | Blocks operational security approval |
| SEC-011 | Medium | Supply-chain and deployment security | Partial CI evidence | Blocks production release gate |
| SEC-012 | Low/Medium | Security observability and audit events | Incomplete evidence | Weakens detection and response |
| SEC-013 | Low/Medium | Error/contract and negative-path completeness | Partial matrix | Requires endpoint-level closure |

## Detailed findings

### SEC-001: Passwordless verification cannot issue deployed access tokens

**Severity:** Critical
**Evidence level:** Direct source inspection; isolated tests use a different
provider; current local runtime did not provide a successful deployed
passwordless proof in this audit.

**Evidence:**

- `AuthSessionConfiguration.kt:47-53` registers `ExternalOidcTokenProvider`
  for `production`, `staging`, and `local-oidc`.
- `ExternalOidcTokenProvider.kt:36-38` always throws
  `UnsupportedOperationException`.
- `LoginVerificationService.kt:59-64` calls `TokenSessionService.createSession`.
- `TokenSessionService.createSession` calls `IdentityProviderPort.issueAccessToken`.
- `TestIdentityProviderConfiguration.kt` is the only configuration that injects
  `InternalJwtTokenProvider`, and it is restricted to `test`.

**Impact:** A valid magic link/code can be redeemed, but the session transaction
cannot return the access token required by the resource services. If the
transaction rolls back, the user cannot log in; if provider behavior changes
without a transaction policy, issuance and session state could diverge.

**Required decision:** Choose exactly one complete model:

1. Squarewise owns the access-token issuer: deploy a real asymmetric signer,
   publish trusted JWKS metadata, use a distinct issuer and audience, rotate
   keys, and configure every resource server to trust that issuer; or
2. The external provider owns issuance: implement a real authenticated RFC 8693
   token exchange/delegation adapter, with provider-specific capability
   configuration, resource/audience restriction, timeouts, retry policy,
   subject mapping, and contract tests.

Do not retain a bean that appears operational but throws at the security
boundary. Startup must fail if the selected provider cannot issue tokens.

**Acceptance evidence:** A fresh local-oidc and provider-like integration test
must start from login start, retrieve the delivered credential, verify it,
validate the returned token at all four resource boundaries, rotate it, replay
the old refresh token, and verify generic failure without raw-token logging.

### SEC-002: Identity lifecycle is email-derived rather than provider-qualified

**Severity:** High
**Evidence level:** Direct source inspection.
`LoginVerificationService.kt:53-56` constructs `internal:$canonicalEmail` and
passes it as the durable subject. `JpaProfileStore.get` provisions a profile if
the subject does not exist. This conflicts with the documented target model in
which `(issuer, subject)` is the stable authorization identity and email is
mutable contact data.

**Risks:**

- Changing an email can create a new identity rather than update contact data.
- Identity linking, account merge, provider-subject changes, and account
  recovery cannot be represented safely.
- A valid provider token with a previously unseen subject can cause implicit
  local profile creation through `/me`.
- The same email across provider/issuer boundaries is not a sufficient identity
  binding.

**Recommendation:** Introduce an explicit identity mapping owned by Accounts:
`issuer`, `providerSubject`, `accountId`, status, and audit timestamps. Resolve
or provision only through an explicit enrollment/linking policy. Store email as
normalized contact data with its own uniqueness and verification state. Never
derive an authorization subject by concatenating email.

### SEC-003: Existing WebSocket subscriptions are not re-authorized after membership changes

**Severity:** High
**Evidence level:** Direct source inspection; initial-subscription tests do not
prove continuous revocation.

`GroupGraphqlController.kt:47-55` authorizes the subject when the subscription
is created and calls `gateway.getGroup` once. The subsequent stream filters
fanout events by `groupId` without re-checking current membership or token/session
validity.

**Impact:** A member removed after subscribing may continue receiving group
invalidation events until the socket closes. Even if events contain no balances,
group activity and revision timing are protected information, and the behavior
violates the documented requirement that removal blocks future access including
existing subscriptions.

**Recommendation:** Add a revocation-aware subscription policy. Options include:

- re-check membership at a bounded interval and before each event;
- publish authorization-version invalidations and terminate affected streams;
- bind a subscription to a short-lived authorization lease and require
  resubscription; and
- close/reject the socket when bearer expiry or security-event revocation is
  observed.

The preferred design is event-driven invalidation plus a bounded periodic
verification fallback. Add tests for removal, account deletion, session family
revocation, token expiry, reconnect, duplicate subscriptions, and stale events.

### SEC-004: Authenticated profile lookup endpoints permit arbitrary account-ID reads

**Severity:** High
**Evidence level:** Direct source inspection and contract review.

`ProfileController.kt:111-123` exposes `GET /profiles/{accountId}` and
`POST /profiles/batch` without comparing the requested IDs to the caller or to
an authorized group context. `JpaProfileStore.findById/findByIds` directly
returns profile DTOs.

The DTO includes display name, timezone, and default currency. The service-wide
JWT chain only proves that the caller is authenticated; it does not authorize
which account IDs may be read.

**Recommendation:** Make the profile projection an internal, least-privilege
operation. Either:

- remove it from the public user-facing surface and authorize it with a
  dedicated service credential plus an explicit purpose/scope; or
- require a group/resource authorization context and return only profiles for
  active members of that resource.

Add tests for a valid user reading another unrelated account, batch mixing
authorized and unauthorized IDs, deleted accounts, and direct access when the
BFF is bypassed. Do not rely on UUID unpredictability as authorization.

### SEC-005: Service-to-service trust is network-dependent, not cryptographically scoped

**Severity:** High
**Evidence level:** Source/configuration review; production network proof not
available.

The four resource servers validate the same configured API audience and the BFF
propagates the user's bearer token downstream. Production Compose places
services on an internal network, but there is no distinct service identity,
service audience, mTLS, or workload authorization in application code.

**Impact:** If an internal service becomes reachable through a routing mistake,
compromised workload, SSRF, or misconfigured ingress, a normal user token can
be replayed directly against downstream APIs. Profile lookup magnifies this
risk.

**Recommendation:** Keep user authorization at the domain service, but add a
separate service-to-service trust layer: mTLS or workload identity, distinct
audiences/scopes, explicit downstream route policy, and network policy that
exposes only the BFF publicly. If the BFF acts on behalf of a user, use a
proper delegation model (for example RFC 8693) or a verifiable actor claim;
do not infer trust from a private network alone.

### SEC-006: CSRF protection is narrower than the complete cookie-authenticated surface

**Severity:** Medium
**Evidence level:** Direct source inspection and focused BFF tests.

`BrowserCsrfWebFilter.kt:22-36` requires the double-submit nonce only for
`/auth/token/refresh` and `/auth/logout`. GraphQL receives an access cookie via
`BrowserAccessCookieWebFilter` and can execute mutations, but the generic CSRF
filter does not cover `/graphql`.

SameSite=Lax and exact-origin checks reduce browser CSRF risk, and the current
policy rejects unlisted origins. They are not equivalent to a complete
state-changing cookie policy across deployments, proxy behavior, same-site
subdomains, and future routes.

**Recommendation:** Either require the CSRF proof for every unsafe
cookie-authenticated GraphQL operation, or prohibit cookie authentication for
GraphQL mutations and require an explicit bearer/one-time browser action token.
Keep exact Origin validation as an additional control. Add tests for every
mutation, missing/mismatched nonce, origin-less requests, same-site untrusted
origins, preflight, and WebSocket origin handling.

### SEC-007: Refresh/login rate limiting uses a coarse and proxy-sensitive network key

**Severity:** Medium
**Evidence level:** Historical source inspection; implementation status updated below.

**Current status (2026-10-06):** The former direct `remoteAddr` bucket path has
been replaced by the shared HMAC-derived limiter and bounded client-address
partition resolver. Local tests and shared-Redis multi-replica/outage probes
cover the current path. Hosted proxy-chain validation and production edge
configuration remain open.

`AuthController.kt:158-161` uses `remoteAddr`, and for IPv4 reduces it to the
first two octets. There is no explicit trusted-proxy configuration in this
boundary.

**Impact:** Many legitimate users behind a shared NAT or corporate proxy can
consume one bucket and deny each other. Conversely, the effective key may be a
single reverse-proxy address in production, reducing abuse isolation. Client
supplied forwarding headers must not be trusted without a bounded, configured
proxy chain.

**Recommendation:** Put coarse network limits at the edge, and make application
limits multi-dimensional: canonical account/email digest, session family,
client identifier, and a verified network partition. Use framework-supported
forwarded-header handling only when the trusted proxy list is explicit. Test
IPv4, IPv6, NAT, proxy chains, spoofed forwarding headers, and Redis outage.

### SEC-008: Access-token revocation is not immediate

**Severity:** Medium
**Evidence level:** Confirmed design behavior.

PostgreSQL authoritatively revokes refresh sessions, logout families, and
deletion-related sessions. Ordinary bearer requests are intentionally validated
locally and do not query `auth_sessions`. Consequently, an already-issued access
token remains usable until expiry even after logout, deletion, or suspicious
refresh replay.

This can be acceptable for a short-lived access token, but it must be an
explicit product/security decision, not an implied guarantee of “logout”.

**Recommendation:** Keep access tokens short-lived and document the bounded
revocation window. For higher-risk operations, add a revocation version or
introspection check with a safe cache strategy. Do not make every request
database-bound without measuring the cost. Add a test and operational metric
for the maximum post-revocation access window.

### SEC-009: External-provider portability is an interface, not an implementation

**Severity:** Medium
**Evidence level:** Direct source inspection and documentation reconciliation.

The provider-neutral SPI is a good boundary, but `ExternalOidcTokenProvider`
contains no HTTP client, client authentication, token endpoint, provider
capability discovery, timeout, retry, audience/resource binding, or subject
mapping. `clientId` is populated from the API audience in
`AuthSessionConfiguration.kt:49-52`, which is not generally a valid client
registration.

**Recommendation:** Treat each provider adapter as a separate implementation
task. Define a provider capability contract, use RFC 8414 metadata, require
authenticated token endpoint calls, bound all network operations, redact
requests/responses, and run a second-provider compatibility suite. Do not claim
managed-provider support from the SPI alone.

### SEC-010: Key rotation and incident-response controls are incomplete

**Severity:** Medium
**Evidence level:** Documentation/configuration review.

The repository requires deployment secrets and protects auth-email envelopes,
but the audit did not find complete executable evidence for:

- overlapping JWT signing-key rotation and old-key retirement;
- credential-digest secret rotation without invalidating or mishandling active
  credentials;
- auth-email envelope-key rotation and replay/retention handling;
- Redis, database, broker, provider, and BFF credential rotation;
- emergency global session revocation and operator authorization;
- immutable security-event retention and access review;
- recovery after a signing key or refresh secret compromise.

**Recommendation:** Add key-versioned cryptographic configuration, dual-key
verification windows, rotation runbooks, rotation rehearsal tests, incident
roles, and evidence artifacts. Store production secrets in a managed secret
system; never rely on environment variables as the entire operational control.

### SEC-011: Supply-chain and deployment security are only partially evidenced

**Severity:** Medium
**Evidence level:** CI/repository review.

The repository has dependency centralization, SBOM generation, hygiene checks,
workflow validation, and image hardening defaults. The reviewed evidence does
not close vulnerability scanning, image provenance/signing, license policy,
base-image patch cadence, admission verification, or target-environment
rollback/restore security.

**Recommendation:** Require SBOM publication plus vulnerability thresholds,
container scanning, signed images and provenance attestations, dependency
renovation, secret scanning, license review, minimal runtime images, and a
reproducible deployment verification step. Record exceptions with owner,
expiry, affected artifact, and compensating control.

### SEC-012: Security telemetry is useful but not a complete audit trail

**Severity:** Low/Medium
**Evidence level:** Source/docs review.

Token redaction and structured observability controls exist, and the login path
logs only an account ID. The audit did not find a complete, durable security
event model for login success/failure, refresh replay, logout, deletion
revocation, subject change, authorization denial, provider outage, key rotation,
and administrator actions.

**Recommendation:** Emit structured security events with correlation ID,
provider/subject hash or stable pseudonymous ID, event type, outcome, reason
class, service, and timestamp. Exclude credentials, tokens, email where not
needed, raw IP where policy prohibits it, and request bodies. Protect event
integrity, retention, access, and clock synchronization.

### SEC-013: Endpoint evidence is broad but not yet complete for all security dimensions

**Severity:** Low/Medium  
**Evidence level:** Matrix/documentation review.

The repository reports 45 REST operations, GraphQL operations, and WebSocket
coverage. The operation matrix itself still lists unresolved failure, replay,
timeout, dependency, backpressure, and production-environment dimensions.
Local success/negative tests cannot prove multi-replica authorization, provider
outage behavior, proxy correctness, or target deployment controls.

**Recommendation:** Keep the endpoint matrix authoritative and require each row
to identify: authentication, object authorization, input bounds, rate limit,
idempotency, error shape, timeout, dependency failure, cache failure, replay,
concurrency, logging/redaction, and highest evidence level. A shared security
filter test must never close an endpoint-specific authorization row.

## Control-by-control assessment

### Authentication and token validation

**Implemented or strongly evidenced:**

- Resource-server chains exist in Accounts, Expense Core, Notifications, and
  BFF for `production`, `staging`, and `local-oidc`.
- Issuer discovery, issuer validation, audience validation, temporal validation,
  bounded subject validation, and asymmetric algorithm policy are centralized in
  `libs/security`.
- Missing issuer/audience and non-HTTPS production-like issuer configuration
  fail closed.
- Forged signature, wrong issuer, wrong audience, expired, invalid subject, and
  unsupported-algorithm negative paths are represented in local evidence.

**Open:** access-token issuance, provider-qualified identity mapping, key
rotation, managed-provider compatibility, and immediate revocation policy.

### Passwordless credentials

**Implemented or strongly evidenced:** canonical email handling, one-time
credential digests, expiry, attempt policy, generic accepted responses, encrypted
auth-email handoff, outbox delivery, and replay rejection.

**Open:** deployed token issuance (SEC-001), account identity mapping (SEC-002),
credential and envelope-key rotation, delivery-provider outage/retry evidence,
and complete abuse controls across account, email, IP, device, and provider
dimensions.

### Refresh sessions, logout, and deletion

**Implemented or strongly evidenced:** opaque refresh tokens, HMAC digests at
rest, rotation, atomic compare-and-set, idle and absolute expiry, family replay
revocation, subject restoration from writer state, logout ownership checks, and
bulk refresh-session revocation during deletion.

**Open:** access-token revocation window (SEC-008), session issuance provider,
key rotation, and durable security-event/audit coverage.

### Resource authorization

Expense Core performs service-owned group membership checks for ordinary group,
expense, settlement, search, export, sync, invitation, and recurring-schedule
operations. Notifications resolves inbox/preferences by authenticated subject.
Accounts `/me` operations use the principal subject.

The profile lookup endpoints remain the exception: they accept arbitrary account
IDs after authentication (SEC-004). WebSocket membership is checked only at
subscription start (SEC-003). These are authorization findings, not merely test
coverage gaps.

### Browser, CORS, and CSRF

The BFF uses exact HTTP(S) origins, rejects wildcard/path/credential-bearing
origins, sets Secure/HttpOnly/SameSite=Lax access and refresh cookies, keeps the
refresh cookie under `/auth`, uses a readable CSRF nonce only for the double
submit proof, and keeps token material out of browser response bodies.

The remaining concern is applying the CSRF proof consistently to every unsafe
cookie-authenticated operation, especially GraphQL mutations (SEC-006). Browser
security also requires deployment HTTPS, secure proxy headers, HSTS, CSP where
HTML is served, clickjacking protection, and an explicit cookie domain policy.

### WebSocket security

The handshake is protected by the reactive resource-server chain and the BFF
origin filter. The GraphQL subscription limits per-user subscriptions and
filters events by group. Continuous authorization, revocation, expiry,
duplicate-subscription protocol handling, queue/backpressure, and reconnect
semantics remain incomplete (SEC-003 and SEC-013).

### Cache and database authority

The design correctly keeps PostgreSQL authoritative for sessions, revocation,
identity, and financial authorization. Redis is used for rate limiting and is
expected to fail closed with bounded waits. Local cache-resilience evidence
covers eviction, restart, and Redis outage.

The rate-limit key-quality concern is addressed in the current implementation
by deployment-HMAC derivation and bounded client partitioning. Remaining
concerns are hosted alert/cardinality evidence and ensuring no future profile
or membership cache becomes a stale authorization authority.

### Input, protocol, and error security

Request validation, bounded bodies, GraphQL depth/complexity controls,
idempotency, pagination, formula-injection protection, structured errors, and
token response cache directives are present in the reviewed implementation.

For closure, add endpoint-specific tests for malformed paths, duplicate
parameters, oversized headers/bodies, content-type confusion, method override,
HTTP request smuggling at the ingress, GraphQL alias/batch amplification,
WebSocket malformed frames, and upstream timeout/error redaction.

### Secrets, cryptography, and data protection

Raw refresh tokens and one-time credential digests are not stored as plaintext;
auth-email delivery uses an authenticated encryption envelope; test-only JWT
signing uses a minimum key length; production-like required secrets fail closed.

Open controls include asymmetric production signing, key versioning and
rotation, secret-manager integration, envelope retention/destruction, database
encryption/backups, PII minimization, export/deletion propagation, and formal
data-retention/access-review policy.

### Messaging and background processing

Transactional outbox, publisher confirms, manual acknowledgements, bounded
retries, and deduplication provide a good foundation. Security review must still
prove: broker TLS/authentication, per-service vhost/permission isolation,
malicious event validation, replay/poison-message handling, auth-email envelope
key access isolation, and no sensitive payload leakage to dead-letter queues or
management UIs.

### Operations, detection, and response

Runbooks exist for authentication outages, provider outages, session
revocation, cache behavior, and production readiness. Release approval still
requires executed evidence for TLS/ingress, secret rotation, restore/failover,
image scanning, capacity/abuse behavior, alert routing, incident response, and
rollback.

## Recommended remediation order

1. **P0:** Resolve SEC-001 by selecting and implementing one real token issuer
   model; add deployed-profile context tests that fail startup or the journey
   when issuance is unavailable.
2. **P0:** Resolve SEC-002 with an explicit issuer/subject identity mapping and
   remove implicit profile provisioning from ordinary bearer reads.
3. **P0:** Resolve SEC-003 with continuous WebSocket authorization/revocation and
   removal/expiry/reconnect tests.
4. **P0:** Resolve SEC-004 and SEC-005 by narrowing profile APIs and enforcing
   service/network/workload boundaries.
5. **P1:** Apply CSRF consistently to cookie-backed GraphQL mutations; harden
   proxy-aware rate limits; document the bounded access-token revocation window.
6. **P1:** Implement the provider adapter and second-provider contract suite;
   never mark the SPI as provider compatibility evidence.
7. **P1:** Add key/secret rotation and emergency revocation rehearsals.
8. **P1:** Complete image/dependency/provenance/security scanning and target
   deployment evidence.
9. **P1:** Add durable security events, alert thresholds, and operator access
   review.
10. **P2:** Consider DPoP for native/high-value clients and complete protocol
    fuzzing/abuse testing.

## Required verification package for re-audit

The next audit should retain exact command output and artifacts for:

```text
./gradlew.bat test check jacocoTestReport bootJar --parallel --no-daemon
uv run --frozen --no-build python tools/contracts/validate.py
uv run --frozen --no-build python tools/contracts/validate_public_surface.py
uv run --frozen --no-build mypy tests tools
uv run --frozen --no-build python tools/ops/check_security_hygiene.py
uv run --frozen --no-build python tools/ops/check_architecture.py
uv run --frozen --no-build python tools/ops/validate_sbom_baseline.py
./gradlew.bat cyclonedxBom --no-daemon
```

And, separately, executable evidence for:

- deployed-profile passwordless verify and refresh;
- real OIDC token acceptance and invalid-token rejection at every service;
- identity enrollment/linking and email-change behavior;
- profile IDOR and batch authorization denial;
- direct-service access and workload/network policy;
- WebSocket removal, deletion, expiry, replay, reconnect, and backpressure;
- cookie GraphQL mutation CSRF/origin matrix;
- IPv4/IPv6/NAT/proxy rate-limit behavior;
- signing/envelope/digest secret rotation;
- provider outage, Redis outage, broker outage, database failover, and restore;
- image/dependency vulnerability and provenance policy;
- security-event emission, redaction, retention, alerting, and operator access.

## Evidence boundary and tracking

This document supplements, and does not silently overwrite, the existing
`AUTH-08`, `QA-08`, and production-readiness records. The repository's earlier
local green test evidence remains valid at its stated evidence level, but it
does not close the findings above. In particular, historical progress entries
claiming passwordless token issuance must be reconciled with the current
`ExternalOidcTokenProvider` source before being used as release evidence.

No production approval should be recorded until the P0 findings have a linked
task, implementation commit, focused regression tests, live evidence, and an
updated endpoint/security matrix.
