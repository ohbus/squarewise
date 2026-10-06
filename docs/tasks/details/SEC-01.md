# SEC-01: Whole-security remediation plan

## Status, objective, and evidence boundary

**Status:** planned. This is a documentation and tracking task only; it does
not authorize runtime remediation.

**Objective:** turn every SEC-001 through SEC-013 finding in
[`whole-security-implementation-audit-2026-09-29.md`](../../security/whole-security-implementation-audit-2026-09-29.md)
into small, owned, dependency-ordered implementation tasks with exact paths,
contracts, acceptance criteria, validation, and release evidence.

The audit is the finding source of truth. This plan is the execution source of
truth. It must not relabel a passing unit test, a Compose probe, or a
documentation statement as production security evidence.

The current public-production decision is **NO-GO**. SEC-001 through SEC-005
are P0 release blockers. SEC-006 through SEC-013 must close before browser,
operational, and public-release claims can be approved.

## Inputs and non-negotiable invariants

### Inputs

- `docs/security/whole-security-implementation-audit-2026-09-29.md`
- `docs/tasks/details/AUTH-08.md`
- `docs/security/authentication-threat-model.md`
- `docs/security/endpoint-authentication-matrix.md`
- `contracts/`, `docs/quality/public-interface-operation-matrix.md`, and
  `docs/reviews/production-readiness-audit.md`

### Invariants

1. PostgreSQL is authoritative for identity mappings, refresh-session families,
   revocation, and durable security events. Redis never approves a revoked
   session or resource decision.
2. Resource servers validate ordinary bearer tokens locally. A normal bearer
   request must not add an Accounts/session database lookup.
3. Durable identity is `(issuer, subject)`. Email is verified contact data, not
   an authorization key or a substitute subject.
4. Every service authorizes its own resources. The BFF, caches, UUID opacity,
   and client-supplied IDs do not grant access.
5. Credentials, tokens, secrets, plaintext one-time codes, and unbounded PII
   never enter logs, metrics, errors, URLs, browser storage, or generic events.
6. Security uncertainty fails closed: issuer configuration, token issuance,
   identity lookup, session transition, rate-limit authority, membership state,
   provider availability, and key material.
7. A child task changes only registered owned paths, updates docs/contracts
   first, produces a small coherent commit, and pushes only its session branch.

## Standards baseline

| Standard | Required application |
| --- | --- |
| RFC 9700 | Exact redirects, authorization code, PKCE, CSRF/mix-up controls, secure lifecycle, and no deprecated grants. |
| RFC 7636 and RFC 8252 | PKCE `S256`, system-browser native authorization, and no embedded native secret. |
| RFC 8414 | Trusted issuer metadata/discovery only. |
| RFC 8693 | Token exchange only with authenticated, resource/audience-bound provider support. |
| RFC 7009 | Provider revocation only when the provider owns the actual grant handle. |
| RFC 8725 and RFC 9068 | Explicit JWT algorithm, issuer, audience, claim, and key-rotation policy. |
| RFC 9449 | Optional DPoP only after the bearer model is otherwise secure. |
| RFC 9457 | Stable, redacted security failure responses. |
| OWASP ASVS/API Security Top 10 | Defense in depth for authorization, browser controls, secrets, input limits, telemetry, supply chain, and verification. |

## Dependency graph and finding mapping

```text
SEC-01A token-authority decision and operational issuer
       |
       +--> SEC-01B durable identity mapping and lifecycle
       |          +--> SEC-01E session, revocation, rate-limit policy
       +--> SEC-01C profile object authorization and workload trust
       +--> SEC-01D browser GraphQL and WebSocket authorization

SEC-01F cryptographic operations, telemetry, supply chain, and release evidence
       ^---------------------------------------------------------------+
                             consumes A through E evidence
```

