# API implementation status

The REST OpenAPI documents describe the reviewed public surface. All 45 current
REST operations are wired to controllers, and stateful operation families use
the documented PostgreSQL/JPA adapters and transaction boundaries. Contract
path items that carry `x-implementation-status` are marked
`implemented-durable`; operation-level coverage maturity remains separate from
implementation status.

Current slices include Accounts profile/export requests, single and batch profile lookup (ACC-05 with duplicate deduplication, secured by SEC-01C object authorization and internal workload role checks), Expense Core groups/members/invites, allocation preview, expenses, balances, settlements, synchronization, and recurrence, plus Notifications preferences, inbox (including idempotent mark-as-read via NOT-09), delivery, and email consumption. The BFF exposes `me`, group queries and mutations (including group renaming via CORE-18/CORE-22 and bounded member fanout via BFF-06/BFF-07), expense/repayment operations, settlement suggestions, and live change subscriptions. Coverage and persistence maturity are tracked authoritatively in `docs/tasks/registry.yaml`.

Public-interface coverage is tracked separately from implementation status in
[`docs/quality/public-interface-coverage.md`](../quality/public-interface-coverage.md)
under QA-07. An implemented operation is not considered exhaustively covered
until its contract, transport, authorization, failure, concurrency, replay,
side-effect, and live integration evidence are recorded at the appropriate
level.

Current authorization and lifecycle invariants include:

Authentication lifecycle hardening is tracked separately under AUTH-08. The
current public surface must not be interpreted as proof that every operation has
complete session-expiry, logout, cache-failure, GraphQL, WebSocket, and
production-provider evidence. The endpoint inventory and required negative
matrix are maintained in
[`endpoint-authentication-matrix.md`](../security/endpoint-authentication-matrix.md).

The BFF browser session routes (`/auth/login/start`, `/auth/login/verify`,
`/auth/token/refresh`, and `/auth/logout`) are implemented as a cookie boundary;
their access and refresh credentials are never returned to browser JavaScript.
The remaining production-provider and production-scale claims are intentionally
separate from local implementation status.

- every expense and settlement money value carries an explicit ISO 4217 currency
  in the REST and GraphQL request contract; the server never substitutes a
  currency. A future client may retain the last selected currency locally only
  as an input prefill and must still serialize it on every request.

- all group-scoped expense, settlement, recurring-schedule, and group-lifecycle
  writes/read operations require an authenticated active member;
- archived groups reject new expense writes with a conflict response, and
  archive replay is rejected;
- notification inbox and preference operations reject missing authentication;
- malformed cursors, invalid pagination bounds, malformed identifiers, and
  incompatible media negotiation have explicit problem/status coverage.
- settlement responses expose `fromParticipantId` and `toParticipantId`
  consistently across implementation and OpenAPI; reversing an unknown or
  cross-group settlement returns the structured not-found problem rather than
  an internal error.

The operation-level evidence and remaining dimensions are maintained in
[`public-interface-operation-matrix.md`](../quality/public-interface-operation-matrix.md).
The latest local Docker evidence includes 86 live REST edge checks, plus the
acceptance and signed-persona product, offline, WebSocket/concurrency, and chaos
recovery journeys, but this
does not claim production OIDC, production-scale capacity, restore, multi-region
failover, security-scan, or deployment-rollback evidence.

## ERRC-06 REST error-contract coverage

This matrix records OpenAPI declarations only. It is not evidence that every
runtime controller currently emits every declared status. All 45 operations
reference the shared `ProblemDetails` schema and explicitly declare the
predictable failure statuses below. `401` carries `WWW-Authenticate`, `405`
carries `Allow`, and `429` carries `Retry-After` through shared response
components.

