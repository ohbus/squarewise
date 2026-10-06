# AUTH-08: RFC-aligned authentication and session hardening

## Status

Complete for the implementation and local security-evidence scope. The first runtime increment implements bounded idle/absolute refresh
sessions and the second increment restores trusted account identity on refresh,
adds authenticated refresh-token logout, and removes caller-controlled client
identity from refresh rotation. REST/resource authorization and local
GraphQL/WebSocket/Redis failure evidence are now recorded. The current flow is
Squarewise-owned, so provider-grant revocation is explicitly not applicable;
the provider boundary and future delegated-grant requirements are documented in
`docs/security/provider-grant-revocation-boundary.md`. The BFF browser
cookie/CSRF runtime boundary is now implemented; the local cache matrix and
local OIDC negative evidence are complete. Hosted/production-scale evidence
remains a separately recorded release boundary under QA-08/OPS-22. Native
application integration remains outside the deferred UI boundary.

## Implemented increments

- `9c1e95c`: session timing policy, absolute-expiry migration, configuration,
  and policy tests.
- Current increment: writer-backed account identity restoration, deletion fail
  closed behavior, authenticated family logout, OpenAPI logout request contract,
  and controller/service tests.
- Follow-up hardening: Accounts production security now permits only the two
  login operations and token refresh by exact path; logout and all other routes
  remain authenticated.
- Follow-up hardening: the atomic refresh update enforces both idle and absolute
  expiry predicates, closing the race between the service pre-check and the
  database update at the hard session boundary.
- Follow-up hardening: the BFF GraphQL transport now applies a highest-precedence
  exact browser-origin policy. Unlisted origins receive `403 Forbidden`,
  allow-listed preflight receives `204 No Content` with a specific origin, and
  no credentialed or wildcard CORS response is emitted. Origin-less requests are
  retained for native/non-browser bearer clients.
- Follow-up hardening: an accepted account-deletion request now resolves the
  writer-owned account identifier and bulk-revokes all active refresh sessions in
  the same transaction. This prevents an already-issued refresh token from
  restoring access after deletion begins while retaining the profile for audit
  and financial attribution.
- Follow-up hardening: refresh sessions now retain the provider-qualified subject
  captured at issuance. Migration V9 backfills subjects where the account mapping
  is available; sessions with no subject or a changed subject fail closed and
  revoke their family before any replacement token is issued.
- Follow-up hardening: refresh admission tests now cover distributed-bucket
  denial, invalid server-derived partitions, and Redis/store uncertainty. The
  production path remains fail closed when the rate-limit authority cannot
  produce a decision.

## Objective

Complete the authentication lifecycle for Squarewise in a provider-neutral,
RFC-aligned manner. The implementation must preserve the existing local JWT
resource-server boundary, protect every REST, GraphQL, and WebSocket endpoint,
provide bounded session continuity while the user remains active, and terminate
sessions after inactivity or an absolute lifetime.

The work must minimize database visits without weakening security. Ordinary
bearer-token requests must validate locally and must not query Accounts or
`auth_sessions`. Refresh, logout, revocation, and security-event decisions
remain writer-authoritative and fail closed. Redis and existing cache facilities
may accelerate safe reads and rate limits, but a stale cache must never override
session revocation, refresh replay detection, identity changes, or resource
authorization.

## RFC and protocol baseline

The implementation and tests must align with:

- RFC 6749: OAuth 2.0 Authorization Framework.
- RFC 6750: OAuth 2.0 Bearer Token Usage.
- RFC 7009: OAuth 2.0 Token Revocation.
- RFC 7519: JSON Web Token.
- RFC 7636: Proof Key for Code Exchange.
- RFC 8252: OAuth 2.0 for Native Apps.
- RFC 8414: OAuth 2.0 Authorization Server Metadata.
- RFC 8725: JSON Web Token Best Current Practices.
- RFC 9068: JWT Profile for OAuth 2.0 Access Tokens.
- RFC 9449: OAuth 2.0 Demonstrating Proof of Possession, where sender
  constraint is selected for a client class.
- RFC 6265: HTTP State Management Mechanism for browser cookies.
- OpenID Connect Discovery and RP-Initiated Logout specifications.

RFCs define protocol and security requirements, not universal timeout values.
Squarewise must record its selected values as policy and validate them at
startup.

## Proposed policy

The initial policy is configuration-backed:

| Policy | Initial value | Rule |
|---|---:|---|
| Access-token lifetime | 5–10 minutes | Short-lived and audience-restricted |
| Refresh idle lifetime | 30 days | Refresh token expires when unused |
| Absolute session lifetime | 90 days | Sliding refresh cannot extend this boundary |
| Client refresh threshold | 2 minutes | Refresh before the access token expires |
| Clock skew | 30 seconds | Applied consistently at temporal boundaries |

User activity means successful authenticated session activity that results in a
refresh or a server-side browser-session activity update. Mouse movement, page
focus, and arbitrary client timers must not extend a server session on their own.

