# Authentication implementation audit

**Audit date:** 2026-09-20

## Executive assessment

The current implementation has a good foundation but is not yet a complete
production authentication system. Spring Security resource-server adapters are
present in all four applications, and domain authorization still performs
independent membership checks. Keycloak is usable as a local OIDC issuer and
can be replaced at the issuer/discovery configuration boundary. However, the
repository does not yet prove a complete signed-token journey through the
applications, and it does not yet implement Squarewise-owned passwordless
login, short-lived access/session issuance, rotating refresh tokens, logout,
revocation, or reuse detection.

The target boundary is:

```text
Squarewise login/session boundary
  -> provider-neutral OIDC adapter/port
     -> local Keycloak now
     -> Auth0, Okta, Entra, or another OIDC provider later
```

Keycloak remains infrastructure configuration. Domain and authorization code
must not depend on Keycloak SDKs, realm roles, or vendor claim names.

## Evidence-backed findings

### Strengths

- Expense operations reject missing and blank subjects before membership or
  financial stores are queried.
- Group and expense authorization is performed by Expense Core; possession of
  a valid token does not imply group membership.
- Accounts, Expense Core, Notifications, and the BFF have explicit servlet or
  reactive resource-server chains under `local-oidc`, `staging`, and
  `production` profiles.
- Shared `libs/security` builds issuer-discovered decoders and validates the
  issuer, standard time claims, and configured audience.
- Compose provides a pinned local Keycloak image, realm import, readiness
  checks, Mailpit, and stable service hostnames.
- Issuer, audience, image, hostnames, ports, credentials, and internal service
  URLs are configuration-backed rather than embedded in application logic.

### Gaps and risks

1. **JWT policy is incomplete.** The decoder does not yet explicitly enforce a
   non-empty bounded `sub`, an approved algorithm set, token type, or a
   documented clock-skew policy. The required negative matrix is not
   executable evidence yet.
2. **There is no Squarewise-owned login boundary.** No `login`, `identity`,
   `session`, or `provider` application feature currently owns login start,
   callback, code verification, or session issuance.
3. **No passwordless flow exists.** Magic links and one-time codes are only
   documented. There is no hashed single-use credential store, expiry, attempt
   counter, resend cooldown, generic response, or concurrency protection.
4. **No application token lifecycle exists.** There are no short-lived
   Squarewise access credentials, rotating hashed refresh-token families, reuse
   detection, session listing, logout revocation, or security-event
   invalidation.
5. **The local Keycloak realm is a fixture, not the target UX.** It enables a
   public client and direct password grants for a seeded user. That is useful
   only for deterministic local integration testing and must not become the
   production login design.
6. **Real-provider evidence is missing.** Keycloak discovery/startup has been
   observed, but all four applications accepting a real signed token and
   rejecting invalid/expired tokens has not been demonstrated. E2E journeys
   still contain legacy arbitrary identities for non-member fixtures.
7. **Browser security policy is implemented at the BFF boundary.** Browser
   sessions use exact origins, credentialed auth-route CORS, Secure HttpOnly
   cookies, double-submit CSRF, and browser-safe responses. Native PKCE
   application integration and provider-specific redirect/state/nonce wiring
   remain deployment/client work.
8. **Security chain shape is duplicated.** The shared library should expose
   reusable policy builders while application adapters remain thin.

## Recommended target design

### RFC 9700 OAuth security baseline

