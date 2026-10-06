# OPS-21: Modular k6 load tests for high-value endpoints

Create independently runnable k6 scenarios for Accounts, Expense Core,
Notifications, and BFF GraphQL. Keep scripts modular, tag requests by bounded
endpoint names, use explicit error/latency thresholds, and separate safe read
traffic from fixture-backed mutation tests.

Acceptance: each application has a script, the GraphQL path is covered, target
URLs/token are configurable, thresholds and rates are documented as provisional,
and JSON/JS structure validation passes. A representative environment must run
the scripts and attach throughput, p95/p99, error rate, saturation, and recovery
evidence before this task is marked complete.

Validation: `make load-k6-validate`; k6 execution is environment-dependent.
