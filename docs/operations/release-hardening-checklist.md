# Production release-hardening checklist

An image must not be promoted until the release record links evidence for each
gate below. The checklist is intentionally environment-specific: thresholds,
retention, paging destinations, and capacity limits are owned by the deployed
platform.

- [ ] Prometheus scrapes all four services and the Grafana overview dashboard is imported.
- [ ] Critical and warning alerts have tested notification routes and an on-call owner.
- [ ] Error responses expose `code`, `source`, and `requestId`; logs can locate the same request.
- [ ] Database backup was restored into an isolated environment and migrations were rehearsed.
- [ ] Broker outage, backlog, outbox retry, and notification delivery recovery were exercised.
- [ ] Secrets were injected by the deployment platform and rotation was tested.
- [ ] Dependency, image, license, and secret scans passed with reviewed exceptions.
- [ ] Rate limits and ingress restrictions protect application and actuator endpoints.
- [ ] Load and soak tests recorded throughput, p95/p99 latency, error rate, pool saturation, and recovery time.
- [ ] Rollback image and database compatibility were verified.

Missing evidence is a release blocker, not a warning. Attach command output,
timestamps, image digest, environment, and the responsible reviewer to the
release record.

Run the repository-owned prerequisite check with:

```sh
make release-gate
```

Run `make security-hygiene` for tracked-file credential checks. Run
`make load-probe URL=http://localhost:28080/actuator/health CONCURRENCY=8
DURATION=60` against a safe non-mutating endpoint and retain its JSON output.
