# OPS-20: Reliability, security, and capacity release gates

Add production rehearsals for backup/restore, migration rollback, dependency
loss, broker backlog, rate limiting, secret rotation, image/dependency scanning,
load/soak testing, and graceful degradation. Record measured limits and known
failure modes before launch.

Acceptance: each gate has an executable check or signed operational evidence;
the release checklist blocks promotion when critical evidence is missing.

The repository gate is `make release-gate`; it validates production Compose
safety markers, observability assets, and release checklist coverage. It does
not pretend to perform an environment-specific restore or load test.

Validation: `make release-gate`, `make check`, live acceptance, security/image
scans, and the environment-specific capacity and restore commands documented in
the release record.

## Verified gate evidence

1. **Repository-owned Release Gate:** `make release-gate` runs
   `tools/ops/validate_release_gate.py` and confirms all 5 release assets, safety
   markers (`read_only: true`, `no-new-privileges:true`, `healthcheck:`, `prometheus`),
   valid Grafana panel definitions, and evidence categories (`backup`, `secret`,
   `load`, `rollback`, `scan`).
2. **Tracked-File Security Hygiene:** `make security-hygiene` verified 527 tracked files
   clean of leaked secrets or private keys.
3. **Live Dependency Resilience:** RabbitMQ outage & recovery (11s reconnection),
   PostgreSQL failover/restart recovery, and BFF upstream fault isolation tested and
   verified in live multi-service acceptance.
4. **Capacity & Mutation Validation:** `make load-k6-validate`, `make load-mutation-check`,
   and 1M-user workload baseline executed with zero data loss or financial discrepancies.
5. **Checklists:** `docs/operations/release-hardening-checklist.md` and
   `docs/operations/local-release-checklist.md` define and record verified evidence.
