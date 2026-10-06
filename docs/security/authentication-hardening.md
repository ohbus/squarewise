# Authentication hardening plan

## Current assessment

Squarewise has a sound service-level authorization boundary and uses Spring
Security resource-server APIs, but its concrete authentication is currently a
local acceptance harness. Under the `local` profile, any bearer token is
accepted literally as the user subject. This is intentionally non-production
behavior and does not prove OIDC integration.

Keycloak service, realm, issuer/JWK configuration, and reusable resource-server
adapters now exist for local OIDC and production/staging profiles. Discovery
startup and security-library tests are evidenced. There is still no
Squarewise-owned passwordless login flow, refresh-token lifecycle, or complete
real-token application E2E proof. See the detailed
[authentication audit](authentication-audit.md).

## Target architecture

The application owns the login UX and identity mapping; an OIDC provider owns
identity proof and token signing. The provider is selected by configuration:

```text
Client -> Squarewise auth endpoints -> configured OIDC provider
                                      |-- local Keycloak
                                      |-- Auth0
                                      |-- Okta / Entra / other OIDC
```

Core code must depend on a narrow provider-neutral port. No domain code may
inspect Keycloak realm roles or provider-specific claim names. The durable
identity key is a provider-qualified subject, never an email address.

## Login experience

The default path should be a Squarewise-branded email magic link. A short-lived,
single-use email code is the fallback for mobile or interrupted browser flows.
Both paths require generic responses, request throttling, resend cooldowns,
attempt limits, hashed credential storage, expiry, audit events, and no raw
credential logging. Provider-hosted screens are used only for MFA, recovery,
consent, or other step-up actions that genuinely require them.

## Credential lifecycle

Browser clients should preferably receive a secure, HttpOnly, Secure,
SameSite-controlled application session. Native/API clients should use
short-lived access tokens and rotating refresh tokens. Refresh-token families
must be hashed at rest, revoked on reuse detection, and invalidated by logout or
security events. A reasonable initial policy is 5–10 minute access tokens,
5–10 minute login credentials, and bounded 14–30 day refresh sessions.

## Local versus production

`local-demo` may retain the passthrough principal only as an explicit,
localhost-only developer mode with a startup warning. `local-oidc` must use real
Keycloak-issued JWTs and the same validation path as production. Production must
use generic issuer discovery/JWK validation and fail startup if required issuer,
audience, or client settings are missing.

## Implementation tracker

The current status for AUTH-01 through AUTH-08 is maintained in
`docs/tasks/registry.yaml`. AUTH-08 is complete for local implementation and
security evidence: session policy, migration,
trusted refresh identity, logout, exact public-route matching, and local REST,
GraphQL, WebSocket-negative, Redis-failure, and BFF browser cookie/CSRF evidence
are implemented. Native application integration, provider-owned revocation where
a provider owns the grant, and production-scale evidence remain separate
follow-up gates. Local cache eviction/restart evidence is implemented by the
AUTH-08 E2E matrix. Account
deletion now bulk-revokes active sessions, refresh sessions bind the original
provider-qualified subject, and token responses prohibit intermediary caching.

| ID | Deliverable | Priority | Dependency | Status |
|---|---|---:|---|---|
| AUTH-01 | Provider-neutral architecture and tracker | P0 | - | Done |
| AUTH-02 | Remove implicit `test-user` identity fallback | P0 | AUTH-01 | Done |
| AUTH-03 | Fail-closed production JWT resource server | P0 | AUTH-01 | Done |
| AUTH-04 | Issuer, audience, algorithm, expiry, and subject validation | P0 | AUTH-03 | Done |
| AUTH-05 | Explicit opt-in/localhost safeguards for local demo auth | P0 | AUTH-01 | Done |
| AUTH-06 | Optional local Keycloak realm and Mailpit bootstrap | P1 | AUTH-03 | Done |
| AUTH-07 | Squarewise-owned login start/callback contracts | P1 | AUTH-01 | Done |
| AUTH-08 | RFC-aligned session lifecycle, endpoint protection, cache consistency, and full local security evidence | P0 | AUTH-07, DB-08, ERR-03, QA-07 | Done |
| AUTH-09 | Rate limiting and email-enumeration protection | P0 | AUTH-08 | In progress; shared Redis/HMAC limiter and local distributed/outage evidence implemented, hosted and production evidence open |
| AUTH-10 | Identity mapping using provider-qualified subjects | P0 | AUTH-03 | Planned |
| AUTH-11 | Short-lived access credentials and rotating refresh tokens | P0 | AUTH-10 | Planned |
| AUTH-12 | Logout, revocation, reuse detection, and session management | P0 | AUTH-11 | Planned |
| AUTH-13 | Secure browser cookies and CSRF policy | P0 | AUTH-11 | Done (AUTH-08) |
| AUTH-14 | GraphQL HTTP/WebSocket authentication parity | P0 | AUTH-03 | Planned |
| AUTH-15 | Real-provider integration and security regression suites | P0 | AUTH-06, AUTH-12 | Planned |
| AUTH-16 | Operations, key rotation, incident response, and recovery runbooks | P1 | AUTH-12 | Planned |

