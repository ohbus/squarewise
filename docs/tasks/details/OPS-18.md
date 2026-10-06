# OPS-18: Micrometer and Prometheus service metrics

Expose production-safe Prometheus metrics for Accounts, Expense Core,
Notifications, and BFF. Enable service identity tags, actuator health probes,
HTTP/server metrics, JVM metrics, and the dependency instrumentation available
from Spring Boot. Add focused smoke coverage for every scrape endpoint.

Acceptance: all four applications resolve the Prometheus registry, expose
`/actuator/prometheus`, use bounded labels, and document production network
restrictions.

Validation: `make check`, `python3 tools/contracts/validate.py`, and
`git diff --check`.
