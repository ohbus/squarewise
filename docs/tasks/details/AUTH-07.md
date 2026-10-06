# AUTH-07: Squarewise-owned passwordless authentication contract

## Objective

Define the provider-neutral application flow for low-friction email login.
Squarewise owns the user-facing endpoints and session policy; the configured
OIDC provider remains responsible for identity proof and token signing. Local
Keycloak, Auth0, Okta, and Entra must fit the same port without domain changes.

## Proposed flow

1. `POST /auth/v1/login/start` accepts an email and optional client context.
2. The service always returns the same generic response, regardless of account
   existence, and queues a single-use email link/code when allowed.
3. The browser opens a Squarewise-owned verification route. The credential is
   never logged and is stored only as a hash.
4. `POST /auth/v1/login/verify` atomically redeems the credential and creates a
   Squarewise session or completes the configured provider adapter flow.
5. Browser clients receive a secure HttpOnly session cookie; native clients
   receive a short-lived access token and rotating refresh token.
6. `POST /auth/v1/token/refresh`, `POST /auth/v1/logout`, and session-management
   endpoints enforce rotation, revocation, and reuse detection.

Provider-hosted pages are reserved for MFA, recovery, consent, or step-up
security. Any authorization-code integration uses PKCE `S256`, exact redirect
matching, transaction-bound state/nonce, issuer mix-up protection, and no
implicit or password grants, following RFC 9700.

## Security invariants

- Generic responses prevent email enumeration.
- Email input is normalized once at the authentication boundary: trim outer
  whitespace, apply Unicode-aware case folding, use a canonical international
  domain representation, and validate before lookup or credential issuance.
  The canonical form is used for matching and rate limits; the original
  display form is never used as an identity key.
- Credentials expire, are single-use, hashed, attempt-limited, resend-throttled,
  and atomically redeemed.
- Refresh tokens are hashed, rotated on every use, and revoke their family on
  reuse detection.
- Access tokens are short-lived and audience-restricted.
- Logout, account deletion, provider-subject changes, and suspicious activity
  revoke sessions.
- No credential appears in URLs beyond the one-time link, logs, traces,
  metrics, referrers, or error payloads.
- Every downstream service continues independent resource authorization.

## Acceptance criteria

- Contract is provider-neutral and consistent in local, staging, and production.
- Magic-link and code requests cannot reveal account existence.
- Replay, expiry, brute-force, resend, concurrent redemption, delivery failure,
  refresh replay, logout, and revocation are executable tests.
- REST, GraphQL, and WebSocket clients use the same session/token semantics.
- Keycloak is only a local provider adapter and can be replaced by configuration.

## Status

The provider-neutral passwordless authentication slice is implemented through the
Accounts application boundary and has focused unit, persistence, controller,
broker, Mailpit, Bruno, and live provider evidence. The verified implementation
currently includes:
1. `IdentityProviderPort` SPI isolates token issuance; `InternalJwtTokenProvider`
   issues RFC 7519 HMAC-SHA256 JWT access tokens while allowing external OIDC
   delegation via configuration.
2. `TokenSessionService` manages refresh tokens, rotating them within families
   and revoking the entire family immediately upon reuse detection.
3. `LoginVerificationService` atomically consumes single-use credentials and provisions
   the canonical subject profile.
4. `AuthController` exposes `POST /auth/login/start`, `POST /auth/login/verify`,
   `POST /auth/token/refresh`, and `POST /auth/logout` under `/accounts/v1`.
5. Public surface matrix and Bruno collections are validated and in parity.
6. Focused Accounts persistence and controller tests, plus the repository-wide
   JVM test gate recorded in `docs/tasks/progress.md`, pass for the implemented
   paths; live Compose, broker, Mailpit, and real OIDC-token verification are
   also recorded there.
