# ACC-01: Implement Accounts and OIDC

- Phase: future_implementation
- Owner role: accounts
- Dependencies: FND-02
- Owned paths: `app/accounts/`

## Outcome

Implement stable OIDC account linkage and profile/preferences lifecycle.

## Implementation requirements

Validate issuer/subject identity, input and access; expose DTOs not entities; implement export/deletion request workflow.

## Acceptance criteria

Account operations conform to contracts; deletion cannot silently remove group financial history; launch retention policy remains release-gated.

## Verification and handoff

Record exact commands, exit status, artifact paths, findings and unresolved blockers
in the task registry. No task is done until its deliverable is reviewed. Use the
approved contracts as the behavioral authority; update the specification and
register child tasks before expanding scope. Future implementation tasks are not
authorized to bypass the documentation gate or the current scaffold milestone.

## Current increment

The contract slice now exposes authenticated `GET /accounts/v1/me`, validated
`PATCH /accounts/v1/me`, and `POST /accounts/v1/me/deletion-request` with shared
problem responses and controller tests. The profile store is deliberately an
in-memory adapter while OIDC claim validation, JPA persistence, migration, and
deletion workflow retention rules are implemented in the next ACC-01 increment.

Added issuer and bounded-subject OIDC claim validation as a provider-neutral
security boundary. JWT decoder configuration, key rotation, account persistence,
and claim-to-profile lifecycle integration remain pending.

Added an idempotent deletion-request state model that records intent without
removing financial history. Retention policy execution, cancellation, export,
and durable persistence remain pending.

Added a stable export-request lifecycle model separate from deletion. Export
materialization, signed download URLs, expiry enforcement, and durable storage
remain pending.

Added a JPA-backed profile adapter and Flyway V1 migration behind the
`ProfileStore` port. Production Spring contexts use the durable adapter;
isolated controller tests use the in-memory adapter. OIDC decoder wiring,
retention execution, export materialization, and PostgreSQL integration tests
remain pending.

Account identity projections now use a database-side deterministic first-row
lookup rather than materializing every identity linked to an account. This
preserves the existing single-identity projection while bounding authentication
read memory when an account has multiple provider bindings.
