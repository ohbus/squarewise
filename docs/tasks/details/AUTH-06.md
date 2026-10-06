# AUTH-06: Keycloak environment and meaningful Compose hostnames

## Objective

Provide a real Keycloak OIDC environment for local, staging, and production-
like validation while keeping the application code provider-neutral. All
Compose services must have stable, meaningful hostnames and internal URLs must
use those names rather than localhost or ambiguous container identifiers.

## Requirements

- Keycloak is the current provider in every environment.
- Realm/client/bootstrap configuration is externalized and contains no
  production secrets.
- Mailpit is the local SMTP sink for passwordless-link/code tests.
- The local Keycloak image is pinned centrally in `infra/versions.env.example`
  and imports `infra/local/keycloak/realm/squarewise-realm.json`.
- Keycloak issuer, JWKS, and application audience are configured through
  environment variables.
- Compose hostnames use `idp-keycloak`, `accounts-api`, `expense-core-api`,
  `notifications-api`, `squarewise-bff`, `postgres-db`, `message-broker`, and
  `mailpit-email`.
- Health checks and startup dependencies use these stable names.
- Real signed tokens replace arbitrary local bearer strings in OIDC E2E tests.

The Compose/Keycloak integration is an infrastructure adapter inside the
authentication boundary. It must not leak realm/bootstrap details into domain
or application packages and must remain replaceable through the OIDC provider
port.

## Acceptance criteria

- Dependency and full-stack Compose configurations render successfully.
- Keycloak starts healthy and exposes discovery/JWKS through its meaningful
  hostname.
- A seeded local user can obtain a signed token for the Squarewise audience.
- All four applications accept valid tokens and reject invalid/expired tokens.
- Mailpit receives passwordless authentication messages without leaking codes
  into application logs.
- Auth0/another OIDC provider can later replace the issuer by configuration.

## Current evidence

- Official Keycloak container guidance was checked before pinning the image;
  realm import uses `/opt/keycloak/data/import` and `--import-realm`.
- Keycloak `26.7.4` started from the full local Compose topology.
- The `squarewise` realm imported successfully from the checked-in fixture.
- OIDC discovery returned HTTP 200 at
  `http://localhost:8090/realms/squarewise/.well-known/openid-configuration`.
- Application token acquisition and real-token REST/GraphQL/WebSocket journeys
  are verified under `local-oidc`; invalid-token rejection is covered by the
  Keycloak and isolated test-issuer matrices. The applications no longer use
  the local passthrough profile.
- Operations, README, Bruno environment, and E2E instructions are updated.
- Compose image names, service hostnames, credentials, ports, realm, issuer,
  audience, and inter-service URLs are environment-backed with meaningful
  defaults in `infra/versions.env.example`; no application-facing local URL is
  required to be edited in the Compose YAML.
- Added `tools/bruno/environments/local-oidc.bru`, which requires an injected
  signed token through `SQUAREWISE_BRUNO_TOKEN`.
- The REST edge harness now refuses to run authenticated checks without an
  injected `BEARER_TOKEN`, preventing placeholder bearer strings from being
  mistaken for identities under OIDC validation.
- `docker compose ... config --quiet`, `git diff --check`, and
  `:libs:security:test --rerun-tasks --no-daemon` passed. The latter executed
  all four security-library tests.

## Verification closure

- Compatibility-only seeded-user fixtures are isolated from live CI.
- Full-stack `local-oidc` WebSocket journeys, passwordless Mailpit delivery,
  malformed-bearer, wrong-issuer, expired-token, and provider-signed
  invalid-subject rejection are verified.

## Environment parity decision

Local, staging, and production must use the same authentication flow shape and
the same resource-server validation policy. Local Keycloak is only an issuer
replacement and test infrastructure; it is not a reason to enable a weaker
grant. The local client therefore uses authorization code plus PKCE (`S256`),
with direct access/password grants disabled. Any deterministic local test
token must be obtained through the same supported flow or a dedicated test
adapter that is never enabled in deployed application profiles.
