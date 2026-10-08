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
  "type": "https://squarewise.example/problems/request_validation_failed",
  "title": "Validation failed",
  "status": 422,
  "code": "VALIDATION_ERROR",
  "numericCode": "911101",
  "errorName": "REQUEST_VALIDATION_FAILED",
  "messageKey": "error.request.validation_failed",
  "requestId": "req_123",
  "detail": "One or more request parameters failed validation.",
  "timestamp": "2026-10-08T14:00:00Z",
  "violations": [{"field": "totalMinor", "message": "must be greater than 0", "messageKey": "validation.positive", "rejectedValue": -50}]
}
```

Clients branch on high-level category `code` (e.g. `VALIDATION_ERROR`, `BUSINESS_RULE_VIOLATION`, `NOT_FOUND`, `STATE_CONFLICT`, `RESOURCE_GONE`, `AUTHENTICATION_ERROR`, `AUTHORIZATION_ERROR`, `RATE_LIMIT_EXCEEDED`, `MALFORMED_REQUEST`, `INTERNAL_ERROR`), use `messageKey` for frontend internationalization (i18n), and use `errorName` / `numericCode` for exact domain identity, telemetry, and observability.

Generic HTTP 400 is strictly reserved for malformed request syntax / unparseable payloads (`REQUEST_BODY_MALFORMED`, `GRAPHQL_OPERATION_INVALID`). Semantic validations, Bean validation, and business rule violations use HTTP 422 (`Unprocessable Content`), while expired tokens and cursors use HTTP 410 (`Gone`).

Rate-limit denials and fail-closed limiter-store decisions use HTTP 429 with the
catalogued `RATE_LIMIT_EXCEEDED` category. The REST boundary includes a bounded
`Retry-After` value of 60 seconds for this code; limiter failures must not be
translated into an application 5xx or an unstructured gateway error. The BFF
uses the same catalog in GraphQL `errors[].extensions.code`: resolver/upstream
429 responses and query depth/complexity rejections use `RATE_LIMIT_EXCEEDED`, while
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