The effective refresh expiry is:

```text
min(last_successful_refresh + refresh_idle_lifetime,
    session_created_at + absolute_session_lifetime)
```

`absolute_session_expires_at` is immutable after session creation.

## Dependencies

- AUTH-01 through AUTH-07.
- ERR-03 structured authentication errors.
- DB-08 writer-only Accounts authentication state.
- QA-07 public-interface and negative-path evidence.
- OPS-24 CI and Compose authentication portability.

AUTH-09 through AUTH-16 remain related follow-up work where the existing tracker
assigns separate ownership for distributed limits, client security, protocol
parity, provider compatibility, evidence, and operations.

## Owned implementation paths

The runtime increment owns the following paths; the current branch has modified
the session/auth and evidence paths below. BFF browser cookie/CSRF behavior is
implemented here; native PKCE application integration remains explicitly
deferred under the accepted UI boundary:

```text
app/accounts/src/main/kotlin/**/accounts/auth/
app/accounts/src/main/resources/application.yml
app/accounts/src/main/resources/db/migration/
app/accounts/src/test/kotlin/**/accounts/auth/
app/bff/src/main/kotlin/**/bff/
app/bff/src/test/kotlin/**/bff/
contracts/rest/accounts.openapi.json
contracts/graphql/
tests/e2e/
tests/acceptance/
```

Documentation and evidence paths owned by this task are:

```text
docs/security/
docs/architecture/
docs/api/
docs/operations/
docs/tasks/details/AUTH-08.md
docs/tasks/progress.md
```

The coordinator owns `docs/tasks/registry.yaml` and `docs/tasks/board.md`.

## Work packages

### AUTH-08A: Policy and configuration

Create a pure session-expiry policy and validated configuration properties.
Reject contradictory or unsafe values at startup in production-like profiles.
Keep policy calculations independent of Spring, JPA, Redis, HTTP, and OIDC
providers.

### AUTH-08B: Session persistence and atomic rotation

Add immutable absolute expiry to durable sessions, preserve idle expiry as the
sliding boundary, and retain PostgreSQL as the writer-authoritative source.
Refresh must atomically validate, replace, and revoke the old token. It must
preserve the family and absolute expiry, update the last-use timestamp, and
issue a new token pair.

The operation may use targeted native SQL where it reduces round trips, but it
must not weaken compare-and-set behavior or replay detection. If the active
conditional transition fails, the service must fail closed and revoke the
affected family according to the selected replay policy.

### AUTH-08C: Trusted identity restoration

Refresh must resolve the provider-qualified subject and account state from
trusted Accounts-owned data. It must remove synthetic refresh identity values
such as `internal:refresh` and must never accept subject, email, or account ID
from the HTTP caller.

Deleted, suspended, unmapped, or identity-changed accounts must not receive a
new access token. Failure responses remain generic `401` responses.

### AUTH-08D: Logout and revocation

Implement effective idempotent logout. Logout must revoke the intended session
family, clear browser credentials where applicable, and coordinate with an
external provider revocation/logout adapter when the provider owns the grant.
Unknown tokens must not disclose whether a session existed.

The public contract must state whether logout revokes one device session or all
sessions. The default recommendation is current-family revocation, with account-
wide revocation reserved for deletion and security events.

### AUTH-08E: Existing endpoint protection

Build and maintain an endpoint authentication matrix for every REST operation,
GraphQL operation, and WebSocket handshake. Public routes must be explicit.
All other routes must require authentication and independently enforce resource
authorization.

The matrix must cover missing, malformed, expired, forged, wrong-issuer,
wrong-audience, blank-subject, non-member, removed-member, cross-group,
rate-limit, dependency-failure, and timeout behavior.

### AUTH-08F: Cache and database efficiency

Use existing cache facilities for OIDC discovery/JWK material, distributed rate
limits, bounded safe projections, and request-scoped BFF context. Do not use a
stale cache as the authority for session revocation, refresh acceptance, replay
detection, identity changes, or financial authorization.

Ordinary JWT requests must not perform an Accounts or `auth_sessions` lookup.
Refresh and logout must use the writer and the smallest secure number of round
trips. Cache mutation/invalidation behavior must be tested during restart,
eviction, stale-entry, and Redis-unavailable scenarios.

### AUTH-08G: Browser and native clients

Browser sessions should use a BFF-owned secure HttpOnly cookie, CSRF protection,
strict CORS/origin policy, and no JavaScript-visible refresh token. Native
clients must use Authorization Code + PKCE through the system browser and keep
refresh tokens in platform secure storage.

The BFF browser contract is implemented with HttpOnly access/refresh cookies,
double-submit CSRF, exact Origin checks, and browser-safe response metadata.
Native clients retain the bearer-token contract and use the provider's PKCE
configuration; application secure-storage integration remains deferred.

### AUTH-08H: GraphQL and WebSocket parity

