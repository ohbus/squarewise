# OPS-19: Dashboards, alerts, SLOs, and production runbooks

Create deployment-owned Prometheus recording rules, Grafana dashboards, paging
routes, and runbooks for API RED metrics, database/broker saturation, outbox
lag, delivery failures, BFF upstream failures, and JVM health. Define SLOs from
measured workload rather than inventing capacity guarantees.

Acceptance: every alert has an owner, severity, query, threshold, runbook link,
and test or replay evidence; metrics retention and cardinality limits are
documented.

Validation: dashboard/rule linting, configuration review, and a failure-drill
report recorded in `docs/tasks/progress.md`.
