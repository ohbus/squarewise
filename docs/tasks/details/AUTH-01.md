# AUTH-01: Provider-neutral authentication hardening baseline

## Objective

Define and register the authentication architecture before implementing login,
token, or provider-specific integrations. The application must support local
OIDC validation with Keycloak while remaining configurable for Auth0, Okta,
Microsoft Entra ID, or another standards-compliant OIDC provider.

## Decision

Squarewise will not depend on Keycloak-specific APIs or claims. Services consume
validated OIDC identities through Spring Security resource-server support.
Keycloak is the current production and local identity provider, but remains an
infrastructure choice rather than an application/domain dependency. Provider-
specific issuer, audience, discovery, client, and email settings are deployment
configuration so a managed OIDC provider can replace it later.

The user-facing login surface remains Squarewise-owned. Passwordless email links
and one-time codes may be implemented behind an OIDC/provider adapter. Provider
hosted pages are reserved for consent, MFA, recovery, or other step-up flows.

Access credentials must use short-lived access tokens and rotating refresh
tokens, or an equivalent secure server-side session for browser clients.

## Architecture shape

Authentication uses Screaming Architecture: the top-level packages and modules
must make the business capabilities visible (`login`, `identity`, `session`,
and `provider`) rather than exposing framework or vendor names. Each feature
uses internal layering with modular DDD and Hexagonal Architecture: domain and
application policies depend on inbound/outbound ports, while Spring Security,
Keycloak, email, persistence, and HTTP/WebSocket implementations remain
replaceable adapters. Shared `libs/security` contains only technical OIDC/JWT
primitives and no login or business workflow.

## Initial findings

- The `local` profile accepts any bearer value as the subject and performs no
  signature, issuer, audience, or expiry validation.
- No Keycloak container, realm, issuer configuration, or real OIDC acceptance
  flow is currently present.
- Production OIDC resource-server wiring is documented but not yet evidenced
  by a production configuration or integration test.
- `ExpenseController` contains a `test-user` fallback when the principal is
  absent; this must be removed before production authentication work is
  considered complete.

## Delivery rules

No child task may change runtime authentication without first updating its
task detail, affected contracts, architecture/operations documentation, and
test plan. Every externally visible auth behavior must be covered at all
applicable layers:

- unit tests for token, identity, credential, and policy logic;
- controller/filter tests for HTTP and GraphQL boundaries;
- persistence tests for expiry, uniqueness, revocation, and concurrency;
- Bruno requests and assertions for REST/GraphQL contract behavior;
- live E2E journeys against the configured local OIDC provider;
- negative security tests for replay, enumeration, malformed credentials,
  wrong issuer/audience, expiry, revocation, and authorization loss.

The implementation is incomplete until the exact commands and evidence are
recorded in `docs/tasks/progress.md`. Local passthrough-token tests cannot be
reported as proof of real OIDC integration.

## Planned child increments

1. AUTH-02 removes implicit identities and makes missing authentication a hard
   401, with controller, REST-edge, Bruno, and E2E coverage.
2. AUTH-03 adds fail-closed production JWT resource-server configuration with issuer,
   audience, algorithm, expiry, and subject validation.
3. AUTH-04 validates issuer, audience, algorithm, expiry, subject, and token
   type, with forged-token and boundary tests.
4. AUTH-05 adds explicit opt-in and localhost-only safeguards for passthrough
   local auth.
5. AUTH-06 adds optional local Keycloak, realm bootstrap, and Mailpit integration,
   plus real-token E2E and Bruno environments.
6. AUTH-07 adds provider-neutral login-start, callback, email-link, and one-time-code
   contracts with anti-enumeration and rate limiting.
7. AUTH-08 implements identity mapping, short-lived access credentials, refresh rotation,
   reuse detection, logout, revocation, and session management.
8. AUTH-09 through AUTH-15 add HTTP, GraphQL, WebSocket, provider-compatibility,
   abuse-resistance, operational, and recovery tests.

## Required evidence per child task

Each child task must record:

1. exact files changed and why each is owned by the task;
2. contract changes and backward-compatibility impact;
3. threat model and abuse cases considered;
4. unit/controller/persistence test names and expected assertions;
5. Bruno request names, environment variables, and assertions;
6. E2E journey steps, fixtures, cleanup, and provider used;
7. validation commands with exact output summary;
8. known limitations, production-only evidence, and follow-up task links;
9. commit hash and clean-worktree review.

## Acceptance criteria

- No non-local environment accepts arbitrary bearer tokens.
- Production startup fails closed when OIDC configuration is absent or invalid.
- Local Keycloak issues real tokens consumed by all four applications.
- A generic OIDC provider can be selected through configuration without domain
  or authorization code changes.
- Passwordless login credentials are single-use, expiring, rate-limited, and
  never logged or stored in plaintext.
- Access and refresh credential lifecycle behavior is covered by executable
  tests, including rotation, reuse detection, logout, and revocation.
- Domain services continue to enforce membership authorization independently.

## Validation commands

- `python3 tools/contracts/validate.py`
- `python3 tools/contracts/validate_public_surface.py`
- `./gradlew test --rerun-tasks --no-daemon`
- `make security-hygiene`
- `make check`
- `git diff --check`

## Status

Implemented as the architecture baseline. Runtime delivery remains tracked by
AUTH-03 through AUTH-16; this task does not claim that the complete login,
passwordless, or session lifecycle exists.
