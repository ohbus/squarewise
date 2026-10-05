# AUTH-09 — Distributed rate limiting and request-path security controls

## Objective

Replace process-local or single-database hot-path throttling with a provider-neutral,
distributed rate-limit capability while preserving PostgreSQL as the source of truth
for durable authentication state. Apply the capability consistently to passwordless
login, token refresh, notification delivery, GraphQL transport, WebSocket admission,
and selected public API operations.

The implementation must ensure that ordinary bearer-token authentication does not
perform an Accounts or PostgreSQL lookup. Resource services validate signed OIDC
tokens locally using cached discovery/JWK material, then perform only the domain
authorization and business queries required by the operation.

AUTH-09 is registered as an implementation task. It depends on AUTH-07 and AUTH-06. It may share the existing
security and observability libraries, but it must not create cross-service entities,
repositories, or business dependencies.

## Scope and non-goals

In scope:

- A narrow rate-limiter port and policy model in a technical/shared library.
- An atomic Redis-compatible adapter with TTL-based ephemeral state.
- Redis is the only rate-limit state store in local, staging, and production.
- Local Docker Compose Redis support, health checks, environment configuration,
  startup documentation, and operational diagnostics.
- Distributed limits for authentication, refresh, notification delivery, GraphQL,
  WebSocket admission, and ingress-facing API protection.
- Metrics, structured outcomes, failure policy, tests, live E2E tests, and load tests.
- Query-count and request-path evidence proving no remote identity lookup occurs for
  locally validated JWTs.

Out of scope:

- Replacing OIDC, Keycloak, Auth0, Okta, Entra, or another identity provider.
- Storing users, refresh-token families, revocations, audit records, or financial
  authorization state in Redis.
- Building a Redis-like engine inside PostgreSQL, adding an unreviewed PostgreSQL
  extension, or introducing cross-service SQL.
- General caching, JPA second-level caching, session storage, or financial data
  caching.
- Claiming production capacity from local Compose or a single Redis instance.

## Required request-path behavior

### Ordinary authenticated request

The target path is:

```text
HTTP request
  -> ingress/coarse network controls
  -> local JWT signature/issuer/audience/expiry/subject validation
  -> one atomic Redis limit operation where policy applies
  -> service-local authorization and business transaction
  -> response
```

JWT validation must use the Spring Security resource-server infrastructure and its
cached JWK set. It must not call Accounts, Keycloak, or PostgreSQL per request.
Authorization must be combined with business reads where practical, for example a
group-membership predicate in the expense query. A rate-limit check must not use a
read-then-write sequence; the complete decision is one atomic Redis script/command.

### Passwordless login and refresh

Login start, resend, verification, and refresh have stricter policies. They may
perform durable Accounts reads/writes because they manage security state:

- login start: one atomic rate-limit operation, then the existing credential/outbox
  transaction when the request is accepted;
- login verification: one atomic attempt-limit operation, then one conditional
  credential redemption and session-creation transaction;
- refresh: one rate-limit operation where configured, then one conditional refresh
  rotation/revocation transaction;
- logout and security-event revocation: PostgreSQL remains authoritative.

Unknown email, invalid credential, expired credential, replay, exhausted attempts,
and throttled login requests must remain externally indistinguishable according to
AUTH-07. Redis keys must contain no raw email, token, credential, IP address, or
untrusted client identifier. Derive keys from canonical values using a deployment
HMAC secret and a versioned key format.

## Rate-limiter contract

Add a provider-neutral port, preferably under `libs/security` or a narrowly scoped
technical library, with one primary operation such as:

```kotlin
interface RateLimiter {
    fun consume(key: String, policy: RateLimitPolicy): RateLimitDecision
}
```

The exact package and naming must follow existing library conventions. Public types
require KDoc describing parameters, return values, atomicity, TTL behavior, and
failure/clock edge cases.

`RateLimitPolicy` must express at least algorithm/window, maximum permits, refill or
cooldown duration, and policy identifier. `RateLimitDecision` must express allowed,
remaining (bounded and non-sensitive), retry-after, and policy identity. It must not
expose the raw key or implementation details.

The Redis implementation must:

