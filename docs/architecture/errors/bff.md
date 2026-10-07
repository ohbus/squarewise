# BFF error ownership guide

Status: allocated and authoritative under `ERRC-04`; matches `contracts/errors/error-catalog.yaml`.

The database-free BFF owns Domains `41`-`43`. It preserves valid upstream identities
and creates a BFF identity only for a failure introduced by GraphQL, gateway transport,
aggregation, or live-update behavior.

## Allocated BFF-owned families

| Candidate | Name | REST/GraphQL classification | Legacy `code` | Required distinction |
|---|---|---|---|---|
| `411101` | `GRAPHQL_INPUT_INVALID` | `BAD_REQUEST` | `VALIDATION_FAILED` | Resolver/schema input validation |
| `411102` | `GRAPHQL_OPERATION_INVALID` | `BAD_REQUEST` | `VALIDATION_FAILED` | Unsupported/invalid operation |
| `412901` | `GRAPHQL_AGGREGATION_FAILED` | `INTERNAL_ERROR` | `INTERNAL_ERROR` | BFF orchestration defect after safe upstream handling |
| `426701` | `UPSTREAM_PROTOCOL_INVALID` | `BAD_GATEWAY` / 502 | `INTERNAL_ERROR` | Invalid status/content/problem document |
| `426702` | `UPSTREAM_RESPONSE_DECODE_FAILED` | `BAD_GATEWAY` / 502 | `INTERNAL_ERROR` | Contract decode failure |
| `426801` | `UPSTREAM_TIMEOUT` | `GATEWAY_TIMEOUT` / 504 | `INTERNAL_ERROR` | Connect/read/operation timeout when remediation matches |
| `426802` | `UPSTREAM_UNAVAILABLE` | `SERVICE_UNAVAILABLE` / 503 | `INTERNAL_ERROR` | DNS/refusal/circuit/dependency unavailable |
| `426803` | `UPSTREAM_TLS_FAILED` | `BAD_GATEWAY` / 502 | `INTERNAL_ERROR` | TLS/identity negotiation failure |
| `437801` | `SUBSCRIPTION_LIMIT_EXCEEDED` | `RATE_LIMITED` / 429 | `RATE_LIMITED` | Per-connection/account bounded quota |
| `437802` | `LIVE_UPDATE_UPSTREAM_UNAVAILABLE` | `SERVICE_UNAVAILABLE` | `INTERNAL_ERROR` | Fanout/subscription dependency outage |
| `435701` | `LIVE_UPDATE_EVENT_INVALID` | `INTERNAL_ERROR` | `INTERNAL_ERROR` | Invalid event protocol/version |

GraphQL classifications are explicit catalog metadata rather than derived from HTTP
or category digits. Final naming must match the GraphQL framework's stable extension
vocabulary without exposing implementation classes.

## Upstream preservation

The upstream client model must carry status, symbolic `code`, `numericCode`,
`errorName`, source/component/operation, request ID, retry metadata, and a validated
safe detail. The BFF:

- preserves recognized upstream identity and safe public metadata;
- rejects malformed or unknown problem documents as BFF protocol failures;
- never rebuilds identity from status alone;
- never substitutes a random request ID when an upstream request ID is available;
- never matches English messages to decide a code;
- stores internal network/parser causes only for logs/traces.

## Reactive and WebSocket rules

Request identity lives in Reactor context across scheduler changes. Cancellation and
client disconnect are not internal failures. Timeout, circuit, connection, TLS,
protocol, decoding, and unexpected failures are explicitly classified. Subscription
quota enforcement uses typed state, not message parsing. Fanout partial failure and
reconnect/replay semantics state which subscribers were affected.

## Required tests

Cover preservation of every upstream v1/new field, missing/malformed/unknown problem
bodies, each network failure class, timeout and cancellation races, circuit opening,
partial aggregation, resolver validation, response serialization, Reactor-context
request ID, subscription quota, reconnect/replay, fanout outage, malformed live event,
redaction, and GraphQL extension compatibility.

## GraphQL and WebSocket contract

`contracts/graphql/errors.graphqls` is authoritative for all nine root
operations. Every formatted error includes the required v1 `code`, `requestId`,
`source`, and `timestamp`; valid upstream Problem Details additionally preserve
optional `numericCode`, `errorName`, and bounded validation violations.

| Upstream outcome | GraphQL result | Extension identity |
|---|---|---|
| Valid upstream 4xx/5xx Problem Details | `errors[]`; nullable fields may return partial data | Preserve the complete validated upstream identity |
| Unparseable upstream response | `errors[]` with no upstream identity | BFF `UPSTREAM_PROTOCOL_INVALID` (`426701`) |
| Upstream timeout/unavailable transport | `errors[]` with no provider detail | BFF timeout/availability identity (`426801`/`426802`) |
| Resolver or input rejection | `errors[]` | BFF `GRAPHQL_INPUT_INVALID` (`411101`) or operation-invalid (`411102`) |

GraphQL transport remains HTTP 200 for a response containing an `errors` array.
The BFF does not infer an identity from HTTP status alone when a valid upstream
Problem Details document is available.

For `graphql-transport-ws`, lifecycle failures use close codes 4401 (unauthorized
or expired authentication), 4403 (revoked subscription access), 4408 (idle
timeout), and 4429 (subscription rate limit). Reasons are bounded and never
contain stack traces, provider text, tokens, or personal data.