| Finding | Workstream | Closure evidence |
| --- | --- | --- |
| SEC-001, SEC-009 | SEC-01A | Deployed-profile passwordless journey issues a real token; provider capability test supports the selected model. |
| SEC-002 | SEC-01B | Stable issuer/subject map, migration outcome, lifecycle tests, and no implicit account provisioning. |
| SEC-004, SEC-005 | SEC-01C | Unauthorized profile/direct-service reads denied; narrow projection and workload identity work. |
| SEC-003, SEC-006 | SEC-01D | Active-subscription revocation and every cookie-authenticated unsafe operation covered. |
| SEC-007, SEC-008 | SEC-01E | Trusted-proxy/rate-limit matrix and explicit bearer revocation policy. |
| SEC-010 through SEC-013 | SEC-01F | Rotation, telemetry, supply-chain, deployment, and endpoint-matrix evidence. |

## Execution protocol

Before implementation, the coordinator registers each selected workstream as a
child task with a single owner, non-overlapping owned paths, dependencies, and
acceptance evidence. Child implementation starts only after its design gate,
contract changes, and task detail are reviewed. Each child produces small,
coherent commits on its session branch; it must update its documentation and
progress evidence in the same increment. No child may close a finding merely
by adding cache reads, a test-only bean, an allow-list exception, or an
unverified deployment assertion.

The workstreams below are deliberately ordered. The token authority and
identity model determine the claims that authorization, session, browser, and
workload boundaries may trust. Operations and release evidence consume the
resulting implementation rather than declaring it secure in advance.

## SEC-01A: establish an operational token authority

**Closes:** SEC-001 and SEC-009. **Prerequisite:** an explicit architecture
decision approved before any deployed-profile token code changes.

### Decision gate

Choose exactly one supported authority model and document the rejected model,
threats, provider capabilities, rollout, rollback, key ownership, and recovery
procedure in an ADR and the authentication contracts.

1. **Squarewise-owned issuer (preferred unless a managed provider is already
   verified):** implement an Accounts-owned, asymmetric signing authority with
   discoverable issuer metadata/JWKS, stable key identifiers, issuer-specific
   `aud`, short access-token lifetime, and independently rotating refresh
   sessions. Resource servers validate JWKS-backed tokens locally.
2. **Managed issuer/token exchange:** retain external issuance only where the
   actual provider supports the required authenticated token endpoint,
   credential type, subject-token semantics, resource/audience restriction,
   metadata discovery, timeout behavior, and revocation semantics. Use RFC
   8693 only for a provider-supported exchange; it is not permission to mint
   a token from an arbitrary account record. The `client_id` is never inferred
   from the resource audience.

The current `ExternalOidcTokenProvider` throwing implementation cannot be a
deployed-profile bean after this decision. The selected model must make
passwordless verification either complete the provider-owned authorization
transaction or issue a Squarewise-owned token; no test-only issuer may mask a
runtime failure.

### Planned implementation surface

- `app/accounts/src/main/kotlin/com/squarewise/accounts/auth/config/AuthSessionConfiguration.kt`
- `app/accounts/src/main/kotlin/com/squarewise/accounts/auth/provider/IdentityProviderPort.kt`
- `app/accounts/src/main/kotlin/com/squarewise/accounts/auth/provider/ExternalOidcTokenProvider.kt`
- `app/accounts/src/main/kotlin/com/squarewise/accounts/auth/login/LoginVerificationService.kt`
- `app/accounts/src/main/kotlin/com/squarewise/accounts/auth/session/TokenSessionService.kt`
- `app/*/src/main/kotlin/**/security/ProductionSecurityConfig.kt`
- `contracts/identity/`, `contracts/error-catalog/`,
  `docs/security/`, `docs/architecture/`, and `docs/operations/`

### Acceptance and verification

- A production/staging configuration proves the complete passwordless journey
  can return a valid token without a test profile, synthetic issuer, or
  unsupported operation.
