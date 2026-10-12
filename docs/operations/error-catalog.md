# Error catalog governance

The authoritative error identity is the immutable six-digit `DM-L-C-EE` value
defined in [`docs/architecture/error-code-standard.md`](../architecture/error-code-standard.md).
The machine-readable sources are [`contracts/errors/domains.yaml`](../../contracts/errors/domains.yaml)
and [`contracts/errors/error-catalog.yaml`](../../contracts/errors/error-catalog.yaml).
The legacy v1 `code` remains additive-contract compatible, but new runtime code
must use the static catalog definition and governed exception path.

## Naming

Use the catalog's globally unique `SCREAMING_SNAKE_CASE` `errorName`. The
numeric identity is allocated from the semantic domain/module/layer/category
namespace; it is not derived from the name or transport status.

Do not create generic codes such as `BAD_REQUEST`, `DATABASE_ERROR`, or
`UNKNOWN_ERROR`. Reuse an existing identity unless remediation, disclosure,
retry/idempotency, transport, durable side effects, or ownership differs.

## Required decision fields

Every catalog entry records its owner, component, operation, status/classification,
safe text, severity, retryability, disclosure, transports, and lifecycle. New
codes require catalog validation, static catalog regeneration, implementation
tests, client/contract mapping review, and a dashboard/alert decision.

## Add a new code

1. Confirm the condition is semantically distinct and choose its owning domain/module.
2. Select layer and category from the frozen registries and allocate the next
   unused sequence; never fill a retired gap.
3. Add the complete record to the YAML catalog and regenerate the static catalogs:
   `uv run python tools/codegen/generate_error_catalogs.py`.
4. Use the owning `*Errors` definition through a governed exception or typed
   outcome. Do not construct arbitrary numeric/string codes at a throw site.
5. Update the owning context guide and any REST, GraphQL, event, client, test,
   dashboard, or runbook surface affected by the new behavior.
6. Run:
   `uv run python tools/errors/validate_catalog.py`,
   `./gradlew :libs:errors:test`,
   `uv run python tools/contracts/validate.py`, and affected service tests.

## Response and logging policy

REST problems contain the compatibility `code` plus `numericCode`, `errorName`,
`source`, `component`, `operation`, `requestId`,
`errorId`, status, timestamp, and safe detail. GraphQL copies these into
`errors[].extensions`. Logs contain the same identifiers and the sanitized cause.
Never return tokens, SQL, stack traces, raw amounts, or untrusted values.

Metrics use only bounded labels: service, component, operation, code, and
status. Request IDs and resource IDs are for logs/traces, not metric labels.

## Review checklist

- Is this a distinct client-remediation scenario?
- Is the error name semantically descriptive and globally unique?
- Is the numeric identity consistent with the authoritative registry?
- Is HTTP status and retryability explicit?
- Are field/resource details safe?
- Is the catalog updated before code is merged?
- Are REST, GraphQL, Bruno, and integration tests present?
- Are logs correlated without high-cardinality metrics?
