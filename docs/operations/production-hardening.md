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
readiness and Redis health indicators, inspect bounded `store_error` metrics by
service and policy, and check the Redis endpoint, authentication, TLS, and
connection saturation. Do not enable an in-memory or PostgreSQL fallback.
After Redis recovery, verify readiness returns healthy and run the documented
local/managed-environment admission recovery probe before clearing the alert.

For host-native local verification, use
`infra/observability/prometheus.local.yml`; it targets the four documented host
ports through Docker's `host.docker.internal` gateway. The production/staging
configuration remains `infra/observability/prometheus.yml`, which targets the
Compose service names.

## Release gates still required

Before public production launch, configure Prometheus retention and alert
ownership, Grafana dashboards, paging routes, SLO thresholds, backup/restore
rehearsal, secret rotation, dependency and image scanning, rate limiting, and a
capacity test at the measured workload. Local actuator exposure is intentionally
convenient; production ingress must restrict metrics to the monitoring network.
