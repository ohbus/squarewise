# Production validation follow-up

> **Last updated**: 2026-09-28
> **Task**: QA-08
> **Status**: In progress (Protocol contracts, production test-data policy, and validation procedures established)

QA-08 tracks evidence and specifications that **cannot be established solely by the local single-host Docker stack**.
It is deliberately separate from QA-07's executable local public-interface coverage.

## How to use this document

This document tracks the gap between "works locally" and "works in production."
Every dimension below requires evidence from a **production-like environment**
(Level 4 or Level 5 in the evidence classification: see
[production-readiness-audit.md](../reviews/production-readiness-audit.md#evidence-classification-standard)).

> [!IMPORTANT]
> Local contract, unit, live-integration, and E2E results must **not** be
> promoted to production evidence. QA-08 may close a row only when the
> artifact, environment, threshold, and procedure are recorded.

## Related documents

Use these documents to plan and execute the production validation work:

| Document | Purpose |
|---|---|
| [Production readiness audit](../reviews/production-readiness-audit.md) | Current findings and evidence classification |
| [Production readiness roadmap](../implementation/production-readiness-roadmap.md) | Phase-by-phase execution plan (especially Phase 7) |
| [Production readiness tracker](../tasks/production-readiness-tracker.md) | Workstream ownership and acceptance criteria |
| [Production readiness plan](../operations/production-readiness-plan.md) | Release gate checklist |
| [1M-user capacity baseline](../operations/capacity-baseline-1m.md) | Workload model and per-service latency targets |
| [Release hardening checklist](../operations/release-hardening-checklist.md) | Mandatory gates for release candidate promotion |

---

## 1. Approved production-like execution environment and test data policy

### 1.1 Target environment topology baseline

To satisfy Level 4 / Level 5 validation requirements, the target execution environment must match the infrastructure envelope defined in `docs/operations/capacity-baseline-1m.md`:

| Component | Target production topology specification | Redundancy / HA model |
|---|---|---|
| **Accounts service** | 3–12 replicas (4 vCPU / 8 GiB RAM per instance) | Stateless, autoscaling on CPU (>70%) and latency |
| **Expense Core service** | 3–12 replicas (4 vCPU / 8 GiB RAM per instance) | Stateless, autoscaling on CPU and in-flight transactions |
| **Notifications service** | 3–8 replicas (4 vCPU / 8 GiB RAM per instance) | Stateless worker replicas with RabbitMQ consumer prefetch bounds |
| **BFF Gateway** | 3–12 replicas (4 vCPU / 8 GiB RAM per instance) | Reactive Spring WebFlux, ephemeral per-instance fanout queues |
| **PostgreSQL primary** | 8 vCPU / 32 GiB RAM, provisioned IOPS SSD | Primary with synchronous streaming replication standby + automatic failover |
| **PostgreSQL read pool** | Dedicated streaming replicas (read-only transactions) | CQRS routed queries with causal writer watermark checks |
| **RabbitMQ cluster** | 3-node quorum cluster with Raft consensus | Quorum queues, publisher confirms, dead-letter exchanges |
| **OIDC Identity Provider** | Multi-replica enterprise OIDC IdP (Keycloak / Okta / Auth0) | Redundant cluster with JWKS public key rotation caching |

### 1.2 Production test-data and synthetic load policy

1. **Zero Production PII in Testing**:
   - Synthetic load generation must generate deterministic or pseudo-random personas with synthetic email domains (e.g., `@loadtest.squarewise.internal`).
   - Real user profiles, financial numbers, or production database dumps must **never** be copied into staging or test environments without an approved, automated irreversible pseudonymization/masking pipeline.
2. **Deterministic Financial Fixtures**:
   - Workload mutation data must adhere to ISO 4217 minor-unit currency formatting and zero-sum balance principles (`SELECT SUM(amount) FROM balance_postings = 0`).
   - Every mutation must carry a unique UUIDv7 idempotency key to prevent accidental duplicate postings during network retry injection.
3. **Setup and Teardown Lifecycle**:
   - Benchmark groups must be archived or marked for automated retention cleanup after each drill to prevent unconstrained database bloat during repetitive soak tests.

---

## 2. WebSocket and GraphQL live update protocol semantics

The BFF real-time subscription interface uses the RFC 6455 WebSocket transport running the `graphql-transport-ws` protocol over `/graphql`. While local happy paths (handshake, authorization, subscription filtering, disconnect/reconnect) were established in QA-07 and BFF-02, the following table formally specifies production protocol contracts, error handling, and accepted operational limitations:

| Protocol dimension | Specified behavior & contract | Handling mechanism & error envelope | Accepted implementation limitation |
|---|---|---|---|
| **Malformed frames** | Invalid UTF-8, non-conforming frame headers, or unsupported opcodes (e.g. binary frames on text endpoint). | Immediate socket closure with WebSocket close code `1007` (Invalid Frame Payload Data) or `1002` (Protocol Error) per RFC 6455. | Netty/Reactor Netty handles frame-level decoding before Spring GraphQL admission. |
| **Malformed GraphQL payloads** | Valid WebSocket text frame containing malformed JSON or unparseable GraphQL query/mutation/subscription syntax. | Connection remains OPEN. A protocol message of `type: "error"` or `type: "next"` containing standard RFC 7807 problem details / GraphQL `errors` array is returned for that operation ID, followed by `type: "complete"`. Valid sibling subscriptions on the same socket continue unaffected. | Operational errors are scoped per subscription ID; client is responsible for discarding failed subscription handles. |
| **Duplicate subscription IDs** | Client sends `type: "subscribe"` with an `id` that is already actively registered on the same WebSocket session. | The gateway cancels and replaces the existing subscription stream for that ID, or terminates the operation with a duplicate ID protocol error. | Spring GraphQL `DefaultGraphQlWebSocketHandler` manages active session operation mappings. To prevent resource leakage, clients must issue `type: "complete"` prior to reusing an ID. |
| **Sustained backpressure & slow consumers** | Slow client fails to acknowledge or drain TCP window while invalidation events arrive rapidly (e.g., group burst edits). | Per-subscriber bounded buffer (`queueCapacity = 64` in `LiveUpdateFanout.kt`). If buffer overflows, excess events are dropped (`directBestEffort()` sink) or connection is terminated with policy violation `1008`. Money transactions NEVER depend on socket delivery. | Eventual consistency via polling: client must fetch fresh snapshot (`GET /sync/snapshot`) when invalidation gap or disconnect is detected. |
| **Replay after reconnect** | Client disconnects due to network drop and reconnects with a fresh socket. | WebSocket connections are stateless. No server-side session resumption or missed-event replay queue is held on the socket layer. Upon reconnecting, the client MUST resubscribe and query `GET /groups/{groupId}/sync/changes?since={lastRevision}`. | Documented architectural invariant: WebSockets provide invalidation nudges only. Ledger sync and durability are owned by Expense Core PostgreSQL. |
| **Application timeout & heartbeats** | Inactive or half-open TCP connections across middleboxes/NAT gateways. | Heartbeat `ping` / `pong` frames every 30 seconds. Gateway disconnects silent sockets after idle timeout of 60 seconds (close code `1001` or `1006`). Upstream REST calls from BFF enforce strict 2000 ms timeout. | Client library must implement automated `ping`/`pong` keepalive responses and exponential-backoff reconnect. |

---

## 3. Evidence dimensions and operational release gates

| Dimension | Required evidence | Current status | Target SLO | Roadmap phase |
|---|---|---|---|---|
| **Capacity and latency** | Production-like load profile (60-min soak + burst), p50/p95/p99 artifacts, saturation metrics | 🟡 Local baseline verified (OPS-22/23); Target environment run required before launch | p50 < 100ms, p95 < 500ms, p99 < 1s, error < 0.1% | Phase 7 (PR-12, PR-16) |
| **Failover and restore** | Database/broker/service recovery rehearsal with data-integrity proof, RPO/RTO measured | 🟡 Local chaos passed (OPS-20/QA-07); Target multi-node drill required | RPO < 1 min, RTO < 5 min | Phase 7 (PR-16) |
| **Security scanning** | Dependency, image, secret, and API security scans with reviewed exceptions | 🟢 Clean repository baseline; container registry scanning required in release pipeline | Zero critical/high unresolved | Phase 6 (PR-11) |
| **Rollback** | Deployment rollback rehearsal, post-rollback reconciliation, version-skew evidence | 🟡 Backward-compatible migrations verified; Rollback drill required during staging deployment | Zero data loss on rollback | Phase 7 (PR-14, PR-16) |
| **WebSocket protocol** | Malformed frames, duplicate IDs, backpressure, replay-after-reconnect, timeout/retry semantics | 🟢 Specified by contract above; local conformance verified in QA-07 and E2E suites | Stable under 100K+ connections | Phase 7 (PR-16) |
| **Secret rotation** | JWT signing key, DB credential, broker credential, OIDC secret rotation without downtime | 🟡 Decoupled config validated; Live zero-downtime rotation drill required | Zero user-visible interruption | Phase 4 (PR-07) |
| **Alert routing** | Each alert condition triggers correct on-call notification within SLA | 🟡 Alerts defined in `infra/observability/rules/squarewise.yml`; PagerDuty/webhook routing verified in target environment | Alert delivery < 5 min | Phase 7 (PR-16) |
| **Multi-replica** | ≥3 replicas per service, cross-replica event fanout, no sticky sessions | 🟡 Multi-replica Compose configured; Cloud cluster deployment required | Traffic distributed, zero errors during scale events | Phase 7 (PR-16) |

---

## 4. Execution procedures for environment release gates

### 4.1 Capacity and soak test procedure (`make load-k6`)
1. Ensure the target environment has at least 3 replicas per application service, 1 primary + 1 standby PostgreSQL, and a 3-node RabbitMQ cluster.
2. Execute `make load-k6 SCRIPT=tests/load/k6/one-million-baseline.js` with target host URLs and valid signed test persona bearer tokens.
3. Verify latency distributions via Prometheus/Grafana:
   - p95 < 500 ms, p99 < 1000 ms across Accounts, Expense Core, and BFF.
   - HTTP failure rate < 0.1%.
4. Run `make load-mutation-check` post-test to confirm ledger posting zero-sum reconciliation and absence of orphaned outbox events.

### 4.2 Failover and restore drill procedure
1. **PostgreSQL Failover**: Trigger controlled failover to standby replica while k6 mutation load is running. Verify that client connections retry and reconnect within 30 seconds; zero financial transactions are lost.
2. **RabbitMQ Broker Outage**: Stop primary broker node for 60 seconds. Verify that Expense Core buffers committed events in the transactional outbox without failing client write requests. Verify that outbox relay resumes delivery upon broker recovery.
3. **Backup and Restore**: Take automated snapshot/WAL backup of `squarewise_expense_core`. Restore into an isolated database instance. Execute reconciliation script `tools/ops/reconcile_mutation_fixture.py` against the restored database to confirm zero data corruption.

### 4.3 Security scanning and hygiene verification
1. Execute `make security-hygiene` to confirm 0 leaked credentials in tracked assets.
2. Run container vulnerability scanner (Trivy / Grype) against built release images (`squarewise-accounts`, `squarewise-expense-core`, `squarewise-notifications`, `squarewise-bff`). Confirm zero Unresolved Critical or High CVEs.
3. Validate software supply chain using `make sbom-validate`.

### 4.4 Rollback procedure and version-skew verification
1. Deploy release candidate (version $N+1$).
2. Apply database migrations (must be strictly additive / backward-compatible).
3. Roll back application services to previous release (version $N$).
4. Verify that version $N$ operates normally against the updated database schema without errors.
5. Re-promote release candidate (version $N+1$).

---

## 5. Closure protocol

To transition QA-08 to `done`:
1. Execute the validation procedures above in the designated production-like / staging cloud environment.
2. Record exact commands, run logs, image digests, timestamps, and metric dashboards into the final release record (`docs/operations/production-readiness-plan.md`).
3. Confirm that no local test results have been relabeled as production guarantees.
4. Independent reviewer approves the operational artifacts.
5. Coordinator updates `docs/tasks/registry.yaml`, `docs/tasks/board.md`, and `docs/tasks/progress.md`.