- Every resource server rejects incorrect issuer, audience, algorithm, key ID,
  expired/not-yet-valid token, missing required claim, and unknown key. It
  accepts only a current, correctly scoped token after JWKS rotation.
- Provider metadata, endpoint failures, unknown keys, and issuer ambiguity
  fail closed with RFC 9457-compatible redacted errors and bounded timeouts.
- Unit tests cover provider ports/adapters; integration tests cover signing or
  token exchange, discovery/JWKS, rotation overlap, and restart; Compose/E2E
  covers the selected deployed profile.

## SEC-01B: make durable identity independent of email

**Closes:** SEC-002. **Depends on:** SEC-01A claim and issuer decision.

### Design and migration work

Create a writer-owned identity mapping with a unique normalized `(issuer,
subject)` key, its immutable account/profile identifier, verified-email
history/status, lifecycle state, timestamps, and migration provenance. Treat
email changes, aliases, recycled addresses, deleted accounts, and provider
subject changes as explicit transitions. A request for `/me` must resolve an
already linked identity; it must never provision a profile as a side effect.

Add a Flyway migration with a preflight report, deterministic backfill rules,
collision/quarantine handling, rollback/restore procedure, and a post-migrate
invariant query. The migration must be forward-only and writer-owned; no other
service receives direct access to Accounts tables.

### Planned implementation surface

- `app/accounts/src/main/kotlin/com/squarewise/accounts/profile/`
- `app/accounts/src/main/kotlin/com/squarewise/accounts/auth/login/LoginVerificationService.kt`
- `app/accounts/src/main/kotlin/com/squarewise/accounts/auth/session/TokenSessionService.kt`
- `app/accounts/src/main/resources/db/migration/`
- Accounts API contracts, identity lifecycle documentation, and account
  deletion/retention operations documentation.

### Acceptance and verification

- Same email under two issuers cannot merge identities; one issuer/subject
  cannot map to two active accounts; email rename does not change identity.
- Missing, deleted, disabled, or changed subject mappings deny access and emit
  a redacted security event; they never create an account during lookup.
- Migration tests cover empty, legacy, duplicate, malformed, deleted, and
  partially migrated fixtures; database integration tests prove uniqueness and
  concurrency behavior.
- Refresh and access claims use the stable identity mapping while preserving
  current session-family revocation and deletion invariants.

## SEC-01C: enforce object authorization and workload trust

**Closes:** SEC-004 and SEC-005. **Depends on:** SEC-01A and SEC-01B for
trusted caller and subject semantics.

### Profile authorization

Redesign `ProfileController` so public endpoints expose only the caller's own
profile or a documented, least-privilege projection required by a named domain
use case. Remove or protect arbitrary `GET /profiles/{id}` and batch lookup
behavior with service-owned relationship/role checks. UUID opacity, BFF
filtering, and a client assertion are not authorization. Update REST/GraphQL
contracts before controllers and make denied/not-found disclosure deliberate
and consistent with the error policy.

### Service-to-service trust

Define a separate workload principal from end-user bearer identity. The
architecture decision must choose platform workload identity or mutually
authenticated TLS plus dedicated service audience/claims, specify credential
issuance/rotation, and retain each service's resource authorization. Private
Docker networking and forwarding a user token are insufficient. Do not create
cross-service SQL access as a shortcut; use a narrow HTTPS/event contract.

### Planned implementation surface

- `app/accounts/src/main/kotlin/com/squarewise/accounts/profile/api/ProfileController.kt`
- `app/accounts/src/main/kotlin/com/squarewise/accounts/profile/`
- `app/bff/src/main/kotlin/com/squarewise/bff/`
- Production security configurations in Accounts, Expense Core, Notifications,
  and BFF; affected REST/GraphQL contracts and Bruno collections.

### Acceptance and verification

- Anonymous, cross-user, stale-membership, malformed-ID, bulk-enumeration,
  and privileged-role tests prove deny-by-default behavior on every profile
  operation.