- use a single atomic Lua script or equivalent server-side operation;
- create or increment the counter and assign expiry atomically;
- return a deterministic retry duration;
- use bounded key and value sizes;
- namespace and version all keys, for example `squarewise:rl:v1:<digest>`;
- avoid unbounded cardinality and provide expiry for every key;
- handle Redis time consistently, or document the trusted application-clock policy;
- expose timeout and error metrics without logging keys or payloads.

The existing Accounts PostgreSQL conditional-bucket implementation must be removed
from the rate-limit runtime path. PostgreSQL remains authoritative for durable
authentication state, but is not a rate-limit store. No controller or service may
implement counters directly.

## Policy inventory

Define policy identifiers centrally and document their initial values as configurable
operational defaults, not product guarantees:

| Boundary | Key dimensions | Required behavior |
| --- | --- | --- |
| Login start | HMAC(email), HMAC(network partition) | Anti-enumeration, burst and sustained limit |
| Login resend | HMAC(email), HMAC(network partition) | Cooldown before email dispatch |
| Login verify | HMAC(credential/email), HMAC(network partition) | Brute-force attempt bound |
| Refresh | subject/session family, network partition | Abuse protection without breaking normal rotation |
| Notification delivery | recipient/template or delivery identity | Enforce before provider dispatch |
| GraphQL HTTP | subject/IP plus operation class | Request and complexity limits |
| WebSocket | subject/IP plus connection state | Connection, reconnect, and subscription admission |
| Public API | IP, authenticated subject, route class | Coarse protection at BFF/ingress and service edge |

Policy configuration must have bounded numeric validation, explicit units, safe
production defaults, and no silently disabled limit. Sensitive authentication
policies fail closed when the shared store is unavailable. There is no automatic
PostgreSQL, process-local, or in-memory production fallback. The protected
operation returns the existing structured HTTP 429/`RATE_LIMITED` response with a
bounded `Retry-After` value where safe, and emits alertable store-unavailable
metrics. A 503 is permitted only where a separately reviewed public contract
explicitly defines dependency unavailability; it must never bypass the limit.

## Current code and configuration changes

Before implementation, inventory and update the current paths rather than adding a
parallel mechanism:

1. Accounts: migrate the AUTH-07 abuse-policy adapter from its current PostgreSQL
   implementation behind the shared `RateLimiter`/policy boundary. Preserve atomic
   credential redemption, generic login responses, HMAC key derivation, and the
   Accounts-owned credential/outbox transaction.
2. Notifications: ensure delivery limiting is invoked in the actual consumer path
   immediately before dispatch, not merely constructed or tested in isolation. Keep
   recipient preferences, inbox deduplication, retry, and parking semantics intact.
3. BFF: apply limits before expensive GraphQL parsing/resolution where possible, then
   retain operation/complexity bounds and upstream timeout/bulkhead controls. Do not
   turn Redis into a BFF database.
4. WebSocket: limit handshake and reconnect admission, authenticate every connection,
   bound subscriptions, and ensure revoked credentials cannot reconnect successfully.
5. Resource services: verify resource-server JWT validation is local and JWK-cached;
   remove any request-time identity/profile lookup that is not required by domain
   authorization. Add instrumentation or test probes to count SQL statements.
6. Shared configuration: centralize Redis endpoint, credentials/TLS, namespace,
   operation timeout, fail-open/fail-closed mode, and policy values. Never add
   duplicated dependency versions or committed secrets.
7. Observability: add bounded Micrometer counters/timers for decisions, denials,
   store errors, timeouts, fail-closed decisions, and policy IDs. Never use email, IP, token,
   user ID, group ID, request ID, or raw Redis keys as metric labels.

Expected files/areas to inspect and modify include `libs/security`,
`app/accounts`, `app/notifications`, `app/bff`, `libs/observability`,
`gradle/libs.versions.toml`, Spring application configuration, Compose files under
`infra/local` and production overlays, Makefile targets, and the relevant security,
notification, GraphQL, and WebSocket tests. The implementation must first search for
and reuse existing security ports, key derivation, error mapping, metrics, and
configuration patterns.

## Local development support

Add a required Redis service to every dependency and full-stack local Compose
topologies. It must have:

