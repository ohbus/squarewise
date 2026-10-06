# Authentication readiness review

**Review date:** 2026-09-20  
**Scope:** OIDC resource-server validation, local Keycloak integration,
Squarewise-owned passwordless login, session/token lifecycle, email
normalization, and resource authorization.

## Executive conclusion

The authentication slice is end-to-end complete for the AUTH-03 through AUTH-07
scope. The current implementation validates externally issued
OIDC access tokens in all four applications when the `local-oidc`, `staging`,
or `production` profile is active. Keycloak is currently a local OIDC provider
fixture and is not embedded in the domain or login policy. That is the correct
provider boundary for future Auth0, Okta, Entra, or another conforming OIDC
provider.

The application-owned passwordless flow includes public login-start and
verification controllers, refresh rotation, logout revocation, encrypted
Accounts-to-Notifications delivery, and live Mailpit evidence. The complete
slice is recorded as done in `docs/tasks/registry.yaml`; future browser-policy,
managed-provider, and incident-response work remains outside this slice.

## AUTH-08 hardening boundary

AUTH-08 is the registered parent task and is complete for implementation and
local security evidence. The current branch implements and verifies the session-policy, migration, trusted-identity,
logout, endpoint-matcher, and local negative-path slices. It does not claim
managed-provider or production-scale completion. Delivered scope is:

- RFC-aligned idle and absolute session expiry.
- Atomic refresh rotation with trusted identity restoration.
- Effective logout and family revocation.
- Existing REST, GraphQL, and WebSocket endpoint protection.
- Cache use for safe acceleration without bypassing writer-authoritative
  revocation or authorization.
- Browser/native client security policy.
- Full unit, persistence, controller, contract, Bruno, E2E, cache-failure, and
  endpoint-matrix evidence.

The latest hardening also binds refresh sessions to the provider-qualified
subject captured at issuance, revokes all active sessions when deletion begins,
and marks token-bearing verification/refresh responses `no-store`/`no-cache`.

Those implementation checks now pass. This does not expand the claim to
managed-provider compatibility or production-scale readiness; those remain
separate QA-08/OPS-22 release gates.

## Evidence-based status

| Capability | Current evidence | Assessment |
| --- | --- | --- |
| OIDC issuer/JWK discovery | `libs/security` decoder factories use issuer discovery and standard issuer/time validators | Implemented at library level |
| Audience and subject policy | Required audience and bounded `sub` validators with configurable asymmetric algorithm allow-list | Implemented; add claim-shape and key-rotation integration evidence |
| Local provider | Compose Keycloak 26.7.4, imported realm, PKCE S256, direct grants disabled, discovery observed HTTP 200 | Integrated as a local provider fixture |
| Provider replacement | Issuer/audience/algorithm values are externalized; domain code does not import Keycloak APIs | Good boundary; managed-provider compatibility still needs live-provider contract tests |
| Passwordless email | Canonical `EmailAddress`, HMAC-digested one-time credentials, expiry, single-use conditional redemption, attempt policy | Core building blocks implemented |
| Login orchestration | `LoginStartService` is wired to the public controller with rate limiting and generic responses | Verified with live endpoint and anti-enumeration tests |
| Email delivery | Encrypted Accounts outbox event and Notifications consumer deliver through Mailpit | Verified without plaintext credential logging |
| Access tokens | Resource services validate bearer JWTs and the internal provider mints signed access tokens | Verified through passwordless verification and protected journeys |
| Refresh tokens | Session service hashes, rotates, and family-revokes refresh tokens on reuse | Verified by controller, persistence, and live rotation/replay tests |
| Resource authorization | Group/expense operations accept authenticated principal and membership checks exist in Expense Core | Must be audited operation-by-operation; authorization must remain service-owned and never rely on BFF filtering |
| Contract/E2E | OpenAPI, Bruno, REST/GraphQL/WebSocket negative probes, and live Compose journeys | Verified and synchronized with the tracker |

## Keycloak decision

Keycloak should remain a local and test-environment OIDC provider, not a
Squarewise domain dependency. Squarewise should own the user experience and its
application authorization model. The provider adapter should only supply
identity proof, issuer metadata, signing keys, and, when required, provider-hosted
step-up/MFA/consent interaction.

