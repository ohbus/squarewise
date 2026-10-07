# v1 error compatibility window

## Status

The compatibility window remains open. This document is the controlled
migration notice and readiness checklist; it is not a declaration that
`numericCode` or `errorName` are required yet.

## Local promotion rehearsal

The repository now contains the code and contract changes for the required-field
promotion: the canonical schema and REST OpenAPI documents require both fields,
and `ProblemDetailsDto` enforces non-null values. This is locally verified
readiness evidence only. The externally effective contract remains pending until
the closure gates below are approved; no staging or production rollout is
implied by local validation.

## Current contract

API v1 continues to require the legacy symbolic `code` field. Servers may emit
the additive `numericCode` and `errorName` fields, and current services emit
them for governed errors. Clients should ignore unknown JSON properties and
must continue to deserialize the legacy fields.

## Closure gates

The window may close only after all of the following are attached to the task
ledger and approved by the coordinator and product owner:

1. ERRC-28 records 100% staged rollout for Accounts, Expense Core,
   Notifications, and the BFF, with canary metrics and rollback evidence.
2. Client adoption certification identifies every supported first-party and
   third-party consumer and confirms additive-field tolerance.
3. ERRC-26 records production-like performance, heap, and GC evidence.
4. The approval records the effective date, support contact, and rollback
   window for the required-field contract.

## Planned closure changes

The local promotion rehearsal has added `numericCode` and `errorName` to
`required` in the schema and all REST OpenAPI `ProblemDetails` definitions,
while retaining the v1 symbolic `code`. The validator and typed response model
reject omission in current code; external effective-date approval remains
pending.

## Integrator guidance during the open window

Integrators should preserve `code`, tolerate additive properties, log the
request ID, and avoid parsing human-readable `detail` as a stable identity.
Do not assume that the compatibility window has closed based on local tests or
the presence of fields in one service response.
