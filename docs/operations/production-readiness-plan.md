# Production Readiness Plan: Pre-Launch Gate Checklist

> **Last updated**: 2026-09-20
> **Target**: 1–10 million DAU backend deployment
> **Status**: Release gate: must be completed against the release candidate

## How to read this document

This checklist is the **final release gate** for the Squarewise backend. It must
be completed against the **actual release candidate** in the **target deployment
environment** (or a validated production-like environment). Local Compose, unit
tests, and static validation are supporting evidence only; they do **not**
approve production promotion.

Every checkbox represents a **measurable, verifiable condition**. There are no
subjective judgments. Either the evidence exists or it doesn't.

> [!IMPORTANT]
> This is a go/no-go checklist, not a development task list. The development
> work is tracked in the [production-readiness-tracker.md](../tasks/production-readiness-tracker.md)
> and executed according to the [production-readiness-roadmap.md](../implementation/production-readiness-roadmap.md).
> This document is filled out at the **end** of that process.

### Document relationships

| Document | Purpose | When to use |
|---|---|---|
| [production-readiness-audit.md](../reviews/production-readiness-audit.md) | What is wrong and why | Understanding findings |
| [production-readiness-roadmap.md](../implementation/production-readiness-roadmap.md) | How to fix it (phases) | Planning implementation |
| [production-readiness-tracker.md](../tasks/production-readiness-tracker.md) | Who is doing what, when | Tracking progress |
| **This document** | Is it ready to ship? | Final release decision |

---

## Decision rule

> **Promote to production ONLY when:**
>
> 1. Every **must-pass** item below has current evidence
> 2. Every known exception has an **owner** and **expiry date**
> 3. The release manager records a **go/no-go decision**
>
> **Any failed security, data-integrity, recovery, migration, configuration,
> or capacity item is a release blocker.** No exceptions.

---

## 1. Scope and evidence

> [!NOTE]
> This section ensures we know exactly what we're shipping and that every
> claim is backed by evidence from the right environment. The most common
> mistake is using local test results as production evidence.

### Checklist

- [ ] **Release scope documented**: API changes, migrations, events, and
      operational changes are linked to registered tasks in `docs/tasks/registry.yaml`

- [ ] **Release candidate immutable**: Identified by:
  - Commit hash: `________________`
  - Image digests (one per service): `________________`
  - Dependency lock state: `________________`
  - Generated SBOM: `________________`

- [ ] **Evidence standards met**: All evidence names the exact:
  - Version (commit + image digest)
  - Environment (local/CI/staging/production-like)
  - Date and operator
  - Command that produced the evidence
  - Result (pass/fail + metrics)
  - Limitation (what it does NOT prove)

- [ ] **No evidence inflation**: No local-only result is described as
      production capacity or availability evidence

### How to verify

```bash
# Generate SBOM (CycloneDX)
./gradlew.bat cyclonedxBom

# Get image digests
docker inspect --format='{{.RepoDigests}}' squarewise-accounts:latest
docker inspect --format='{{.RepoDigests}}' squarewise-expense-core:latest
docker inspect --format='{{.RepoDigests}}' squarewise-notifications:latest
docker inspect --format='{{.RepoDigests}}' squarewise-bff:latest

# Verify commit
git rev-parse HEAD
```

---

## 2. Code quality and clean design

> [!NOTE]
> This section verifies that the codebase follows enterprise engineering
> standards. These are not style preferences: they prevent entire categories
> of bugs at scale. Reference: [programming-principles.md](../quality/programming-principles.md),
> [coding-guidelines.md](../quality/coding-guidelines.md).
>
> **Design principle**: **SOLID**: Single Responsibility, Open/Closed,
> Liskov Substitution, Interface Segregation, Dependency Inversion.

### Checklist

- [ ] **Clean build**: `./gradlew.bat check --no-daemon` passes from a clean
      checkout with no cached build artifacts

- [ ] **Static analysis passes**: Formatting (Spotless), static analysis
      (Detekt), compiler warnings, architecture tests (ArchUnit), and
      KDoc/Javadoc checks all pass

- [ ] **Single Responsibility enforced**: Each class, entity, repository,
      service, adapter, controller, and configuration component has ONE
      responsibility and ONE authoritative home file

  > 📖 **What this means**: `ExpenseController.kt` does transport mapping
  > only. `ExpenseService.kt` owns business logic and transactions.
  > `ExpenseEntity.kt` is the JPA entity. `ExpenseRepository.kt` is the
  > Spring Data interface. They are in separate files.