- Workload calls with a user audience, wrong service identity, expired client
  credential, or no caller relationship are rejected. Valid workload calls are
  limited to documented projections and operations.
- Contract/public-surface validation and live E2E probes prove no accidental
  reintroduction of broad profile discovery or service trust by network alone.

## SEC-01D: close browser mutation and subscription time-of-check gaps

**Closes:** SEC-003 and SEC-006. **Depends on:** SEC-01B for membership and
account lifecycle events.

### WebSocket authorization continuity

Replace one-time subscription admission with an explicit authorization-lifetime
model. Membership removal, group deletion, account deletion, role downgrade,
session-family revocation, and token expiry must reach an active subscription
and close or re-authorize it before further group data is delivered. Prefer a
membership/session authorization epoch carried through invalidation events;
define bounded reconnect/revalidation fallback for delivery failure. Never use
a periodic cache lookup as the sole revocation guarantee.

### Browser unsafe-operation protection

Inventory all cookie-authenticated GraphQL mutations and REST methods. Extend
the exact-origin, double-submit CSRF boundary to every unsafe operation,
including GraphQL transport, while preserving explicit bearer/native behavior.
Define SameSite, Origin, Fetch Metadata (where usable), cookie path/domain,
preflight, error-body, and no-store behavior. A state-changing request without
the browser proof must be rejected before its resolver/service executes.

### Planned implementation surface

- `app/bff/src/main/kotlin/com/squarewise/bff/transport/graphql/GroupGraphqlController.kt`
- `app/bff/src/main/kotlin/com/squarewise/bff/transport/http/BrowserCsrfWebFilter.kt`
- `app/bff/src/main/kotlin/com/squarewise/bff/transport/http/BrowserOriginWebFilter.kt`
- `app/bff/src/main/kotlin/com/squarewise/bff/transport/http/BrowserAccessCookieWebFilter.kt`
- BFF WebSocket/session/membership event adapters, GraphQL contracts, browser
  security documentation, and GraphQL/WebSocket test suites.

### Acceptance and verification

- An already connected subscriber receives no post-revocation event after
  membership/session invalidation; reconnect requires current authorization.
- Cross-origin and CSRF-missing/mismatched cookie-authenticated mutations are
  rejected, while valid same-origin cookie and explicit bearer calls retain
  their intended behavior.
- Tests exercise admission, midstream removal, event ordering/duplicate
  invalidations, broker/cache outage, reconnect, token expiry, and resolver
  non-execution on rejected browser requests.

## SEC-01E: formalize rate-limit and bearer-revocation guarantees

**Closes:** SEC-007 and SEC-008. **Depends on:** SEC-01A and SEC-01B.

### Abuse-control source address

Define the trusted proxy topology and a canonical client-address extraction
component. Ignore all forwarded headers unless the immediate peer is an
explicit trusted proxy; parse a bounded, documented header chain; reject
ambiguous/malformed input; and preserve privacy by storing only the minimum
necessary derived signal. Replace the current two-octet heuristic with a
policy reviewed for IPv4, IPv6, NAT, and proxy deployment.

### Access-token residual window

Document a bounded compromise/logout policy: maximum access-token lifetime,
what refresh-family revocation invalidates immediately, which high-risk routes
require session-version/current-state confirmation or deny until re-login, and
how deletion/security events reach every relevant boundary. Normal resource
requests remain locally verifiable and database-free; any exceptional online
check is narrow, cached safely, and fails closed. Redis accelerates a durable
revocation/version decision but never becomes the authority.

### Planned implementation surface

- `app/accounts/src/main/kotlin/com/squarewise/accounts/auth/abuse/`
- `app/accounts/src/main/kotlin/com/squarewise/accounts/auth/session/`
- shared security policy/configuration and production proxy configuration
- authentication/session contracts, abuse-control operations runbooks, and
  Accounts/BFF security tests.

### Acceptance and verification

