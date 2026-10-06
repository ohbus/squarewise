# OPS-17: Production error taxonomy and source attribution

Define and implement a stable, client-safe error vocabulary. Every REST problem
must identify the originating service and preserve the request correlation ID;
unexpected causes must be logged with stack traces without leaking them in the
response.

Acceptance: the problem schema, handlers, REST tests, and API documentation agree;
errors from each service expose `source`, and contract validation passes.

Validation: `./gradlew :libs:errors:test --rerun-tasks --no-daemon`,
`python3 tools/contracts/validate.py`, and `git diff --check`.
