# v1 error compatibility window

## Status

The compatibility window remains open. This document is the controlled
migration notice and readiness checklist; it is not a declaration that
`numericCode` or `errorName` are required yet.

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

After approval, the schema and all REST OpenAPI `ProblemDetails` definitions
will add `numericCode` and `errorName` to `required`, while retaining the v1
symbolic `code`. The validator and omission tests will then reject legacy-only
fixtures.

## Integrator guidance during the open window

Integrators should preserve `code`, tolerate additive properties, log the
request ID, and avoid parsing human-readable `detail` as a stable identity.
Do not assume that the compatibility window has closed based on local tests or
the presence of fields in one service response.