- [ ] **No business rule duplication**: Business rules are NOT duplicated
      between controllers, BFF resolvers, in-memory stores, JPA stores,
      scripts, and contracts

  > 📖 **DRY Principle**: If expense validation exists in `ExpenseValidator`,
  > it must NOT also exist in `ExpenseController`. The controller delegates to
  > the validator. One source of truth.

- [ ] **Shared libraries are technical only**: `libs/` contains technical
      concerns only (error handling, IDs, observability, security
      infrastructure). They do NOT become a hidden cross-service domain layer

  > 📖 **What this means**: `libs/errors` provides the error framework.
  > It does NOT define business-specific error codes that belong in
  > `app/expense-core/`.

- [ ] **Dependency Inversion for external systems**: Application code depends
      on ports (interfaces), not concrete adapters, where external systems are
      involved

  > 📖 **Hexagonal Architecture**: `ExpenseService` depends on
  > `ExpenseStore` (interface/port), not `JpaExpenseStore` (adapter).
  > This allows swapping implementations for testing.

- [ ] **Controllers do transport mapping only**: Transaction boundaries and
      business invariants remain in application/domain services. Controllers
      parse HTTP, delegate, and format responses.

- [ ] **No production test contamination**: No production path uses test
      fixtures, placeholder identities, fallback credentials, hard-coded
      tokens, or fail-open authentication
  - Specifically verified: `InternalJwtTokenProvider` NOT in production context
  - Specifically verified: `X-Acceptance-Fault` header ignored in production
  - Specifically verified: `InMemory*Store` classes NOT in production context

- [ ] **Documentation complete**: Public methods, models, and non-trivial
      domain logic document parameters, return values, invariants, failure
      modes, and edge cases with `/** ... */` KDoc comments

- [ ] **No dead code**: Dead code, stale compatibility branches, commented-out
      implementations, TODOs, and speculative abstractions are removed or
      tracked with an owner and expiry date

### How to verify

```bash
# Full build check
./gradlew.bat check --no-daemon

# Formatting check only
./gradlew.bat spotlessCheck

# Architecture tests (included in check, but can run separately)
./gradlew.bat :app:expense-core:test --tests "*ArchitectureTest*"
```

---

## 3. Duplication and source-of-truth review