- a pinned, centrally documented image version;
- a meaningful internal hostname such as `rate-limit-redis`;
- explicit CPU/memory limits appropriate to the local topology;
- a health check used by `up --wait`;
- a non-production local password or protected local network configuration;
- persistence disabled or explicitly documented as disposable for local throttling;
- no published host port unless required for diagnostics;
- startup configuration for Accounts, Notifications, BFF, and any service using the
  shared adapter;
- a narrow `make redis-status`, `make redis-logs`, or equivalent diagnostic target;
- documentation for clearing only the local rate-limit namespace without deleting
  PostgreSQL volumes or unrelated data.

All local modes, including `local`, `local-oidc`, native application runs, and the
full Compose stack, use Redis. Application startup/readiness must fail when Redis is
unavailable. Protected requests must fail closed with structured HTTP 429 and must
never fall back to PostgreSQL or local memory.
2. `local-oidc`/full stack: Redis-backed limiter with real Keycloak tokens and the
   same adapter path intended for production-like testing.

Compose validation, environment doctor checks, application startup, and health
diagnostics must all work when Redis is absent in PostgreSQL mode and must fail with
an actionable configuration/health error when Redis is required but unavailable.

## Testing and evidence plan

### Unit and component tests

- policy validation rejects zero, negative, overflowing, or ambiguous durations;
- key derivation is deterministic, versioned, bounded, and secret-dependent;
- raw sensitive values never appear in keys, logs, metrics, or error details;
- Redis script results map correctly to allow, deny, remaining, and retry-after;
- first request, boundary request, expiry, cooldown, and concurrent requests behave
  atomically;
- Redis timeout, authentication failure, malformed response, and unavailable store
  return structured HTTP 429, bounded retry metadata, and alertable metrics without
  sensitive values;
- PostgreSQL adapter retains atomic conditional behavior and has no read-then-write
  race;
- notification, login, GraphQL, and WebSocket adapters invoke the shared port once
  per admission decision;
- JWT validation uses cached keys and does not invoke a user/profile repository.

### Integration tests

Run Redis and PostgreSQL with the real Flyway/JPA/configuration context. Prove:

- two application instances share one limit;
- concurrent requests cannot exceed the configured permit count;
- expired keys permit a new window;
- Redis restart and reconnect behavior matches policy;
- sensitive login limits remain generic externally;
- ordinary bearer requests produce zero Accounts identity SQL lookups;
- business authorization still queries only the owning service database;
- notification throttling occurs before SMTP/provider dispatch;
- GraphQL and WebSocket limits do not bypass REST/security policies.

### Future live E2E tasks

Create follow-up registered tasks for these independently reviewable suites:

1. `AUTH-09-E2E-LOGIN`: start two Accounts replicas against one Redis, issue
   repeated login/resend/verify requests, prove shared throttling, generic responses,
   replay protection, expiry, and no credential leakage in logs/Mailpit metadata.
2. `AUTH-09-E2E-REFRESH`: run concurrent refresh requests with one token family,
   prove exactly one rotation succeeds, reuse revokes the family, and Redis limits
   do not replace PostgreSQL revocation truth.
3. `AUTH-09-E2E-SURFACE`: exercise REST, GraphQL HTTP, WebSocket connect/reconnect,
   and Notifications delivery through the public interfaces; prove policy parity,
   structured `429`/problem responses, `Retry-After`, and bounded metrics.
4. `AUTH-09-E2E-FAILURE`: pause/restart Redis, verify every protected operation
   returns structured HTTP 429, proper alertable metrics are emitted, and recovery
   occurs without stale unlimited access or an automatic PostgreSQL/in-memory
   fallback.
5. `AUTH-09-E2E-QUERY`: enable SQL/query telemetry and prove ordinary JWT requests
   do not query Accounts for identity validation; record query counts and latency for
   representative authenticated reads and writes.
6. `AUTH-09-CAPACITY`: run k6 or the existing load harness with one and multiple
   replicas, measure Redis throughput/latency, PostgreSQL pool use, denial accuracy,
   and failure recovery. Do not infer production capacity from local results.