The replacement seam is the OIDC configuration and validation port, not a
Keycloak-specific service API. A future provider change should require only
issuer/client/redirect/claim mapping configuration plus adapter tests. The
following must not appear in domain or authorization code: Keycloak admin APIs,
Keycloak token classes, realm-role assumptions, provider-specific group names,
or email-as-identity matching.

## Recommended user journeys

### Browser

1. The client posts a normalized email to the Squarewise-owned login-start
   endpoint.
2. Squarewise always returns the same accepted response and queues a single-use
   link. Unknown accounts are indistinguishable from known accounts.
3. The link opens a Squarewise-branded verification route. The credential is
   redeemed once, then removed from the URL before any third-party request or
   analytics event.
4. Squarewise creates a server-side session and sends a `Secure`, `HttpOnly`,
   `SameSite=Lax` cookie with bounded idle/absolute expiry. CSRF protection is
   required for cookie-authenticated state-changing requests.

### Native/client API

1. Login-start and code verification use the same application endpoints.
2. Verification returns a short-lived, audience-restricted access token and a
   rotating opaque refresh token.
3. Refresh rotates the token on every successful use. Reuse of an old token
   revokes the entire family and requires a new login.
4. Logout revokes the current session/family according to an explicit endpoint
   policy; account deletion and suspicious activity revoke all sessions.

Provider authorization-code + PKCE remains available for clients or step-up
flows that need provider-managed MFA/consent. It must use authorization code,
PKCE S256, exact redirect allow-lists, state and nonce bound to the transaction,
issuer mix-up protection, and no implicit or password grant.

## Verified implementation increments

- AUTH-03/AUTH-04: shared servlet/reactive issuer, audience, signature,
  temporal, algorithm, and subject validation with fail-closed configuration.
- AUTH-05/AUTH-06: local OIDC uses the same provider-neutral resource-server
  path as production; signed Keycloak journeys cover REST, GraphQL, and
  WebSocket boundaries.
- AUTH-07: public passwordless endpoints, atomic one-time redemption,
  encrypted outbox delivery, signed access-token issuance, refresh rotation,
  family-wide reuse revocation, and logout are implemented.
- Negative evidence covers malformed, forged, wrong-audience, wrong-issuer,
  unsupported-algorithm, expired, and provider-signed invalid-subject tokens.
  The isolated test OIDC issuer is test-only and does not alter production
  authentication configuration.

## Delivery boundary decision

The existing Expense Core outbox is service-private and must not be imported by
Accounts. Auth-email delivery will use an Accounts-owned transactional outbox
with a versioned `auth.email.requested.v1` event. Accounts will persist the
credential digest and delivery record in one transaction; the raw credential
must not be persisted in the outbox or generic notification inbox. The event
adapter requires an explicitly bounded protected handoff for the one-time
plaintext, with strict redaction and short retention. Notifications consumes
the versioned event and delegates SMTP/provider delivery to its existing
`EmailDispatcher` port; the live Mailpit journey verifies this boundary.

The payload contract is now recorded in
`contracts/events/auth-email-requested.v1.schema.json`. Its
`encryptedCredential` field is deliberately not a plaintext credential field;
the encryption envelope/key-management adapter is implemented and fails closed
when unavailable.

## Acceptance evidence for the completed AUTH-03 through AUTH-07 slice

The following evidence is recorded in the task registry and progress ledger:

- A complete local Keycloak journey obtains a real signed token and exercises
  Accounts, Expense Core, BFF GraphQL, and WebSocket behavior.
- Squarewise-owned email link and code journeys work without redirecting users to
  an unknown provider page for ordinary login.
- Access tokens are short-lived, refresh tokens are opaque, stored only as
  digests, rotated on every use, and family-revoked on replay.
- Every protected resource denies authenticated non-members and never trusts
  client-supplied user IDs, email addresses, or BFF-only checks.
- Local, staging, and production use the same flow and fail closed when issuer,
  audience, signing policy, digest secret, or token-signing configuration is
  absent.
- REST/OpenAPI, GraphQL, Bruno, unit, persistence, concurrency, and E2E
  artifacts are synchronized with the tracker and progress ledger.

BFF browser-cookie/CSRF policy is implemented in the AUTH-08 increment. Managed-
provider runs, full cache restart/eviction evidence, native application
integration, and incident-response procedures remain separately scoped gates.