- Tests cover direct, trusted-proxy, spoofed-forwarded, malformed, IPv4, IPv6,
  NAT, Redis-available, Redis-outage, and restart cases with a documented
  response and audit outcome.
- Logout, compromise, passwordless replay response, and account deletion have
  an explicit access-token residual window; high-risk endpoint behavior is
  tested at each state transition.
- Rate-limit authority failure never silently permits a protected login/verify
  request, and cache eviction/restart cannot resurrect a revoked session.

## SEC-01F: make security operations and release evidence executable

**Closes:** SEC-010, SEC-011, SEC-012, and SEC-013. **Consumes:** evidence
from SEC-01A through SEC-01E.

### Cryptography, secrets, and incident operations

Specify key/secret inventory, owners, rotation cadence, overlap windows, JWKS
cache behavior, emergency compromise procedure, break-glass authorization,
rollback, and post-incident reissue/revocation. Store durable, redacted
security events for authentication, token/session lifecycle, authorization
denials, admin/workload actions, key changes, and configuration changes.
Define retention, access review, correlation identifiers, alert thresholds,
on-call ownership, and testable runbooks without logging credentials or PII.

### Supply chain, deployment, and evidence matrix

Extend CI/release policy with pinned action/dependency review, dependency and
container vulnerability thresholds/exceptions, SBOM provenance, artifact/image
signing/verification, secret scanning, environment configuration admission,
deploy-time smoke/negative checks, rollback, and periodic restore/failover
tests. Complete the endpoint matrix with endpoint-specific anonymous,
authenticated, cross-user, wrong-audience, revoked, browser, and WebSocket
evidence; distinguish local evidence from hosted production proof.

### Planned implementation surface

- `docs/operations/`, `docs/security/`, `docs/quality/`,
  `docs/api/implementation-status.md`, and endpoint matrices
- `.github/workflows/`, CI helper scripts, SBOM/security-hygiene tooling, and
  deployment manifests only after their child tasks are registered
- security event schemas/adapters within the owning service, with contracts
  and redaction tests.

### Acceptance and verification

- A tabletop key-compromise exercise and a controlled rotation prove old/new
  key overlap, unknown-key failure, emergency invalidation, and recovery.
- Security event tests prove required fields/correlation and prove absence of
  access tokens, refresh tokens, codes, secrets, and prohibited PII.
- CI produces and verifies required attestations/SBOMs, blocks policy failures
  or records approved time-bounded exceptions, and runs release negative
  probes. Hosted CI/deployment, HA/DR, restore, capacity, and alert-delivery
  evidence remain separate named gates until actually observed.

## Cross-workstream verification and completion criteria

Each child declares focused commands in its detail. The parent integration
gate will run the applicable commands below after source changes, record exact
versions/results in `docs/tasks/progress.md`, and distinguish unavailable
Windows tooling from a passed gate:

```text
./gradlew.bat :libs:security:test --rerun-tasks --no-daemon
./gradlew.bat :app:accounts:test --rerun-tasks --no-daemon
./gradlew.bat :app:bff:test --rerun-tasks --no-daemon
./gradlew.bat test check jacocoTestReport bootJar --parallel --no-daemon
uv run python tools/contracts/validate.py
uv run python tools/contracts/validate_public_surface.py
uv run mypy tests tools
uv run python tools/ops/check_security_hygiene.py
uv run python tools/ops/check_architecture.py
uv run python tools/ops/validate_sbom_baseline.py
./gradlew.bat cyclonedxBom --no-daemon
git diff --check
```

SEC-01 is complete only when every SEC-001 through SEC-013 finding has either
verified closure with a named commit and reproducible evidence, or an accepted
time-bounded exception with owner, compensating control, and review date. The
final re-audit must review source, contracts, migrations, runtime configuration,
CI/release artifacts, and operational exercises. It must not replace production
proof with local tests, and it may remove the NO-GO decision only after the P0
blockers and their deployment evidence are closed.