Each E2E task must declare its own owned paths, fixtures, Compose profile, exact
commands, report artifacts, expected evidence, and environment limitations. Tests
must use real public interfaces and real Redis; unit mocks alone cannot complete the
task.

## Documentation and operational updates

Update, as part of implementation:

- `docs/architecture/overview.md` and the technology decision to record Redis as the
  mandatory ephemeral rate-limit dependency, with no fallback;
- `docs/security/authentication-hardening.md` and the AUTH-09 readiness status;
- `docs/operations/compose-topology.md`, quickstart, CI, and production runbooks;
- `docs/operations/production-readiness-plan.md` and the relevant audit finding;
- `docs/quality/testing-strategy.md` and acceptance evidence for distributed limits;
- `docs/api/implementation-status.md` and contracts if `429`, headers, or problem
  shapes change;
- dashboards/alerts for denial spikes, store failure, latency, fail-closed decisions, and Redis
  memory/eviction/replication health.

The error contract must use the existing catalogued `RATE_LIMITED`/HTTP 429 shape,
include a bounded `Retry-After` policy where appropriate, and never reveal whether an
email or account exists.

## Acceptance criteria

- A single provider-neutral limiter port is used by all covered boundaries.
- Redis decisions are atomic, TTL-bound, distributed across replicas, and free of
  raw sensitive data.
- PostgreSQL remains authoritative for identity, sessions, revocations, audit, and
  financial state.
- Local Compose supports both PostgreSQL-only development and Redis-backed
  production-like testing with health checks and documented commands.
- Login, refresh, notifications, GraphQL, WebSocket, and public API policies are
  enforced in their actual request/dispatch paths.
- Valid JWT authentication performs no Accounts/PostgreSQL identity lookup per
  request; query-count evidence is recorded.
- Redis outage behavior is explicit, tested, observable, returns structured 429
  responses, raises proper alerts, and never silently disables authentication
  throttling or switches to another production store.
- Unit, integration, contract, live E2E, security-hygiene, and load evidence pass at
  their declared scope.
- All public code/configuration has KDoc/Javadoc and no secrets or generated output
  enter the repository.
- Architecture, operations, security, quality, API, task detail, registry, board,
  and progress evidence are synchronized by the coordinator.

## Validation commands

At minimum, the implementation must run and record exact results for:

```text
make doctor
make compose-config
make contracts
make lint
make test-unit
make check
make acceptance-live
make e2e-all
make security-hygiene
make load-test
```

Add focused commands for the Redis suite, for example:

```text
./gradlew :libs:security:test :app:accounts:test :app:notifications:test :app:bff:test --no-daemon
python3 tests/e2e/test_rate_limiting.py
python3 tests/e2e/test_auth_query_counts.py
```

Exact target names must match the repository after implementation; nonexistent
commands must not be reported as passing. Record dependency versions, topology,
replica count, Redis failure mode, report paths, and known limitations in the
registry/progress ledger.

## Dependencies and ownership

Dependencies: AUTH-06, AUTH-07, OPS-18, OPS-19, OPS-20, PR-20, PR-27, and the
relevant local infrastructure foundation. The coordinator must register AUTH-09,
assign one owner, reserve non-overlapping paths, and register each future E2E child
task before delegation. Registry and board updates remain coordinator-owned.

## Known limitations and decisions to resolve before production

- Select managed Redis, Redis Cluster, or another Redis-compatible service only after
  measured throughput, availability, TLS, backup, memory, eviction, and cost review.
- Define whether production uses one regional limiter or region-local limits; do not
  imply global enforcement without a multi-region design.
- Define and rehearse fail-closed Redis outage behavior for each policy class:
  structured 429 response, bounded retry metadata, proper alert, recovery, and no
  fallback.
- Define key namespace rotation and cleanup for policy/secret version changes.
- Define alert thresholds from measured workload rather than adopting local numbers
  as SLOs.

No production launch approval is implied by local Redis tests.

## Current implementation evidence

### Shared rate-limit contract increment (2026-10-05)

Added the provider-neutral `RateLimiter`, bounded `RateLimitPolicy`, safe
`RateLimitDecision`, fail-closed store exception, and atomic Redis adapter under
`libs/security`. The adapter derives caller-provided canonical key material with
a deployment HMAC secret before constructing the namespaced Redis key and maps
the single-script result to bounded decision metadata. Policy and key-derivation
tests pass. Service migration and live Redis/public-interface evidence remain
open.

