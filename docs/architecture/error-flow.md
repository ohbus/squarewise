# Consistent error flow

Status: current implemented v1 behavior. The accepted documentation-only successor
design is the [six-digit standard](error-code-standard.md),
[exception/boundary guide](error-handling-guide.md), and
[migration plan](error-code-refactoring.md). Those documents do not describe shipped
runtime behavior.

Every REST request passes through the service's request-ID filter and boundary
validation. The filter accepts a bounded alphanumeric `X-Request-Id` or creates
one, returns it on every response, and makes it available to logs and tracing.

All REST services import `libs/errors`. Its advice maps framework and domain
failures to `application/problem+json` with this shape:

```json
{
  "type": "https://squarewise.example/problems",
  "title": "Request validation failed",
  "status": 400,
  "code": "VALIDATION_FAILED",
  "requestId": "req_123",
  "detail": "One or more fields are invalid",
  "timestamp": "2026-09-17T19:00:00Z",
  "violations": [{"field": "totalMinor", "message": "must match ..."}]
}
```

Clients branch on `code`, never free-text `detail`. The stable vocabulary is
`VALIDATION_FAILED`, `UNAUTHENTICATED`, `FORBIDDEN`, `NOT_FOUND`, `CONFLICT`,
`IDEMPOTENCY_CONFLICT`, `RATE_LIMITED`, and `INTERNAL_ERROR`. Unexpected errors
retain details only in correlated server logs. GraphQL maps the same code into
`errors[].extensions.code` and preserves the request ID.

The current local implementation emits `numericCode` and `errorName` with every
governed Problem Details response, while `code` remains the symbolic v1 value.
The production-effective date and client-adoption approval for this promotion remain
external ERRC-29 evidence; local emission is not production rollout evidence.

Rate-limit denials and fail-closed limiter-store decisions use HTTP 429 with the
catalogued `RATE_LIMITED` code. The REST boundary includes a bounded
`Retry-After` value of 60 seconds for this code; limiter failures must not be
translated into an application 5xx or an unstructured gateway error. The BFF
uses the same catalog in GraphQL `errors[].extensions.code`: resolver/upstream
429 responses and query depth/complexity rejections use `RATE_LIMITED`, while
other upstream 4xx responses retain their corresponding 4xx code.

Request validation is mandatory on every command/query DTO. Domain invariants
remain mandatory after transport validation; a valid JSON shape can still be an
invalid expense or unauthorized operation.

The complete GraphQL extension and WebSocket lifecycle contract is defined in
[`contracts/graphql/errors.graphqls`](../../contracts/graphql/errors.graphqls)
and [`contracts/graphql/README.md`](../../contracts/graphql/README.md). Valid
upstream Problem Details preserve `code`, required `numericCode` and
`errorName`, `requestId`, `source`, `timestamp`, and bounded violations. An
unparseable upstream response receives a BFF-owned protocol error rather than a
status-derived or fabricated upstream identity. WebSocket lifecycle failures
use close codes 4401, 4403, 4408, and 4429.
