# AUTH-05: Remove weaker local authentication modes

## Objective

Ensure local application execution uses the same signed OIDC JWT validation
path as staging and production. There is no local passthrough identity mode.
Missing issuer, audience, discovery, or signing keys must prevent protected
application startup or reject requests; local convenience must not weaken
security.

## Decision

`local-oidc` is the only supported application security profile for local
containers and native runs. Keycloak is the local issuer adapter, while
Squarewise login/session and authorization behavior remain the same as every
other environment. The former `local` opaque-token passthrough is removed,
not merely hidden behind a weaker opt-in profile.

## Acceptance criteria

- No application runtime class accepts a raw bearer string as a subject.
- Local Compose and documented native runs select `local-oidc`.
- Missing local OIDC settings fail closed.
- Local, staging, and production use the same decoder, claim policy, audience,
  issuer, algorithm, expiry, and subject rules.
- Focused compilation/security tests, Compose validation, and documentation
  checks pass.

## Status

Complete. Runtime passthrough classes have been removed, local live
acceptance/load entry points require signed bearer injection, and the local
OIDC, negative-token, and passwordless journeys use the provider-neutral
validation path.
