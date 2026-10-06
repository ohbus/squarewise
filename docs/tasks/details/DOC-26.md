# DOC-26: Reconcile local smoke-demo delivery evidence

## Objective

Record the committed local smoke-demo increment on `master` and reconcile its
implementation, operational documentation, Bruno collection, and verification
evidence with the repository tracker.

## Dependencies

- `OPS-09`
- `QA-04`

## Acceptance criteria

- Local bearer authentication supplies a `java.security.Principal` to REST and GraphQL handlers.
- Expense Core group summaries expose `kind`; BFF mapping normalizes omitted collections.
- Bruno requests cover Accounts, Expense Core, Notifications, and GraphQL BFF local operations.
- Accounts `/me` and BFF groups/member GraphQL smoke probes succeed.

## Validation evidence

- Commit: `e63ee35` on `master`.
- `make check` passed.
- `make acceptance-live` passed all QA-04 and QA-05 journeys, including
  rollback, concurrent rename serialization, authorization, fanout failure,
  and recovery.
- Focused Accounts, Expense Core, Notifications, and BFF tests passed.
- `python3 tools/contracts/validate.py` and `git diff --check` passed.
- Live Accounts `/me` and BFF GraphQL groups/member probes returned HTTP 200.

## Known limitation

The opaque-token introspector is restricted to the `local` Spring profile and
is not a production authentication mechanism. Live verification uses seeded
local data and the `test-user` token.