The planned OAuth flow follows [RFC 9700](https://www.rfc-editor.org/rfc/rfc9700):

- authorization-code flow only; implicit and resource-owner-password grants are
  prohibited;
- PKCE with `S256` for every public client and also for confidential clients;
- transaction-specific state and OIDC nonce, exact redirect-URI matching, no
  open redirects, and issuer mix-up protection;
- short-lived, audience-restricted access tokens with least-privilege scopes;
- rotating refresh tokens with family revocation on reuse, or sender
  constraint where supported;
- TLS for all non-loopback authorization traffic and no credentials in URLs,
  logs, referrers, or browser history;
- authorization-server metadata/discovery used as the provider capability
  source, while issuer allowlists remain deployment-controlled.

The local Keycloak password-grant fixture must therefore remain test-only and
must not be used by the Squarewise product flow.

### Environment parity invariant

Local, staging, and production use the same Squarewise-owned login/session
contracts, the same provider-neutral JWT policy, and the same authorization
checks. Only the configured OIDC issuer, client registration, email delivery,
and operational secrets vary. Local Keycloak is an infrastructure substitute,
not a reduced-security profile. No environment may enable a passthrough
identity, implicit grant, resource-owner-password grant, wildcard redirect,
or weaker token validation for convenience.

- `auth.login` owns start, callback, magic-link request, code verification,
  resend, logout, and session operations.
- `auth.identity` maps `(issuer, subject)` to a local account. Email is mutable
  contact data and never the authorization key.
- Authentication normalizes email input at one boundary before lookup,
  throttling, or credential creation; canonical email is not used as the
  durable authorization identity.
- `auth.provider` exposes provider-neutral ports; Keycloak/Auth0/etc. are
  infrastructure adapters.
- Browser clients receive secure HttpOnly SameSite-controlled sessions. Native
  clients receive 5–10 minute access tokens and rotating refresh families.
- The default UX is a Squarewise-branded email magic link with a one-time code
  fallback. Provider-hosted interaction is reserved for step-up security.
- Store only hashes of magic-link codes and refresh tokens. Redemption must be
  atomic, single-use, expiry-bound, attempt-limited, and resend-throttled.
- Refresh-token reuse revokes the complete family and emits an audit event;
  logout, deletion, and suspicious activity revoke sessions.
- Credentials must never appear in logs, traces, metrics, error responses,
  referrers, or analytics payloads.

Authentication answers “who is this?” only. Every group, expense, settlement,
invite, schedule, sync, and notification operation must still resolve the
validated provider-qualified subject and enforce its own resource policy.

## Prioritized implementation tracker

| Phase | Task | Outcome | Required evidence | Status |
|---|---|---|---|---|
| P0 | AUTH-04 | Complete JWT policy and servlet/reactive parity | signed-token unit matrix and invalid-token probes | done |
| P0 | AUTH-05 | Remove weaker local mode | no raw-token runtime path; local OIDC profile tests | done |
| P0 | AUTH-06 | Prove local Keycloak integration | real REST/GraphQL/WS token journeys | done |
| P0 | AUTH-07 | Define Squarewise-owned auth contracts | API/session contract and threat model | done |
| P0 | AUTH-08 | Implement identity and session issuance | persistence, provider-boundary, session lifecycle, cache-failure, and endpoint evidence | done for local implementation |
| P0 | AUTH-09 | Implement passwordless links/codes | replay, brute force, enumeration, Mailpit tests | in progress; shared Redis/HMAC admission and local distributed/outage/telemetry evidence pass, hosted and production evidence open |
| P0 | AUTH-10 | Implement rotating refresh lifecycle | hashing, rotation, family revocation, reuse detection | planned |
| P0 | AUTH-11 | Implement logout and session revocation | races, deletion, provider-subject changes | implemented in AUTH-08 increment |
| P0 | AUTH-12 | Define browser/native security policy | CSRF/CORS/PKCE/state/nonce/cookie tests | implemented in AUTH-08 increment |
| P0 | AUTH-13 | Secure GraphQL HTTP/WebSocket parity | handshake, reconnect, expiry, revocation tests | planned |
| P1 | AUTH-14 | Prove managed-provider compatibility | second OIDC provider contract run | planned |
| P1 | AUTH-15 | Complete security evidence | unit, integration, Bruno, E2E, hygiene, load | planned |
| P1 | AUTH-16 | Document operations and incident response | rotation, outage, revoke, recovery runbooks | planned |

## Exit criteria

Keep the objective open until a real local Keycloak token and a
configuration-swapped managed OIDC token follow the same application path;
invalid tokens fail before domain stores; resource authorization is enforced
independently; passwordless credentials are single-use, expiring, rate-limited,
hashed, and non-enumerating; access and refresh lifecycles are short-lived,
rotating, and revocable; and REST, GraphQL, and WebSocket flows are covered.
