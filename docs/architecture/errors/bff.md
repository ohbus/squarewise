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