| Service | Method | Path | Declared statuses |
|---|---|---|---|
| accounts | POST | `/auth/login/start` | 400, 401, 403, 404, 405, 409, 429, 500 |
| accounts | POST | `/auth/login/verify` | 400, 401, 403, 404, 405, 409, 429, 500 |
| accounts | POST | `/auth/token/refresh` | 400, 401, 403, 404, 405, 409, 429, 500 |
| accounts | POST | `/auth/logout` | 400, 401, 403, 404, 405, 409, 429, 500 |
| accounts | GET | `/me` | 400, 401, 403, 404, 405, 409, 429, 500 |
| accounts | PATCH | `/me` | 400, 401, 403, 404, 405, 409, 429, 500 |
| accounts | POST | `/me/deletion-request` | 400, 401, 403, 404, 405, 409, 429, 500 |
| accounts | POST | `/me/export-request` | 400, 401, 403, 404, 405, 409, 429, 500 |
| accounts | GET | `/me/export-requests` | 400, 401, 403, 404, 405, 409, 429, 500 |
| accounts | GET | `/profiles/{accountId}` | 400, 401, 403, 404, 405, 409, 429, 500 |
| accounts | POST | `/profiles/batch` | 400, 401, 403, 404, 405, 409, 429, 500 |
| expense-core | POST | `/groups` | 400, 401, 403, 404, 405, 409, 429, 500 |
| expense-core | GET | `/groups` | 400, 401, 403, 404, 405, 409, 429, 500 |
| expense-core | GET | `/groups/{groupId}` | 400, 401, 403, 404, 405, 409, 429, 500 |
| expense-core | PATCH | `/groups/{groupId}` | 400, 401, 403, 404, 405, 409, 429, 500 |
| expense-core | POST | `/allocations/preview` | 400, 401, 403, 404, 405, 409, 429, 500 |
| expense-core | POST | `/groups/{groupId}/archive` | 400, 401, 403, 404, 405, 409, 429, 500 |
| expense-core | GET | `/groups/{groupId}/members` | 400, 401, 403, 404, 405, 409, 429, 500 |
| expense-core | POST | `/groups/{groupId}/placeholders` | 400, 401, 403, 404, 405, 409, 429, 500 |
| expense-core | DELETE | `/groups/{groupId}/members/{membershipId}` | 400, 401, 403, 404, 405, 409, 429, 500 |
| expense-core | POST | `/groups/{groupId}/invites` | 400, 401, 403, 404, 405, 409, 429, 500 |
| expense-core | POST | `/groups/{groupId}/invites/{token}/revoke` | 400, 401, 403, 404, 405, 409, 429, 500 |
| expense-core | POST | `/invites/{token}/claim` | 400, 401, 403, 404, 405, 409, 429, 500 |
| expense-core | POST | `/groups/{groupId}/expenses` | 400, 401, 403, 404, 405, 409, 429, 500 |
| expense-core | GET | `/groups/{groupId}/expenses` | 400, 401, 403, 404, 405, 409, 429, 500 |
| expense-core | PUT | `/groups/{groupId}/expenses/{expenseId}` | 400, 401, 403, 404, 405, 409, 429, 500 |
| expense-core | DELETE | `/groups/{groupId}/expenses/{expenseId}` | 400, 401, 403, 404, 405, 409, 429, 500 |
| expense-core | POST | `/groups/{groupId}/settlements` | 400, 401, 403, 404, 405, 409, 429, 500 |
| expense-core | GET | `/groups/{groupId}/settlements/suggestions` | 400, 401, 403, 404, 405, 409, 429, 500 |
| expense-core | POST | `/groups/{groupId}/settlements/{settlementId}/reversal` | 400, 401, 403, 404, 405, 409, 429, 500 |
| expense-core | GET | `/groups/{groupId}/balances` | 400, 401, 403, 404, 405, 409, 429, 500 |
| expense-core | GET | `/groups/{groupId}/sync/snapshot` | 400, 401, 403, 404, 405, 409, 429, 500 |
| expense-core | GET | `/groups/{groupId}/sync/changes` | 400, 401, 403, 404, 405, 409, 429, 500 |
| expense-core | GET | `/groups/{groupId}/search` | 400, 401, 403, 404, 405, 409, 429, 500 |
| expense-core | GET | `/groups/{groupId}/export` | 400, 401, 403, 404, 405, 409, 429, 500 |
| expense-core | POST | `/groups/{groupId}/schedules` | 400, 401, 403, 404, 405, 409, 429, 500 |
| expense-core | GET | `/groups/{groupId}/schedules` | 400, 401, 403, 404, 405, 409, 429, 500 |
| expense-core | GET | `/groups/{groupId}/schedules/{scheduleId}` | 400, 401, 403, 404, 405, 409, 429, 500 |
| expense-core | PUT | `/groups/{groupId}/schedules/{scheduleId}` | 400, 401, 403, 404, 405, 409, 429, 500 |
| expense-core | POST | `/groups/{groupId}/schedules/{scheduleId}/pause` | 400, 401, 403, 404, 405, 409, 429, 500 |
| expense-core | POST | `/groups/{groupId}/schedules/{scheduleId}/resume` | 400, 401, 403, 404, 405, 409, 429, 500 |
| notifications | GET | `/inbox` | 400, 401, 403, 404, 405, 409, 429, 500 |
| notifications | POST | `/inbox/{notificationId}/read` | 400, 401, 403, 404, 405, 409, 429, 500 |
| notifications | GET | `/preferences` | 400, 401, 403, 404, 405, 409, 429, 500 |
| notifications | PUT | `/preferences` | 400, 401, 403, 404, 405, 409, 429, 500 |
