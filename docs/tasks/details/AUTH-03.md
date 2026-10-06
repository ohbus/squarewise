# AUTH-03: Fail-closed provider-neutral OIDC resource-server validation

## Objective

Replace the implicit production-security assumption with explicit, reusable
servlet and reactive resource-server configuration. Every deployed service must
validate signed Keycloak-issued OIDC JWTs using configured issuer discovery/JWKs
and audience constraints. The configuration must remain generic OIDC so a
managed provider can replace Keycloak later. Missing or invalid production
configuration must fail startup or reject all protected requests; it must never
silently fall back to local passthrough authentication.

## Dependencies and ownership

- Depends on `AUTH-01` and `ERR-03`.
- AUTH-04 owns the detailed claim/algorithm negative matrix; this task owns the
  configuration boundary and fail-closed provider selection.
- Owns shared security configuration, service security properties, focused
  security tests, provider-neutral operations documentation, and progress
  evidence.

## Design requirements

1. Use configuration properties for issuer URI, audience, allowed algorithms,
   and optional clock skew; do not embed provider or Keycloak assumptions.
2. Use Spring Security JWT resource-server validation in Accounts, Expense
   Core, Notifications, and the reactive BFF.
3. Keep `local-demo` passthrough authentication opt-in and isolated by profile.
4. `local-oidc` and production must use the real JWT validation path.
5. Validate the token-derived subject before passing identity to domain code.
6. Preserve independent downstream authorization; authentication must not grant
   group or expense privileges.
7. Do not log bearer tokens, claims containing secrets, or raw authorization
   failures.

## Required test matrix

- production context with missing issuer fails closed;
- configured issuer creates the expected JWT decoder/resource-server chain;
- local-demo is unavailable under production profiles;
- local-oidc uses JWT validation rather than passthrough introspection;
- servlet and reactive services reject missing/malformed bearer tokens;
- authenticated identity is derived from validated `sub`, never email input;
- BFF forwarding preserves the bearer token only across the internal request
  boundary and does not expose it in GraphQL errors or logs.

AUTH-04 must add signed-token tests for wrong issuer, wrong audience, expired,
not-before, unsupported algorithm, bad signature, missing subject, malformed
subject, and key rotation.

## Required acceptance and contract evidence

- update API/security documentation and local operations instructions;
- add Bruno unauthenticated and invalid-token assertions for each service;
- add live E2E runs using real local OIDC tokens once AUTH-06 provisions the
  local provider;
- record exact focused, contract, static, Bruno, and E2E commands in progress;
- record production-like evidence separately from local Docker evidence.

## Acceptance criteria

- No production or staging service accepts arbitrary bearer strings.
- No production or staging service starts with an accidental passthrough
  security profile.
- All four applications have provider-neutral JWT resource-server wiring.
- Issuer and audience are externally configurable and required where needed.
- Security configuration and tests are modular, reusable, and documented.
- No claim or token secret is emitted through logs, errors, metrics, or traces.

## Implementation notes

- Added the reusable `libs/security` OIDC configuration guard.
- Imported the guard into Accounts, Expense Core, Notifications, and the BFF.
- The guard activates only under explicit `production` or `staging` profiles
  and requires an HTTPS issuer and non-empty audience.
- All environments may use Keycloak as the current provider; the guard accepts
  only provider-neutral issuer/audience configuration and contains no Keycloak
  SDK or claim coupling.
- JWT decoder wiring and signature/issuer/audience/expiry/algorithm validation
  are supplied by the completed AUTH-04 shared policy and boundary evidence.
- Compose operations must use meaningful, stable hostnames for all auth and
  service endpoints (for example `idp-keycloak`, `accounts-api`,
  `expense-core-api`, `notifications-api`, and `squarewise-bff`). Issuer and
  internal service URLs must use those names rather than `localhost`, random
  container names, or ambiguous abbreviations. Hostname changes require
  synchronized Compose, environment, operations, and E2E documentation.

## Current verification

- `./gradlew.bat compileKotlin --no-daemon` passed for all applications and
  libraries.
- `./gradlew.bat :libs:security:test --rerun-tasks --no-daemon` passed all four
  guard tests, covering missing issuer, missing audience, insecure production
  issuer, and valid production-like configuration.
- `py -3 tools/contracts/validate.py` and `git diff --check` passed.
- Provider-backed REST, GraphQL HTTP, and WebSocket boundaries are verified
  through the local Keycloak fixture and the isolated malformed-subject issuer,
  including centralized BFF bearer forwarding and readiness-gated startup.
