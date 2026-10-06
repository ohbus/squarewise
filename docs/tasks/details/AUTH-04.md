# AUTH-04: Validate Keycloak OIDC JWT claims and signatures

## Objective

Configure the four applications to authenticate only with valid signed JWTs
issued by the configured OIDC issuer. Keycloak is the current issuer, but the
implementation must use standard OIDC discovery/JWK and JWT claims so a future
managed provider can be substituted without domain changes.

## Dependencies and ownership

- Depends on `AUTH-03` and `ERR-03`.
- Owns shared decoder/validator code, servlet and reactive adapters, security
  unit tests, and invalid-token contract coverage.
- Keycloak Compose provisioning and real-token journeys are AUTH-06.

The implementation must visibly scream authentication capabilities through
feature packages, then layer each feature internally. It follows modular DDD
and Hexagonal Architecture: JWT validation is exposed through ports, and
Spring servlet/reactive decoders plus Keycloak discovery are infrastructure
adapters rather than domain dependencies.

## Validation rules

Every protected request must validate:

- signature against issuer-discovered JWKs;
- exact configured issuer;
- configured audience;
- `exp` and `nbf` with bounded clock skew;
- supported asymmetric signing algorithm;
- non-empty, bounded subject;
- bearer token parsing and token type.

Email, preferred username, and display claims must never become the durable
authorization identity. Authorization uses the validated provider-qualified
subject and independently checks group membership in the owning service.

## Required tests

- valid signed token is accepted;
- missing, malformed, unsigned, and bad-signature tokens are rejected;
- wrong issuer and wrong audience are rejected;
- expired and not-yet-valid tokens are rejected;
- unsupported algorithm and missing subject are rejected;
- subject length/format limits are enforced;
- decoder handles JWK key rotation without trusting arbitrary keys;
- servlet and reactive security chains behave equivalently;
- GraphQL HTTP and WebSocket authentication reject invalid/expired tokens;
- invalid credentials do not reach membership, expense, group, or notification
  stores and never appear in errors/logs/metrics.

## Acceptance criteria

- No non-local protected endpoint accepts an arbitrary bearer string.
- All four applications use the same provider-neutral validation policy.
- Keycloak-issued tokens authenticate the subject from `sub` only.
- Wrong issuer, audience, signature, expiry, algorithm, and subject cases have
  executable test evidence.
- Bruno and REST-edge invalid-token cases are added; real Keycloak E2E follows
  AUTH-06.
- Documentation and progress ledger contain exact commands and limitations.

## Implementation notes

- Added reusable servlet and reactive decoder factories in `libs/security`.
- Added production/staging JWT security chains to Accounts, Expense Core,
  Notifications, and the reactive BFF.
- Issuer URI is supplied by `SQUAREWISE_SECURITY_OIDC_ISSUER_URI`; audience is
  supplied by `SQUAREWISE_SECURITY_OIDC_AUDIENCE`.
- Keycloak discovery/JWK is used through standard Spring OIDC APIs; no
  Keycloak-specific SDK or claim is used.
- Real Keycloak issuer availability and signed-token REST/GraphQL HTTP journeys
  are verified in the local Compose fixture. Bruno now also verifies malformed
  bearer rejection at all four application boundaries. Wrong-audience,
  wrong-issuer, forged-signature, and unsupported-algorithm rejection are
  verified across all four HTTP boundaries. Forged-token WebSocket upgrade
  rejection is also verified. A separately signed token from the configured
  `squarewise` issuer is rejected after its one-second expiry and the configured
  clock-skew window at all four HTTP boundaries. A dedicated provider-signed
  whitespace-only-subject token is rejected across REST, GraphQL, WebSocket,
  and Bruno boundaries; this evidence is isolated from production Compose.

The shared security module also centralizes OIDC claim names, OAuth rejection
codes, algorithm policy messages, and deployment configuration messages in
`OidcSecurityConstants`; servlet, reactive, and startup-guard adapters consume
that single policy vocabulary.

Provider-neutral temporal regression tests now prove that the default issuer
validator rejects expired and not-yet-valid JWT claims and accepts a token
inside its validity window. Live provider-issued expiry is also covered by a
real provider-issued token; invalid-subject journeys use the isolated
`tests/fixtures/invalid_subject_oidc` issuer and are executed against REST,
GraphQL, WebSocket, Bruno, and CI boundaries. The fixture has its own ephemeral
RSA key and is never part of a production Compose profile.

## Delivered policy increments

The shared security library now exposes one provider-neutral claim policy and
reuses it from both servlet and reactive decoder factories. It enforces a
bounded, non-blank `sub` claim before identity reaches application code,
preserves issuer/audience/time validation, and rejects unsupported algorithms.
`SQUAREWISE_SECURITY_OIDC_ALLOWED_ALGORITHMS` defaults to `RS256`; applications
may explicitly select a compatible asymmetric algorithm set for their provider.
Symmetric algorithms and blank/absent algorithm headers are rejected. This is
a policy guard in addition to JWK signature verification, not a replacement
for it.

## Implementation notes: bounded subject policy

- Added `OidcJwtClaimPolicy` to `libs/security` as the shared claim-policy
  adapter used by both decoder factories.
- `sub` must be a non-empty, whitespace-free string no longer than 256
  characters. Email, username, and display claims remain ignored.
- Added tests for valid, missing, blank, whitespace-containing, and overlong
  subjects.
- Added provider-neutral tests for expired, not-yet-valid, and in-window tokens.