GraphQL HTTP and WebSocket boundaries must use the same token validation,
identity, expiry, revocation, and authorization semantics as REST. A socket
handshake validates the token once; reconnect requires a fresh valid token.
Tokens must not be placed in query parameters or logs.

## Security invariants

- No non-local environment accepts arbitrary bearer values.
- Missing or blank identity fails before membership or financial stores.
- Access tokens are short-lived and audience-restricted.
- Refresh tokens are opaque, confidential, and stored only as digests.
- Every successful refresh rotates the refresh token.
- Reuse of a replaced refresh token revokes its family.
- Idle expiry is enforced.
- Absolute expiry is enforced and immutable.
- Logout actually revokes the intended session.
- Account deletion and suspicious activity revoke sessions.
- Provider-qualified subject, not email, is the authorization identity.
- Cache failure cannot make an uncertain revocation decision succeed.
- Ordinary bearer requests do not query Accounts for session validation.
- Domain services authorize resources independently of the BFF.
- Credentials never appear in URLs, logs, traces, metrics, referrers, or errors.
- Browser state-changing requests have CSRF protection.
- Native public clients use PKCE and external user agents.

## Required tests

### Unit tests

- Initial idle and absolute expiry calculation.
- Exact expiry boundaries and clock skew.
- Idle expiry extension after refresh.
- Immutable absolute expiry.
- Invalid policy configuration.
- Client-kind validation.
- Trusted identity resolution.

### Persistence tests

- Flyway migration and Hibernate validation.
- Absolute-expiry constraints.
- Atomic refresh rotation.
- Concurrent refresh single-winner behavior.
- Reuse-family revocation.
- Logout revocation.
- Account deletion revocation.
- Provider-subject change revocation.
- Writer-only routing.
- Cache invalidation after mutation.

The live cache-resilience matrix is implemented by
`tests/e2e/test_auth_cache_resilience.py` and `make e2e-auth-cache`. It proves
rate-limit bucket eviction, Redis outage fail-closed behavior, bounded client
waits, and restart recovery against the dedicated Compose Redis instance.

### Controller and boundary tests

- Every protected REST route rejects anonymous access.
- Invalid and expired tokens return the documented `401` shape.
- Resource authorization rejects non-members and removed members.
- Refresh never trusts caller-supplied identity.
- Logout revokes and remains idempotent.
- GraphQL HTTP and WebSocket negative paths match REST semantics.
- No credentials are emitted by logs, metrics, traces, or errors.

### Bruno and live E2E tests

- Passwordless login and verification.
- Refresh rotation.
- Refresh replay.
- Idle expiry.
- Absolute expiry.
- Logout followed by refresh.
- Account deletion followed by refresh.
- Provider outage.
- Redis outage.
- PostgreSQL writer outage.
- Accounts restart.
- REST, GraphQL, and WebSocket signed-token journeys.
- Full endpoint authentication matrix.

## Documentation deliverables

The implementation increment must update:

```text
docs/security/authentication-hardening.md
docs/security/authentication-audit.md
docs/security/authentication-readiness-review.md
docs/security/passwordless-api-contract.md
docs/security/endpoint-authentication-matrix.md
docs/security/cache-and-session-consistency.md
docs/security/authentication-threat-model.md
docs/security/browser-session-security.md
docs/security/native-client-security.md
docs/architecture/overview.md
docs/architecture/project-structure.md
docs/api/implementation-status.md
docs/operations/quickstart.md
docs/operations/ci.md
docs/operations/authentication-runbook.md
docs/operations/session-revocation-runbook.md
docs/operations/provider-outage-runbook.md
docs/tasks/progress.md
```

No document may claim a behavior is implemented without direct code and test
evidence. Local Compose evidence must remain separate from hosted CI,
production OIDC, HA/DR, restore, capacity, rotation, and incident-response
evidence.

## Planned validation commands

```text
./gradlew.bat :app:accounts:test --rerun-tasks --no-daemon
./gradlew.bat :app:bff:test --rerun-tasks --no-daemon
./gradlew.bat test check jacocoTestReport bootJar --parallel --no-daemon
uv run python tools/contracts/validate.py
uv run python tools/contracts/validate_public_surface.py
make security-hygiene
make workflow-validate
git diff --check
```

Live commands and their exact evidence will be added when runtime work begins.

## Initial documentation gate

The initial organization gate was satisfied by the documentation commit. The
parent task remains `in_progress` until the runtime and live-evidence criteria
above are complete.

The initial gate was satisfied when:

1. AUTH-08 is registered with owner, dependencies, paths, criteria, and
   validation commands.
2. The board links AUTH-08 to the security milestone.
3. The endpoint authentication matrix exists.
4. Cache and database-authority rules are documented.
5. RFC references and policy decisions are recorded.
6. The implementation task lists all required tests and evidence.
7. No runtime source, migration, contract, or test implementation is changed.
8. Contract validation and `git diff --check` pass.