## Exhaustive implementation requirements

### AUTH-02: Remove implicit identities

Replace every `principal ?: fallback` path with an explicit authenticated
subject requirement. Missing, blank, or malformed subjects must produce the
catalogued unauthenticated response and must not query membership stores using
a synthetic identity. Update controller tests for every affected operation,
REST-edge probes, Bruno unauthenticated requests, and a live E2E request for
each public service boundary.

### AUTH-03/AUTH-04: Production token validation

Create one provider-neutral configuration model and separate servlet/reactive
security adapters. Production must fail closed if issuer or audience settings
are absent. Validate discovery/JWK signature, issuer, audience, expiry,
not-before, subject, supported algorithm, and bearer-token type. Test valid,
malformed, unsigned, expired, not-yet-valid, wrong-issuer, wrong-audience,
wrong-algorithm, missing-subject, and key-rotation cases.

### AUTH-05/AUTH-06: Local provider parity

Keep passthrough auth only for explicit `local-demo`; bind it to localhost and
emit a startup warning. Add optional `local-oidc` Compose services for
Keycloak, realm/client bootstrap, Mailpit SMTP, health checks, and deterministic
test users. Replace live acceptance bearer fixtures with real OIDC token
acquisition. Keep a separate Bruno environment and never commit credentials.

### AUTH-07/AUTH-09: Passwordless login

Define Squarewise-owned login endpoints for start, callback, code verification,
resend, and logout. Responses must not reveal whether an email exists. Links
and codes are single-use, hashed at rest, expiry-bound, attempt-limited,
resend-throttled, and excluded from logs/traces. Test replay, expiry, brute
force, enumeration, duplicate requests, concurrent redemption, and delivery
failure. Email delivery must use the existing notification boundary and Mailpit
locally.

### AUTH-08/AUTH-10/AUTH-12: Identity and session lifecycle

Persist provider-qualified identities separately from mutable email/profile
data. Implement short-lived access credentials and rotating refresh-token
families, hashed refresh storage, reuse detection, revocation, logout, active
session listing, and bounded device metadata. Test concurrent refresh, replayed
refresh, logout races, account deletion, provider subject changes, and expired
sessions.

### AUTH-11/AUTH-13: Client security

Use secure HttpOnly SameSite cookies for browser sessions or document the
native-client token-storage contract. Define CSRF behavior, CORS allowlists,
redirect allowlists, state/nonce/PKCE requirements, and cache-control headers.
Test cross-origin requests, fixation, callback CSRF, open redirects, and token
leakage through URLs, logs, referrers, and error responses.

### AUTH-14: API parity

Apply the same identity and expiry semantics to REST, GraphQL HTTP, and
GraphQL WebSocket handshakes/reconnects. Downstream services must continue to
authorize independently. Test unauthorized subscriptions, expiry during a
socket session, reconnect with revoked credentials, cross-user fanout, and
header forwarding/redaction.

### AUTH-15/AUTH-16: Evidence and operations

Run the complete unit, integration, contract, Bruno, live E2E, security-hygiene,
dependency-scan, and production-like validation suites. Document issuer/JWK
rotation, client-secret rotation, email-provider failure, provider outage,
emergency revocation, restore, and incident response. Clearly separate local
Docker evidence from production evidence.

## Immediate risk controls

1. Remove every fallback subject, including `test-user`.
2. Do not expose passthrough authentication outside localhost.
3. Require explicit production OIDC issuer and audience configuration.
4. Test forged, expired, wrong-issuer, wrong-audience, and malformed tokens.
5. Replace acceptance tests that use arbitrary bearer strings with real local
   OIDC tokens once `local-oidc` exists.
