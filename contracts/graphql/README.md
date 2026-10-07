# GraphQL error and WebSocket contract

`errors.graphqls` defines the extension fields used by GraphQL error responses.
GraphQL transports still place error objects in the protocol-level `errors` array.

The BFF preserves `code`, `numericCode`, `errorName`, `requestId`, `source`,
`timestamp`, and validation violations from a valid upstream Problem Details
document. `numericCode` and `errorName` are required after the local contract
promotion rehearsal. Malformed or legacy-only upstream responses receive the
BFF-owned `UPSTREAM_PROTOCOL_INVALID` identity and never a fabricated upstream
identity.

For `graphql-transport-ws`, lifecycle failures use the application close-code
range `4400`-`4499`:

| Close code | Meaning | Client action |
|---:|---|---|
| 4401 | Missing, expired, or invalid authentication | Reauthenticate and reconnect |
| 4403 | Access to the subscribed group was revoked | Stop and refresh authorization |
| 4408 | Connection idle timeout | Reconnect and resubscribe |
| 4429 | Subscription admission limit exceeded | Wait for the retry window and reconnect |

Reasons are bounded and safe; they contain no stack traces, provider details,
credentials, or personal data. Before acceptance, connection failures use the
protocol `connection_error` message where supported; after acceptance the server
closes with the mapped code.
