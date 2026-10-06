# Public-interface coverage matrix

QA-07 owns this matrix. A row is complete only when the referenced test
exercises the public transport and verifies both the response and relevant
persistence, event, or side-effect invariant. A unit or mocked gateway test is
supporting evidence, not live integration evidence.

The literal operation inventory and current gap status are maintained in the
[operation evidence matrix](public-interface-operation-matrix.md). It is
intentionally honest about partial dimensions; contract inventory completeness
does not imply edge-case completion.

Implementation-level missing unit, controller, persistence/messaging, and
deployed E2E tests are tracked in the
[repository-wide test gap audit](test-coverage-gap-audit.md). A complete row
in this public matrix does not close a production branch that the audit still
marks uncovered.

## Coverage dimensions

Each applicable operation must be checked for:

| Dimension | Required assertion |
| --- | --- |
| Success | Contract status, response shape, and durable result |
| Validation | Missing, malformed, boundary, enum, money, date, and collection inputs |
| Authentication | Missing/invalid credentials and challenge/problem response |
| Authorization | Owner/member/removed member/non-member/cross-group behavior |
| Resource state | Missing, archived, deleted, expired, already-applied, and stale version |
| Replay | Idempotency, duplicate request, duplicate event, and safe retry behavior |
| Pagination | First/last/empty pages, cursor expiry/gap, limits, and ordering |
| Failure | Timeout, malformed upstream, database/broker failure, and recovery |
| Concurrency | Simultaneous writes, optimistic conflicts, locking, and invariants |
| Side effects | Audit, sync revision, outbox, notification, balance, and subscription effects |

## REST operation inventory

The contract validator is the authoritative operation inventory. The following
families require a matrix row for every method/path pair:

- Accounts: profile read/update, deletion/export requests, export listing,
  profile lookup, and batch lookup.
- Expense Core groups: create/list/get/update/archive, members and placeholders,
  invite creation/revocation/claim.
- Expense Core financials: allocation preview, expense create/list/update/delete,
  balances, settlements, reversals, and settlement suggestions.
- Expense Core synchronization: snapshot and changes, including cursor gaps and
  expired cursors.
- Expense Core search/export: filters, pagination, CSV limits, and formula
  escaping.
- Expense Core recurrence: create/list/get/update/pause/resume and bounded catch-up.
- Notifications: inbox, mark-read, preferences read/update, consumer delivery,
  deduplication, retry, and broker recovery.

## GraphQL and WebSocket inventory

The matrix covers every schema field:

- Queries: `me`, `groups`, `group`, `settlementSuggestions`.
- Mutations: `createGroup`, `updateGroup`, `createExpense`, `recordRepayment`.
- Subscription: `groupChanged` over HTTP negotiation and the
  `graphql-transport-ws` WebSocket protocol.

Subscription rows track handshake authentication, malformed frames, invalid
arguments, filtering, multiple subscribers, queue pressure, expiry,
unsubscribe, reconnect/resubscribe, authorization revocation, broker loss, and
recovery. The current live evidence proves handshake, malformed operations,
filtering, non-member authorization, reconnect/resubscribe, unsubscribe, and
delivery; broker-loss, malformed-frame, timeout, sustained backpressure, and
replay-after-reconnect remain explicitly unproven.

## Evidence levels

| Level | Meaning |
| --- | --- |
| Contract | Schema/path/operation is structurally and semantically valid |
| Controller | Transport mapping and error conversion tested in-process |
| Persistence | Real database constraints, versioning, and side effects tested |
| Live integration | Running service and dependencies exercised through HTTP/WS |
| E2E | Cross-service public journey with durable and asynchronous assertions |

Coverage claims must name the highest level actually executed. Local evidence
does not prove production restore, production-scale capacity, security scans, or
deployment rollback.

## Executable suite-to-dimension matrix

The same dimensions are exercised at different boundaries. A suite may provide
supporting evidence for an operation, but it cannot replace a public-interface
check when the matrix requires one.