Accounts login-start and refresh admission now consume the shared `RateLimiter`
port in production wiring. The former Accounts-specific bucket port and Redis
component have been removed; the local-oidc profile no longer registers its
in-memory test double. Accounts behavior tests pass, while live distributed
failure/concurrency evidence remains open.

Notifications delivery now delegates to the shared `RateLimiter` through its
delivery boundary. Recipient admission therefore uses the common atomic Redis
adapter, while test-only delivery doubles remain direct unit-test dependencies.
Focused adapter and Notifications context tests pass; GraphQL/WebSocket
admission, failure drills, and live broker/Redis evidence remain open.

The BFF now applies a shared Redis-backed HTTP admission decision at the
GraphQL WebFlux boundary before request parsing. Denials and store outages use
structured HTTP 429 with bounded `Retry-After`; WebSocket upgrades are left to
their dedicated admission slice. Filter and live GraphQL transport tests pass;
WebSocket, query-count, failure-drill, and live multi-replica evidence remain
open.

WebSocket upgrades now receive a distinct `graphql-websocket` admission policy
at the same BFF handshake boundary instead of bypassing rate limiting. The
policy is separate from GraphQL HTTP and remains subject to the existing JWT
security chain and subscription-cap controls. Focused handshake tests pass;
multi-replica reconnect and revoked-token live evidence remain open.

The existing Redis resilience E2E now targets the shared `squarewise:rl:v1:*`
namespace and is part of `make e2e-all` and the selected edge/security CI
stream. It proves refresh denial, targeted namespace eviction, Redis outage
fail-closed behavior, and restart recovery when Docker-backed execution runs;
multi-replica concurrency, query-count, notification, and capacity suites are
still separate open evidence.

The Redis resilience suite was executed against the local Docker Compose stack
after building the four service boot JARs. It observed ten pre-limit `401`
responses, an eleventh `429`, recovery to `401` after targeted namespace
eviction, fail-closed `429` while Redis was stopped, and recovery to `401`
after Redis restart. This is local Compose evidence only; it does not close
hosted CI, production topology, multi-replica, or capacity acceptance.

The public GraphQL surface suite now runs through the rebuilt local Compose BFF.
It admitted requests through the configured HTTP window and returned `429` at
cap plus one; it likewise admitted WebSocket handshakes through the distinct
WebSocket window and returned `429` at cap plus one. The first live run exposed
missing BFF Redis host/password wiring, which was corrected before the passing
rerun. This remains local single-replica evidence, not hosted or production
capacity evidence.

The same suite also stops Redis after clearing its namespace and verifies both
GraphQL HTTP admission and WebSocket handshake admission return fail-closed
`429` responses, then restarts Redis. Refresh outage/recovery and these BFF
public-path checks are local evidence; Notifications delivery outage and
multi-replica failure evidence remain open.

The authenticated lookup-isolation probe stopped Accounts after acquiring a
real Keycloak bearer token, then successfully read Expense Core groups directly
and BFF groups through GraphQL. This proves those ordinary bearer paths do not
require a request-time Accounts call in the local topology. The protected
Prometheus counters recorded `Accounts groups.list 0->0` and `Expense Core
groups.list 0->2` across representative authenticated reads, followed by the
same reads while Accounts was stopped. This closes the representative read
query-count evidence; broader write/query coverage and latency artifacts remain
open.

The shared JPA configuration now installs a Hibernate `StatementInspector` for
non-test runtime profiles. It increments `squarewise.db.statement` with only
the current bounded operation label and retains an in-process statement count;
SQL text, parameters, identities, and request identifiers are not recorded.
The local metric scrape and representative request-count artifact now pass;
hosted metric retention and broader request coverage remain open.

Notifications delivery policy values are now bounded configuration properties,
and both general notification dispatch and protected auth-email dispatch invoke
the shared limiter immediately before provider dispatch. Unit/component tests
cover admission and suppression. A live broker-to-provider denial/recovery
drill now passes locally through Accounts outbox, RabbitMQ, Notifications, and
Mailpit when the test-only delivery window is set to one permit per two seconds;
the production-default and hosted evidence remain separate.

