# Production hardening

This document defines the production observability baseline for the four
non-UI services. It is an operational requirement, not a substitute for a
deployment-specific runbook.

## Failure identification

Every REST problem response contains a stable `code`, the originating service
`source`, and the propagated `requestId`. Operators should search logs using
`requestId`, then group failures by `source` and `code`. Response `detail` must
remain safe for clients; stack traces and sanitized exception causes belong only
in server logs.

The stable error vocabulary is defined by `contracts/errors/problem.schema.json`.
New codes require a contract change, handler mapping, client-safe description,
tests, and a dashboard/alert decision.

## Metrics baseline

Each service exposes `/actuator/prometheus` and `/actuator/metrics` in local and
deployment profiles. Spring Web and WebFlux instrumentation provides request
count, status, latency, and in-flight gauges. Database, RabbitMQ, JVM, and
executor meters must be enabled by the deployment and scraped with a `service`
label. Never add unbounded labels such as request IDs, users, group IDs, or
exception messages.

Required dashboards cover request rate/error rate/latency (RED), JVM and process
health, PostgreSQL pool saturation, RabbitMQ backlog and consumer failures,
outbox age/retry counts, notification delivery outcomes, and BFF upstream
latency/error rates. The rate-limit dashboard also exposes bounded admission
outcomes by service, policy, and outcome; it must not expose keys, subjects,
addresses, tokens, or request IDs.

### Rate-limit store unavailable

`SquarewiseRateLimitStoreUnavailable` means protected admission is failing
closed because Redis decisions cannot be made safely. Confirm the service
readiness and Redis health indicators, inspect bounded `store_error`, `timeout`,
and `fail_closed` metrics by service and policy, and check the Redis endpoint,
authentication, TLS, and connection saturation. Do not enable an in-memory or
PostgreSQL fallback.
Production and staging Compose use an externally managed Redis endpoint; provide
`SQUAREWISE_REDIS_HOST`, `SQUAREWISE_REDIS_PORT`,
`SQUAREWISE_REDIS_PASSWORD`, and `SQUAREWISE_REDIS_SSL_ENABLED` through the
deployment secret/configuration manager. The production Compose contract also
requires `SQUAREWISE_SECURITY_CREDENTIAL_DIGEST_SECRET` for HMAC-derived limiter
keys. Never place those values in the repository or disable TLS for a managed
production endpoint.
After Redis recovery, verify readiness returns healthy and run the documented
local/managed-environment admission recovery probe before clearing the alert.
JVM images use finite DNS cache lifetimes so a managed endpoint or disposable
Redis container can receive a new address without requiring an application
restart; verify this behavior during failover rehearsal.

For host-native local verification, use
`infra/observability/prometheus.local.yml`; it targets the four documented host
ports through Docker's `host.docker.internal` gateway. The production/staging
configuration remains `infra/observability/prometheus.yml`, which targets the
Compose service names.

## Notification deduplication retention

Notifications retain processed event identifiers for the configured
`SQUAREWISE_NOTIFICATIONS_PROCESSED_EVENT_RETENTION` period and remove old
markers in bounded batches. Set this period no shorter than the approved
broker replay guarantee; the default `30d` is an application safety baseline,
not production capacity or retention approval.
Email dispatch also claims a durable five-minute lease before provider
dispatch. This prevents concurrent replica sends and recovers crashed
consumers, but cannot provide provider-level exactly-once delivery after a
provider accepts mail and the consumer crashes before recording `DELIVERED`.

## Aggregate balance result bounds

Expense Core fetches at most 1,001 aggregate participant/currency rows for
balances and settlement suggestions. Results above the 1,000-row correctness
bound are rejected with the existing invalid-request error rather than
truncated, so clients cannot receive incomplete financial state. Pagination for
larger groups remains a future API contract decision. This bound protects the
database result window and JVM settlement calculation; it is not a substitute
for production capacity testing.

## Release gates still required

### Staging service credentials

The staging overlay provisions separate PostgreSQL roles and RabbitMQ users for
Accounts, Expense Core, and Notifications. Supply the per-service credential
variables documented by `infra/deploy/docker-compose.staging.yml`; do not reuse
the bootstrap PostgreSQL or RabbitMQ credentials in application containers.
The database and broker initialization scripts run only on a fresh volume, so
credential rotation or an existing volume requires the normal operator
provisioning procedure before rollout.

Before public production launch, configure Prometheus retention and alert
ownership, Grafana dashboards, paging routes, SLO thresholds, backup/restore
rehearsal, secret rotation, dependency and image scanning, rate limiting, and a
capacity test at the measured workload. Local actuator exposure is intentionally
convenient; production ingress must restrict metrics to the monitoring network.