| Suite | Boundary | Dimensions covered | Explicit limitations |
| --- | --- | --- | --- |
| Gradle service tests | Controller, service, persistence | Validation, authorization, not-found, conflict, money/allocation invariants, pagination, persistence, audit, sync, outbox, notification deduplication | Mocked or in-process dependencies do not prove deployed wiring |
| BFF GraphQL HTTP transport tests | `/graphql` HTTP | Query/mutation success, malformed JSON transport, malformed input, invalid fields, upstream validation/auth/timeout/malformed failures, error redaction | Does not prove WebSocket framing or live upstream availability |
| Bruno collection | Live REST and GraphQL HTTP | Contract-shaped success flows, persistence-visible sequencing, allocation, recurrence, settlement, sync, notification inbox/preferences, structured negative responses | The collection contains 50 requests with 71 assertion-backed checks; surface-aware matching finds 18 operation signals and 36 operations without a Bruno request signal. Dimension-specific negative cases remain in dedicated E2E suites. |
| `tests/e2e/test_rest_edge_cases.py` | Live REST | Authentication, malformed IDs, boundary validation, missing resources, idempotent replay, tampered replay conflict | Does not cover every endpoint’s full authorization matrix |
| `test_product_journey.py` | Live cross-service REST | Group/expense/settlement journey, balances, outbox-to-inbox delivery, offline replay | Production scale and deployment failure domains are not represented |
| `test_offline_resilience.py` | Live REST sync | Queue/replay, duplicate suppression, idempotency conflict, cursor recovery | Client implementation is simulated |
| `test_concurrency_subscriptions.py` | Live REST + GraphQL WebSocket | RFC 6455 handshake, `graphql-transport-ws`, subscription delivery, concurrent stale conflict, recovery/resubmission | Does not prove multi-region broker ordering or sustained queue pressure |
| `test_chaos_recovery.py` | Live dependency failure | GraphQL Expense Core outage error envelope, RabbitMQ outage, transactional outbox retention/drain, downstream delivery recovery | Local fault injection only; retry/timeout policy is not production-proven |

The BFF fanout/controller unit suites additionally cover group filtering, multiple
subscriptions, queue capacity and ordering, unsubscribe, TTL expiry, user
revocation, invalid inputs, and invalidation side effects. These are controller
and in-process fanout evidence; the live WebSocket suite is the public protocol
evidence.

### Evidence options and interpretation

For each operation, reviewers should record one or more of these evidence
options: `contract` (schema/path validity), `controller` (in-process transport),
`persistence` (real database invariants), `live-integration` (running service and
dependencies), and `e2e` (cross-service public journey). The strongest executed
option is the claimed level; a green unit test must not be reported as live or
production evidence. CI runs contract validation, formatting, all Gradle checks,
acceptance tests, and, when the E2E input is enabled, the complete local-stack
integration suites.

The prior local `make e2e-all` execution passed all four suites. This confirms
the listed local live-integration/E2E dimensions for that recorded revision;
the current stack must be rerun after every acceptance-fixture or contract
change. Production-scale
capacity, multi-region ordering, restore rehearsal, security scanning, and
deployment rollback as environment-dependent evidence.

The latest reported `make acceptance-live` run on 2026-10-01 is not a green
closure artifact: 65 of 71 requests passed and six failed. The failures were
Accounts logout (`400`, where the request assertion allowed only `204` or
`401`), Expense Core create expense (`404`), update expense (`500`), delete
expense (`500`), record settlement (`400`), and reverse settlement (`401`).
The following `make e2e-live` run also stopped when the signed owner called
Accounts `getProfilesBatch` and received `403` because the endpoint requires
internal workload authority. These results are evidence of missing or
misaligned deployed acceptance, not permission to loosen assertions: the
replacement tests must first establish the signed persona, durable fixture
state, expected contract status/error, and no-mutation or side-effect
invariant for each failure. If the owner-versus-workload authority expectation
is inconsistent with the contract, resolve that contract decision before
changing either the implementation or the test.

Every GraphQL HTTP query and mutation now has direct transport evidence for
upstream failure or timeout conversion and detail redaction. Settlement
suggestions additionally cover an empty upstream result. Retry timing and policy
under production-like dependencies remain QA-08 evidence rather than a QA-07
local claim.