An isolated k6 admission probe was added and executed at 35 iterations per
second for 10 seconds with 351 completed requests, zero HTTP failures, and
approximately 12.08 ms p95 latency. The local BFF HTTP limit was temporarily
raised to avoid measuring intentional abuse denials; this is single-replica
local wiring/capacity evidence and is not a production capacity claim.

The shared `rateLimitRedis` health contributor is explicitly imported into
Accounts, Notifications, and BFF and included in each readiness group. In the
rebuilt local Compose stack, Accounts and BFF readiness returned `200/UP` with
Redis healthy, `503/DOWN` while Redis was stopped, and `200/UP` after Redis
restart. All three services returned healthy after recovery. This is local
readiness evidence; hosted health, alert, and production failover evidence
remain open.

Rate-limit decisions now expose bounded Micrometer counters for `allowed`,
`denied`, and `store_error`, tagged only by the centrally defined policy ID and
outcome. No key, subject, IP, token, or request ID is used as a metric label.
The shared policy identifier catalog is used by Accounts, Notifications, and
the BFF; deployed scrape/alert evidence remains open.

### Current increment (2026-09-28)

The login-start request path now throws the catalogued `ERR-11` application
exception when its atomic policy denies a request, instead of converting the
denial into a misleading `202 ACCEPTED`. `GlobalErrorHandler` maps `ERR-11` to
HTTP 429/`RATE_LIMITED` and emits a bounded `Retry-After: 60` header. A rate-limit
store-unavailable decision follows the same structured 429 boundary. Focused
Accounts and shared-error tests verify the public status, code, content type, and
retry header.

The notifications inbox page-size guard now raises `ERR-02` explicitly at the
controller boundary instead of relying on a generic `require` exception. Its
HTTP representation remains the structured 400/`VALIDATION_FAILED` contract,
while the application layer now preserves the catalogued 4xx decision.

The BFF now applies the configured per-subject subscription cap in the actual
`groupChanged` resolver, releases the admission slot on stream cancellation,
and raises `ERR-11` when the cap is reached. Its GraphQL error resolver maps
catalogued application and upstream 4xx failures into `extensions.code`; query
depth/complexity rejection and upstream 429 responses use `RATE_LIMITED`.

Refresh-token rotation now performs one atomic Redis-backed network-partition
admission before session rotation. Denials and fail-closed store errors use the
same structured HTTP 429/`RATE_LIMITED` response and bounded retry header as
login-start; invalid or replayed tokens remain generic HTTP 401 responses.

The public REST boundary audit found no remaining request-limit path that falls
through to the unexpected 5xx handler: Expense Core pagination, search/export,
sync, expense, and settlement bounds are either explicit `ERR-02` decisions or
translated by the shared validation handler. Notification delivery limits remain
asynchronous and fail closed by suppressing dispatch when the limit/store path
does not admit delivery.

This increment does not claim completion of AUTH-09. The task is registered as
`in_progress`; distributed cross-surface Redis E2E, failure, query-count,
capacity, and hosted evidence remain outstanding.

- Accounts rate limiting uses the Redis bucket port and a mandatory Redis adapter;
  PostgreSQL rate-limit entities/repositories were removed and the table is retired
  by Flyway migration `V7__remove_postgresql_rate_limit_buckets.sql`.
- Notifications use `RedisDeliveryRateLimiter`; the process-local implementation is
  test-only and is not a Spring production bean.
- Reader connection failures and reader replay lag fail closed; no reader-to-writer
  fallback decision or telemetry remains.
- OIDC/Keycloak profiles validate issuer/JWK-based authentication; the internal JWT
  signer is test-profile-only and is not used by deployed profiles.
- Production messaging and security configurations require their dependencies and
  configuration properties. Docker Compose provides Redis health checks and service
  environment wiring for local development.
- In-memory persistence doubles remain available only for direct unit tests; they are
  not component-scanned or selected by production profiles. Production stores are
  JPA-backed and production broker/rate-limit paths require RabbitMQ/Redis.
