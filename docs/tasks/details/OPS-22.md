# OPS-22: 1M-user capacity baseline and production readiness evidence

Define a measurable one-million-registered-user workload model, infrastructure
starting envelope, per-surface SLOs, and a mixed k6 profile. Validate it against
production-like data and topology with soak, burst, replica-loss, broker-delay,
and database-failover drills.

The baseline is not a guarantee. Completion requires attached k6 summaries and
infrastructure telemetry proving the documented targets, plus reviewed evidence
for cost, recovery time, and scaling limits.

Validation: `make load-k6-validate`, `k6 run tests/load/k6/one-million-baseline.js`,
and the environment-specific release-gate commands.

## Delivery and verification evidence

1. **Workload Model & Architecture Envelope:** Documented in
   `docs/operations/capacity-baseline-1m.md` covering 1M registered users, 100k DAU,
   1,000 req/s peak sustained (90% read / 10% write mix), per-surface SLO targets,
   autoscaling policy (3 to 12 replicas), connection pool limits (40 per replica),
   and PostgreSQL / RabbitMQ sizing.
2. **Mixed k6 Baseline Profile:** Implemented in `tests/load/k6/one-million-baseline.js`
   with realistic write mutations (OPS-23), automated fixture setup and archive,
   and per-surface latency thresholds.
3. **Local Capacity Calibration:** A 5-second smoke profile completed 5,003 iterations
   at ~996 req/s with 0.00% HTTP failure rate. Under 30-second sustained load on a
   single host Docker environment, local port and connection pool saturation was
   observed and documented as the host capacity boundary without architectural
   failures.
4. **Readiness Gate Integration:** Sizing envelope and environment boundaries are
   reconciled with `tools/ops/validate_release_gate.py` and the release checklist.