> [!NOTE]
> At 1–10M DAU with a growing team, duplicated rules drift apart and cause
> inconsistent behavior. This section ensures every rule has ONE authoritative
> definition.
>
> **Design principle**: **DRY** (Don't Repeat Yourself), **Single Source of
> Truth**, **Canonical Data Model**.

### Checklist

- [ ] **Endpoint definitions**: Paths, headers, error codes, event names, and
      schema versions have ONE authoritative definition (e.g., `ApiEndpoints`
      in `libs/ids`)

- [ ] **Version management**: Dependency versions exist ONLY in
      `gradle/libs.versions.toml` (version catalog) or approved central
      configuration. No version numbers in individual `build.gradle.kts` files.

- [ ] **Validation rules**: NOT independently reimplemented in JSON schemas,
      Kotlin DTOs, controllers, and test utilities without an explicit
      documented reason

- [ ] **Authorization rules**: Consistent across REST, GraphQL, background
      jobs, and WebSocket subscriptions

- [ ] **Financial calculations**: ONE implementation with shared property tests
      for rounding, zero-sum, currency, and overflow invariants
  - `ExpenseValidator` is THE validation source
  - `AllocationCalculator` is THE allocation source
  - No duplicate validation in controllers

- [ ] **Retry/idempotency/pagination semantics**: Defined ONCE and reused by
      every adapter

- [ ] **Configuration defaults**: NOT copied across application YAML files
      when they can be inherited from a central safe baseline

- [ ] **Duplication review complete**: A review has inspected:
  - Repeated string literals (URLs, error messages, header names)
  - Repeated mapping functions (entity ↔ DTO conversions)
  - Repeated error translations
  - Repeated persistence conversions
  - Repeated test fixtures and assertion patterns

---

## 4. Configuration hygiene

> [!NOTE]
> Configuration is the #1 source of production incidents. Wrong credentials,
> missing values, or fail-open defaults cause outages that code quality cannot
> prevent.
>
> **Design principle**: **Twelve-Factor App** (Config), **Fail-Fast at
> Startup**, **Principle of Least Privilege**.

### Checklist

- [ ] **Layered configuration**: Separated into:
  1. Safe defaults (in `application.yml`)
  2. Environment-specific overlays (in `application-{profile}.yml`)
  3. Secret injection (via environment variables from secret manager)

- [ ] **No development defaults in production**: Production has NO development
      defaults for:
  - [ ] Passwords and signing keys
  - [ ] Encryption keys
  - [ ] OIDC issuers, audiences, and client secrets
  - [ ] Broker credentials
  - [ ] Database credentials
  - [ ] Email sender/SMTP host

- [ ] **Fail-closed secrets**: Required secrets fail startup when:
  - [ ] Absent → `ApplicationContextException` with clear error message
  - [ ] Weak → rejected by validation (e.g., key length check)
  - [ ] Expired → rejected by validation
  - [ ] Malformed → rejected by validation

- [ ] **Secret management**: Secrets are:
  - [ ] Supplied through deployment secret manager (not environment files)
  - [ ] Never committed to repository
  - [ ] Never logged (even at DEBUG level)
  - [ ] Never exposed in metrics labels
  - [ ] Never embedded in container images

- [ ] **Least-privilege credentials**: Each service has separate:
  - [ ] PostgreSQL database role (cannot access other services' databases)
  - [ ] RabbitMQ user with vhost permissions
  - [ ] Cloud credentials (if applicable)
  - Negative test: Service A credentials → cannot access Service B database

- [ ] **Configuration naming consistency**: Names and meanings are consistent
      across Compose files, CI workflows, deployment manifests, runbooks, and
      local examples

- [ ] **Resource limits configured**: All of the following are explicitly set
      (not relying on defaults):
  - [ ] JVM heap size (`-Xmx`, `-Xms`)
  - [ ] Database connection pool (max connections, min idle, timeout)
  - [ ] Thread pool sizes
  - [ ] Worker concurrency limits
  - [ ] HTTP request timeouts
  - [ ] Retry limits and backoff
  - [ ] Request body size limits
  - [ ] Queue depth limits
  - [ ] Container CPU and memory limits

- [ ] **Finite timeouts everywhere**: All timeout chains are reviewed:
  - [ ] Ingress/load balancer timeout
  - [ ] BFF upstream request timeout
  - [ ] REST client timeout (BFF → services)
  - [ ] Database query timeout
  - [ ] Broker publish timeout
  - [ ] External provider timeout (OIDC, SMTP)
  - [ ] Graceful shutdown timeout
  - Rule: Each layer's timeout < the layer above it

- [ ] **Management endpoints secured**:
  - [ ] Only `/actuator/health/readiness` and `/actuator/health/liveness`
        are publicly accessible
  - [ ] `/actuator/prometheus`, `/actuator/info`, and all other endpoints
        require authentication or network restriction
  - [ ] Consider separate management port (`management.server.port`)

- [ ] **Configuration review process**: Changes are diff-reviewed and
      rendered in the target environment before deployment

---

## 5. API, data, and compatibility

> [!NOTE]
> API compatibility at scale means: clients can upgrade independently, rollback
> is always safe, and no migration takes the database offline.
>
> **Design principle**: **Contract-First Design**, **Expand/Contract Migration**,
> **Backward Compatibility**, **Idempotent Receiver Pattern**.

### Checklist

- [ ] **Contract accuracy**: REST and GraphQL contracts match the running
      implementation for:
  - [ ] Authentication requirements
  - [ ] Authorization rules
  - [ ] Validation rules and error codes
  - [ ] Pagination semantics
  - [ ] HTTP status codes
  - [ ] Response body structure

- [ ] **Error responses**: Structured, catalogued, safe, correlated, and
      stable. Reference: [error-flow.md](../architecture/error-flow.md)
  - Every error has: `type`, `title`, `status`, `detail` (safe), `errorId`,
    `requestId`, `code`, `source`, `component`, `operation`
  - No raw exception messages in public responses

- [ ] **API/event backward compatibility**: Changes are backward compatible
      OR have a versioned rollout plan
  - New fields are added as optional
  - No field removal or renaming without version bump
  - Consumer uses **Tolerant Reader Pattern**

- [ ] **Migration safety**: Database migrations use **Expand/Contract**
      sequencing and are tested against representative data volume (1M+ rows)
  - [ ] Migration duration measured and acceptable
  - [ ] Indexes created without locking (`CREATE INDEX CONCURRENTLY`)
  - [ ] No `ALTER TABLE ... ALTER COLUMN TYPE` without expand/contract

- [ ] **Rollback safety**:
  - [ ] Tested after migration
  - [ ] Tested after partial application rollout (old + new images)
  - [ ] Version-skew test passes (old image + new database schema)

- [ ] **Idempotency**: Keys, replay behavior, duplicate events, retries, and
      conflict handling tested at the public boundary
  - [ ] Expense creation: idempotency key persisted and enforced
  - [ ] Settlement recording: idempotency key added and enforced
  - [ ] Concurrent duplicate: only one succeeds
  - [ ] Altered payload with same key: returns 409 Conflict

- [ ] **Financial reconciliation**: After normal AND failure scenarios:
  - [ ] Balances = SUM(postings) per group/currency
  - [ ] Settlement postings included in balance aggregation
  - [ ] No orphaned audit records or outbox entries
  - [ ] No duplicate postings from retries or concurrent writes

---

## 6. Security and privacy

> [!NOTE]
> A single security vulnerability at 1M DAU is a data breach affecting
> millions of users. This section is non-negotiable.
>
> **Design principle**: **Fail-Closed Security**, **Defense in Depth**,
> **OWASP Top 10**, **Principle of Least Privilege**, **Data Minimization**
> (GDPR Article 5(1)(c)).

### Checklist

- [ ] **OIDC validation**: In every protected service, validate:
  - [ ] Issuer (trusted provider only)
  - [ ] Audience (this service or documented trust model)
  - [ ] Signature algorithm (RS256/ES256, no HMAC in production)
  - [ ] Temporal claims (`exp`, `iat`, `nbf` with bounded clock skew)
  - [ ] Subject (non-empty, non-null)
  - [ ] Scopes (if applicable)

- [ ] **Authorization coverage**: Tested for ALL of the following:
  - [ ] Valid member → 200
  - [ ] Non-member → 403
  - [ ] Removed member → 403
  - [ ] Archived group member → appropriate response
  - [ ] Malformed token → 401
  - [ ] Expired token → 401
  - [ ] Wrong issuer → 401
  - [ ] Wrong audience → 401
  - [ ] Forged/tampered token → 401
  - [ ] Anonymous → 401

- [ ] **Lifecycle flows protected**: Abuse limits and audit evidence for:
  - [ ] Login / magic link
  - [ ] Token refresh
  - [ ] Logout / session invalidation
  - [ ] Invitation flows
  - [ ] Data export
  - [ ] Account deletion

- [ ] **Supply chain security**:
  - [ ] Dependency vulnerability scan passes (OWASP Dependency-Check)
  - [ ] Container image scan passes (Trivy)
  - [ ] SBOM generated and retained (CycloneDX)
  - [ ] License compliance verified (no GPL in MIT project)
  - [ ] Secret scanning passes (Gitleaks/TruffleHog)
  - [ ] Static security analysis passes (Detekt security rules)
  - Exceptions have owner, mitigation, and expiry date

- [ ] **Transport security**:
  - [ ] TLS enforced on all public endpoints
  - [ ] Ingress policy configured (allowed origins, methods)
  - [ ] CORS configured correctly (not `*` in production)
  - [ ] WebSocket origin policy enforced
  - [ ] Rate limits on all public endpoints
  - [ ] Request body size limits
  - [ ] Network segmentation (services not publicly reachable)

- [ ] **Key and secret rotation rehearsed**:
  - [ ] JWT signing key rotation: no session invalidation
  - [ ] Database credential rotation: no downtime
  - [ ] Broker credential rotation: no message loss
  - [ ] OIDC client secret rotation: seamless for users

- [ ] **Privacy compliance**:
  - [ ] Retention policy documented for each data category
  - [ ] Data export provides all user data
  - [ ] Account deletion workflow tested end-to-end
  - [ ] Audit retention separate from deletion
  - [ ] Backup deletion policy defined (GDPR Article 17)
  - [ ] PII not in logs (verified by redaction tests)

- [ ] **Security review**: Threat model and independent security review
      have no unresolved critical or high-risk findings

---

## 7. Reliability, scale, and recovery

> [!NOTE]
> This section proves the system can handle the target traffic and recover
> from failures. Every metric must be **measured, not assumed**.
>
> **Design principle**: **Measure, Don't Guess**, **Chaos Engineering**,
> **SLO-Based Operations**.

### Checklist

- [ ] **Workload model defined**: Documented and agreed:
  - [ ] Active users per tier (1M / 5M / 10M DAU)
  - [ ] Request mix (reads vs. writes, by endpoint)
  - [ ] Peak rate and burst multiplier
  - [ ] Hot-group distribution (Pareto/power law)
  - [ ] Background work volume (recurring schedules, outbox relay)
  - [ ] WebSocket connection count
  - [ ] Growth assumptions and scaling triggers

- [ ] **Load testing passes SLOs**:
  - [ ] 60-minute sustained load at target rate
  - [ ] 5-minute burst at 10x rate
  - [ ] Hot-group concurrency test (many writes to same group)
  - [ ] SLO metrics during load:
    - p50 < 100ms, p95 < 500ms, p99 < 1,000ms
    - Error rate < 0.1%
    - Database connections < 80% pool
    - JVM heap < 80% max
    - Broker queue depth < 1,000

- [ ] **Horizontal scaling verified**:
  - [ ] ≥3 replicas per service with traffic distributed
  - [ ] Cross-replica event/WebSocket fanout works correctly
  - [ ] No sticky-session dependencies

- [ ] **Database recovery**:
  - [ ] PostgreSQL failover: primary failure → replica promotion
  - [ ] Connection recovery: application reconnects after DB restart
  - [ ] Migration locking: concurrent migration attempts are safe
  - [ ] Backup verified: `pg_basebackup` completes successfully
  - [ ] Point-in-time recovery: restore to specific timestamp
  - [ ] Clean restore: reconciliation passes after restore

- [ ] **Broker recovery**:
  - [ ] RabbitMQ node failure: quorum queue survives
  - [ ] Consumer retry: bounded retry with DLQ
  - [ ] Poison message: isolated, doesn't block queue
  - [ ] Duplicate delivery: idempotent consumers
  - [ ] Replay: outbox replay produces no duplicate effects

- [ ] **RPO and RTO measured** (not merely documented):
  - [ ] RPO measured: _____ (target: < 1 minute)
  - [ ] RTO measured: _____ (target: < 5 minutes)

- [ ] **Capacity headroom defined** for:
  - [ ] CPU utilization
  - [ ] Memory utilization
  - [ ] Storage growth rate
  - [ ] Connection pool exhaustion
  - [ ] Queue depth limits
  - [ ] Outbox lag threshold
  - [ ] Database IOPS

---

## 8. Observability and operations

> [!NOTE]
> You can't fix what you can't see. This section ensures you'll know when
> something breaks and how to fix it.
>
> **Design principle**: **USE Method** (Utilization, Saturation, Errors),
> **RED Method** (Rate, Errors, Duration), **Observable System**.

### Checklist

- [ ] **SLO dashboards**: Cover the following for every public service:
  - [ ] Availability (uptime percentage)
  - [ ] Latency (p50, p95, p99)
  - [ ] Error rate (by status code and error code)
  - [ ] Saturation (CPU, memory, connections, queues)
  - [ ] Data freshness (outbox age, sync lag)

- [ ] **Alerts configured and tested**: Each alert has:
  - [ ] Tested owner who receives it
  - [ ] Severity classification
  - [ ] Runbook link
  - [ ] Threshold with hysteresis (avoid flapping)
  - [ ] Escalation policy

- [ ] **Specific alerts verified**:
  - [ ] Outbox age > 30 seconds
  - [ ] Queue lag > 60 seconds
  - [ ] Retry/parking count above threshold
  - [ ] Database pool saturation > 80%
  - [ ] Migration status change (new migration detected)
  - [ ] Sync gaps detected
  - [ ] Reconciliation failure
  - [ ] Error rate spike (> 5x baseline)
  - [ ] Latency spike (p99 > 2,000ms)

- [ ] **Log hygiene**:
  - [ ] Request correlation ID in every log line
  - [ ] No tokens in logs
  - [ ] No secrets in logs
  - [ ] No raw financial amounts in logs
  - [ ] No email addresses in logs (HIGH-06)
  - [ ] No unbounded identifiers in logs
  - [ ] Redaction tests pass

- [ ] **Runbooks executed by non-author**:
  - [ ] Incident response runbook
  - [ ] Rollback runbook
  - [ ] Outage recovery runbook
  - [ ] Data recovery runbook (backup → restore → reconcile)
  - [ ] Security incident runbook
  - [ ] Provider failure runbook (OIDC, SMTP, etc.)

- [ ] **On-call readiness**:
  - [ ] Coverage defined (timezone, rotation)
  - [ ] Escalation contacts verified
  - [ ] Maintenance windows documented
  - [ ] Support ownership clear (who owns what)

---

## 9. Deployment and final approval

> [!NOTE]
> This is the last gate. Everything above must pass before reaching this
> section.
>
> **Design principle**: **Immutable Infrastructure**, **Blue-Green/Canary
> Deployment**, **Rollback-First Thinking**.

### Checklist

- [ ] **Immutable images**: Promoted by digest, NOT mutable tags
  ```text
  ✅ squarewise-accounts@sha256:abc123...
  ❌ squarewise-accounts:latest
  ```

- [ ] **Deployment safety**:
  - [ ] Readiness gates prevent premature traffic
  - [ ] Graceful shutdown (SIGTERM → drain connections → stop)
  - [ ] Rolling compatibility (old + new images serve simultaneously)
  - [ ] Automated rollback path defined and tested

- [ ] **Configuration rendered**: Production configuration reviewed with real
      secret names but without exposing secret values
  ```bash
  # Render config (redacted)
  docker run --env-file .env.redacted squarewise-accounts:sha256... \
    --spring.profiles.active=production \
    --spring.cloud.config.enabled=false \
    env
  ```

- [ ] **Rollout criteria**: Canary or staged rollout thresholds defined:
  - [ ] Error rate threshold for abort
  - [ ] Latency threshold for abort
  - [ ] Health check grace period
  - [ ] Rollback automation trigger

- [ ] **Post-deployment smoke tests**: Cover:
  - [ ] Authentication (login, token refresh)
  - [ ] Authorization (member access, non-member denial)
  - [ ] Financial mutation (create expense, verify postings)
  - [ ] Idempotent replay (retry same idempotency key)
  - [ ] Notification delivery (end-to-end)
  - [ ] Observability (metrics scraped, logs flowing)

- [ ] **Release approval**:

  | Field | Value |
  |---|---|
  | Release version | `________________` |
  | Commit hash | `________________` |
  | Image digests | `________________` |
  | All must-pass items checked | ☐ Yes |
  | Known exceptions | `________________` |
  | Exception owners | `________________` |
  | Exception expiry dates | `________________` |
  | Release manager | `________________` |
  | Independent reviewer | `________________` |
  | **Decision** | ☐ **GO** / ☐ **NO-GO** |
  | Date | `________________` |
  | Signature | `________________` |

---

## Required release artifacts

At minimum, retain the following signed or access-controlled artifacts:

| # | Artifact | How to produce | Retention |
|:---:|---|---|---|
| 1 | Test, static-analysis, contract, and scan reports | `./gradlew.bat check`, `make contracts` | Per-release, 1 year minimum |
| 2 | Dependency lockfile and SBOM | CycloneDX Gradle plugin | Per-release, permanent |
| 3 | Image digests and deployment manifest | Docker build, Compose/K8s manifests | Per-release, permanent |
| 4 | Load, recovery, restore, and rollback reports | k6, custom scripts | Per-release, 1 year |
| 5 | Migration compatibility evidence | Flyway + integration test | Per-release, permanent |
| 6 | Dashboard and alert screenshots or query exports | Grafana export | Per-release, 1 year |
| 7 | Threat model / security review result | External audit | Per-release, permanent |
| 8 | Completed checklist and approval record | This document, signed | Per-release, permanent |
| 9 | Authorization matrix (positive + negative evidence) | Generated from test suite | Per-release, 1 year |

---

## Current Squarewise baseline

> [!WARNING]
> The existing local release record is **not sufficient for promotion**. It
> explicitly records unresolved production backup/restore, alert routing,
> secret rotation, ingress, scanning, rollback, and capacity gates.
>
> This plan should be completed against a **production-like environment**
> before any public launch decision.
>
> See the [production-readiness-audit.md](../reviews/production-readiness-audit.md)
> for the detailed current-state assessment with all critical and
> high-severity findings.