delivery port, and login-start orchestration. Runtime login, refresh, logout,
concrete email delivery, and live end-to-end completion are verified in the
evidence-based readiness review and progress ledger:
[`docs/security/authentication-readiness-review.md`](../../security/authentication-readiness-review.md).

## Implementation increment: canonical email value

The first runtime slice adds `auth.identity.EmailAddress` in Accounts. It
trims outer Unicode whitespace, applies Unicode-aware root-locale case folding,
converts the domain to canonical ASCII using IDN rules, and validates bounded
local/domain lengths. The canonical value is suitable for account lookup,
rate-limit keys, and credential issuance. It is not used as the durable
authorization identity, which remains `(issuer, sub)`.

Tests cover normalization, Unicode domain conversion, malformed addresses,
empty components, invalid domain labels, and length limits. Provider adapters,
database migration, login endpoints, and passwordless credential persistence
are implemented in this completed AUTH-07 slice.

The provider-neutral endpoint contract is recorded in
`docs/security/passwordless-api-contract.md` before controller or persistence
implementation. It is intentionally application-owned so users remain on a
Squarewise-branded flow; provider-hosted interaction is reserved for MFA,
recovery, consent, or step-up requirements.

## Implementation increment: one-time credential core

The next slice introduces provider-neutral auth ports and domain policy for
one-time credentials. A cryptographically random raw credential is returned
only to the email-delivery adapter; persistence receives only a keyed digest,
expiry, attempt budget, and consumed state. Verification is constant-time and
must be completed by a later atomic persistence adapter. This slice does not
claim endpoint, database, rate-limit, or delivery completion.

The persistence increment adds a Flyway schema for login credentials and
refresh-token sessions. It stores canonical email, HMAC digests, expiry,
attempt/consumption state, session family identifiers, revocation timestamps,
and bounded device metadata only. It never stores raw codes, raw refresh
tokens, access tokens, or provider-specific credentials. Conditional updates
and unique identifiers are required for atomic redemption and refresh reuse
detection in the repository adapter.

The repository adapter must expose command-shaped operations rather than allow
callers to mutate security state freely: create credential, consume credential
once, create session, rotate refresh token, revoke family, and revoke session.
Consume/rotate operations must return a conflict or no-match outcome when a
row is expired, already consumed, revoked, or replaced. Concurrent callers
must not both succeed.

Spring integration tests now prove single-consumer redemption, expiry
rejection, one-time refresh rotation, and family revocation against the actual
Flyway/JPA persistence context rather than mocks.

The abuse-policy storage adapter uses an Accounts-owned PostgreSQL bucket and
conditional updates. A request increments only when its window is active and
cooldown has elapsed; an expired window resets atomically. The key is a
server-derived HMAC of canonical email plus a separately derived network
partition, never raw email or an untrusted client identifier. This state is
operational throttling data, not an authorization identity store.

Key derivation is isolated in `LoginRateLimitKeyDeriver`: it receives the
already-canonical email and a server-derived network partition, joins them with
a versioned delimiter, and HMACs the result with the deployment secret. The
repository sees only the resulting digest. Raw IP addresses, forwarded headers,
emails, and user-controlled rate-limit keys never reach persistence.

Bucket acquisition uses one PostgreSQL `INSERT ... ON CONFLICT DO UPDATE`
statement. It atomically handles first request, expired-window reset,
cooldown, and maximum-count checks; no read-then-write race is permitted.

Email delivery remains an outbound port owned by Accounts. Its message model
contains the canonical recipient, template identifier, and rendering data but
never raw credentials in logs or generic errors. A Notifications/RabbitMQ
adapter can deliver through the existing SMTP/Mailpit infrastructure; a future
managed provider adapter can replace it without changing login policy.

The delivery boundary review confirms that Expense Core's outbox is not a
shared library or cross-service database. AUTH-07B must add an Accounts-owned
transactional outbox and a versioned auth-email event; Notifications consumes
that event and delegates to its existing email dispatcher. Raw one-time
credentials must not be placed in a generic notification inbox or ordinary
outbox payload, so the handoff requires an explicitly bounded protected delivery
mechanism and redaction tests.

The versioned event schema is now recorded at
`contracts/events/auth-email-requested.v1.schema.json`. It permits only the
canonical recipient, link/code template, bounded encrypted credential, and
expiry. Encryption and key-management adapters must be implemented before the
event is published; a plaintext fallback is explicitly prohibited.

The AES-GCM key is required through
`SQUAREWISE_SECURITY_AUTH_EMAIL_ENVELOPE_KEY` in all production-like profiles
and local Compose. It has no default and must be exactly 32 decoded bytes;
missing or malformed configuration fails startup.

The Accounts-owned `auth_email_outbox` migration and persistence boundary now
store an idempotent event ID, canonical recipient, template, expiry, delivery
state, and only the protected envelope. The table is deliberately separate from
Expense Core's outbox. Publication and Notifications consumption are now
verified in the local Compose delivery path.
The outbox service now uses a pessimistic row lock and bounded lease timestamp
so one worker claims an available event at a time; expired claims become
eligible for retry.

Lifecycle transitions use flush-and-clear semantics after conditional native
state changes so a long-lived JPA persistence context cannot observe stale
`CLAIMED`, `PUBLISHED`, or `PARKED` state. Acknowledgement and rejection are
conditional on the current claim and therefore stale workers cannot overwrite
newer delivery state.

Accounts now has an explicitly disabled-by-default RabbitMQ publisher. When
enabled, it serializes only the versioned protected event envelope, acknowledges
the outbox after the broker send, and routes AMQP/serialization failures through
retry or parking. It does not import Expense Core messaging classes or publish
plaintext credentials.

The first protection implementation adds `CredentialEnvelopeProtector` and an
AES-GCM adapter. Each envelope uses a fresh 96-bit nonce, a 256-bit configured
key, an explicit format version, and authenticated recipient/template context.
Tampering, wrong-context decryption, malformed envelopes, and invalid key
sizes fail closed. The adapter is intentionally a port-level primitive and is
is wired to the Accounts outbox and Notifications consumer; the live Mailpit
journey verifies protected delivery without exposing the credential in logs.

`LoginStartService` is the application orchestration boundary. It derives the
trusted abuse key, acquires the atomic request slot, issues a link/code, and
hands the delivery-only plaintext to `AuthEmailSender`. Invalid email,
unknown account, throttled request, and delivery failure all map to the same
accepted public outcome; only internal metrics/audit state may distinguish
operational causes, without storing the credential.

The service slice now owns the orchestration boundary: canonicalize email,
issue and persist only a digest-backed credential, and verify through the
conditional repository transition. It returns generic outcomes so controllers
cannot accidentally disclose whether an email or credential exists. Email
delivery and HTTP mapping remain adapters.

The AUTH-07A contract increment adds canonical Accounts endpoint constants and
OpenAPI definitions for login start, credential verification, refresh, and
logout. The contract deliberately exposes only a generic `ACCEPTED` login-start
response and bounds credential/token input sizes. Runtime controller wiring,
atomic rate limiting, and the concrete Notifications delivery adapter are now
implemented and verified; no placeholder sender or insecure fallback is used.

Before the login-start controller is exposed, AUTH-07 adds an abuse-policy
port. It evaluates canonical email/network keys against a configured request
window, resend cooldown, and attempt budget, returning only a generic allow or
deny decision. The state store is an adapter boundary: local may use the
database, while production may use a shared atomic store. No controller may
implement ad-hoc counters.

Deployment wiring for the credential core is profile-gated and fail-closed:
`SQUAREWISE_SECURITY_CREDENTIAL_DIGEST_SECRET` is required in `local-oidc`,
`staging`, and `production`. It has no application default and is never
committed. The configured secret is used only by the HMAC digest adapter; a
rotation procedure and existing-credential migration policy must be completed
before production secret rotation.
