# Squarewise Production Readiness Audit

## Current implementation status (2026-09-21)

This is the current-state reconciliation. The original finding sections below
are retained as historical evidence of what was audited and must not be read
alone as proof that every original defect still exists.

| Finding group | Status | Evidence / remaining limitation |
|---|---|---|
| CRIT-01, CRIT-02 | **Implemented; scale evidence open** | Settlement postings, reversals, durable idempotency, conflict handling, locking, reconciliation, and replay tests exist. Sustained multi-replica and unknown-outcome evidence is open. |
| CRIT-03, CRIT-04, CRIT-05 | **Implemented in deployed wiring** | Production/staging fail closed to OIDC, durable outbox publishing, and durable JPA adapters. Provider rotation, broker HA, restore, and failover evidence remain open. |
| HIGH-01, HIGH-02, HIGH-03, HIGH-04, HIGH-05, HIGH-06, HIGH-09, HIGH-10, HIGH-11, HIGH-12, HIGH-13, HIGH-14, HIGH-15, HIGH-18, HIGH-19, HIGH-21, HIGH-23 | **Implemented with local/integration evidence** | Authorization, bounds, locking, privacy, redaction, error, transport, and fail-closed configuration checks pass at their documented evidence levels. They do not substitute for target-environment verification. |
| HIGH-07 | **Open** | Staging Compose still shares database and RabbitMQ credentials across services; per-service roles and broker permissions are not implemented. |
| HIGH-08 | **Partial** | Architecture, security, SBOM, dependency, and workflow checks exist; container/image scanning, license policy, signing/provenance, and enforced coverage gates remain incomplete. |
| HIGH-16 | **Partial** | GraphQL depth, complexity, request-size, subscription, queue, and TTL controls exist; durable distributed per-user quotas are not proven. |
| HIGH-17 | **Open** | Deletion requests and profile flags exist, but cross-service deletion, pseudonymization, retention, and verification are incomplete. |
| HIGH-20 | **Open design contract** | Locking exists, but `GroupEntity.revision` is manually managed rather than a JPA `@Version` contract. |
| HIGH-22 | **Open** | Notification rate limiting is process-local and not durable/distributed for multi-replica deployment. |
| HIGH-24 | **Partial** | Production Compose declares replicas and resource limits; rollout, placement, autoscaling/PDB equivalents, and measured multi-replica evidence remain open. |

**Release decision: NO-GO.** Local tests, Compose E2E, CI checks, and
documentation updates do not establish HA, DR, capacity, rotation, rollback,
or independent security-review evidence. No production infrastructure may be
provisioned until the remaining code and configuration work is complete.

> **Last reviewed**: 2026-09-21
> **Target audience**: 1–10 million DAU backend deployment
> **Verdict**: 🔴 **NOT APPROVED for production launch**

## How to read this document

This audit is the **single authoritative decision document** for whether the
Squarewise backend can be shipped to production for 1–10 million daily active
users. It is structured so that any engineer: including an intern with zero
context: can:

1. Understand **what** each finding means in plain English.
2. Understand **why** it is dangerous at scale (with concrete attack/failure
   scenarios).
3. Understand **exactly what code/config** to look at.
4. Understand **what design pattern or principle** to apply when fixing it.
5. Understand **how to verify** the fix is correct.
6. Understand **what "done" looks like** with measurable acceptance criteria.

> [!IMPORTANT]
> Every finding in this document is backed by actual source code inspection.
> Passing local tests, updating task wording, or adding a checklist does not
> change this decision. Re-assessment requires evidence produced in an approved
> production-like environment.

---

## Table of contents

- [Audit decision](#audit-decision)
- [Evidence classification standard](#evidence-classification-standard)
- [Release-blocker matrix](#release-blocker-matrix)
- [Current evidence snapshot](#current-evidence-snapshot)
- [Critical findings (release blockers)](#critical-findings-release-blockers)
  - [CRIT-01: Settlement not in balance model](#crit-01-settlement-not-in-balance-model)
  - [CRIT-02: Idempotency keys ignored](#crit-02-idempotency-keys-ignored)
  - [CRIT-03: Fail-open authentication defaults](#crit-03-fail-open-authentication-defaults)
  - [CRIT-04: In-memory broker fallback in production](#crit-04-in-memory-broker-fallback-in-production)
  - [CRIT-05: In-memory persistence wiring in production](#crit-05-in-memory-persistence-wiring-in-production)
- [High-severity findings](#high-severity-findings)
  - [HIGH-01: Profile IDOR vulnerability](#high-01-profile-idor-vulnerability)
  - [HIGH-02: Actuator endpoints publicly exposed](#high-02-actuator-endpoints-publicly-exposed)
  - [HIGH-03: Unbounded database queries](#high-03-unbounded-database-queries)
  - [HIGH-04: Recurring worker concurrency unsafe](#high-04-recurring-worker-concurrency-unsafe)
  - [HIGH-05: Notification fail-open on preference failure](#high-05-notification-fail-open-on-preference-failure)
  - [HIGH-06: PII in logs](#high-06-pii-in-logs)
  - [HIGH-07: Shared credentials across services](#high-07-shared-credentials-across-services)
  - [HIGH-08: No CI security gates](#high-08-no-ci-security-gates)
  - [HIGH-09: Test fault injection in production](#high-09-test-fault-injection-in-production)
  - [HIGH-10: Financial arithmetic overflow](#high-10-financial-arithmetic-overflow)
  - [HIGH-11: Authorization race condition](#high-11-authorization-race-condition)
  - [HIGH-12: Consumer poison-message loops](#high-12-consumer-poison-message-loops)
  - [HIGH-13: Eager ORM fetching on list paths](#high-13-eager-orm-fetching-on-list-paths)
  - [HIGH-14: Error message disclosure](#high-14-error-message-disclosure)
  - [HIGH-15: BFF downstream URL defaults](#high-15-bff-downstream-url-defaults)
  - [HIGH-16: GraphQL abuse controls missing](#high-16-graphql-abuse-controls-missing)
  - [HIGH-17: Deletion is only a flag](#high-17-deletion-is-only-a-flag)
  - [HIGH-18: Participant IDs not membership-validated](#high-18-participant-ids-not-membership-validated)
  - [HIGH-19: Request size limits missing](#high-19-request-size-limits-missing)
  - [HIGH-20: Optimistic locking contract broken](#high-20-optimistic-locking-contract-broken)
  - [HIGH-21: Subscription membership revocation gap](#high-21-subscription-membership-revocation-gap)
  - [HIGH-22: Unused rate limiter](#high-22-unused-rate-limiter)
  - [HIGH-23: Feature flags default to disabled](#high-23-feature-flags-default-to-disabled)
  - [HIGH-24: No deployment topology controls](#high-24-no-deployment-topology-controls)
- [Ship-blocking gaps (environment-level)](#ship-blocking-gaps-environment-level)
- [Required release artifacts](#required-release-artifacts)
- [Reassessment rule](#reassessment-rule)
- [Related documents](#related-documents)

---

## Audit decision

**Decision: 🔴 DO NOT approve production launch or a 1M+ user capacity claim.**

Squarewise is a substantial, well-structured backend implementation with strong
local contract validation, authentication coverage, integration testing, and
reconciliation evidence. However, the code and configuration contain multiple
**fail-open defaults**, **incomplete security boundaries**, **unbounded data
access paths**, **financial integrity gaps**, and **configuration ambiguities**
that individually and collectively prevent enterprise production-readiness
approval.

### What this means in plain English

> The backend code works correctly in the happy path under local testing
> conditions. But at 1–10M DAU, edge cases become daily events. A race
> condition that happens 0.01% of the time means thousands of affected users
> per day. A missing authorization check means a potential data breach. An
> unbounded query means one power user can take down the entire service.
>
> This audit identifies every such gap and provides a concrete remediation
> path.

### Current classification

> Feature-rich backend under active hardening; suitable for continued
> development and controlled testing. **Not approved for public production or
> a 1M+ user capacity guarantee.**

---

## Evidence classification standard

> [!WARNING]
> Understanding this classification is critical. The most common mistake in
> production readiness reviews is claiming that a lower evidence level proves
> a higher-level property. **It does not.**

| Level | Environment | What it proves | What it does NOT prove |
|:---:|---|---|---|
| 1 | Unit/module tests | Isolated behavior of a single class or function | Anything about real databases, networks, or concurrency |
| 2 | Persistence/integration tests (e.g., Testcontainers) | Behavior with a real database or broker in a test harness | Multi-service interaction, production topology, or capacity |
| 3 | Local Docker E2E (docker-compose) | Services talk to each other on your laptop | Production HA, failover, or how it behaves under real load |
| 4 | Production-like isolated environment | Behavior under a declared topology and workload | Generalization beyond the measured envelope |
| 5 | Target environment (actual production) | Release-specific operational evidence | Permanent guarantee (needs recurring verification) |

### Why this matters at 1–10M DAU

At 1M DAU with ~10 requests/user/day, you're handling **~115 requests/second
sustained** and **~1,000+ requests/second at peak**. At 10M DAU, that's
**~1,150 sustained** and **~10,000+ peak**. At these rates:

- A 0.01% error rate = **1,000–10,000 errors/day**
- A race condition window of 100ms = **dozens of corrupted records/day**
- An unbounded query on a table with 100M rows = **full service outage**
- A missing authorization check = **regulatory and legal liability**

**No lower-level evidence may be relabeled as a higher level.**

---

## Release-blocker matrix

Each row below is **individually sufficient to reject shipment**. All must be
closed before production-readiness approval.

| ID | Severity | Area | Evidence-based blocker | Required closure | Design patterns to apply |
|:---:|:---:|---|---|---|---|
| CRIT-01 | 🔴 Critical | Financial correctness | Settlements are not included in the balance-posting aggregation; `SettlementSuggestionEngine` reads only expense balance postings, so recording a repayment does not change balances or suggestions | **One transactional financial model**: every financial effect (expense, settlement, reversal) must produce ledger postings that feed the same aggregation query. Add durable mutation idempotency, reconciliation scripts, and concurrent retry tests. | **Event Sourcing** / **Ledger Pattern**: all state changes produce immutable entries; **CQRS**: derive balances from the single posting stream |
| CRIT-02 | 🔴 Critical | Data integrity | `JpaExpenseStore.create` accepts `idempotencyKey` but **never persists or compares it**. Idempotency is based only on client-supplied `expenseId`; a retry with a new expense ID creates duplicate financial postings. Cross-group collision: existing expense ID lookup happens before group verification | **Durable idempotency record** with unique constraint on `(groupId, actorId, operationScope, idempotencyKey)`, payload hash, replayed response, conflict semantics, and tests for unknown outcomes, cross-group collisions, concurrent retries, and altered payloads | **Idempotent Receiver** pattern: persist the idempotency key atomically with the side effect; **Optimistic Locking** for conflict detection |
| CRIT-03 | 🔴 Critical | Authentication | `AuthSessionConfiguration.kt` is not profile-restricted and wires `InternalJwtTokenProvider` when no `IdentityProviderPort` bean exists. Constructor supplies **fallback JWT secret, issuer, and audience values**. Production passwordless flow can mint internally signed HMAC tokens instead of using the configured external OIDC provider | Production provider selection must **fail closed**. Remove fallback values. Use `@Profile("test", "local")` for internal JWT provider. Add startup validation that rejects missing OIDC configuration. Add provider-issued token integration test | **Fail-Closed Security** principle; **Dependency Injection** with profile-gated beans; **Strategy Pattern** for identity provider selection |
| CRIT-04 | 🔴 Critical | Messaging | `OutboxRelayDaemon` supplies `InMemoryBroker` via `@ConditionalOnMissingBean(BrokerPublisher::class)`, while RabbitMQ publisher requires `squarewise.outbox.rabbit-enabled=true`. If outbox is enabled without RabbitMQ flag, committed financial events stay **process-local** and are lost on restart | Production must **require** an explicit durable broker publisher and fail startup when absent. `InMemoryBroker` must be `@Profile("test", "local")` only. Add Spring context test for production profile | **Transactional Outbox** pattern requires a durable downstream; **Fail-Fast** principle: detect misconfiguration at startup, not at runtime |
| CRIT-05 | 🔴 Critical | Persistence | `InMemoryNotificationInboxStore` and `InMemoryProfileStore` are registered as Spring `@Service`/`@Component` alongside JPA implementations. `NotificationInbox` constructor defaults to `InMemoryNotificationInboxStore`. A missing bean or context change silently selects process-local storage that loses data on restart | Make in-memory stores `@Profile("test", "local")` only. Remove constructor default arguments that select in-memory stores. Add Spring context tests proving production profile selects JPA implementations | **Port/Adapter** pattern (Hexagonal Architecture): adapters are wired via DI, not constructor defaults; **Liskov Substitution** violation risk: in-memory and durable stores have different durability contracts |

---

## Current evidence snapshot

| Area | Current evidence | Level | Decision |
|---|---|:---:|---|
| Domain implementation | Kotlin services, JPA/Flyway persistence, modular boundaries, local E2E | 2–3 | Continue hardening |
| API contracts | OpenAPI/GraphQL validators, 45 REST operations, 9 GraphQL roots | 2–3 | Not complete: operation gaps remain |
| Authentication | OIDC issuer/audience/signature/expiry/subject negative cases and passwordless journey | 2–3 | Strong local evidence; production operations open |
| Authorization | REST, GraphQL, WebSocket, member/non-member coverage | 2–3 | Complete matrix and production failure evidence open |
| Financial correctness | Minor units, reconciliation, postings, balances, idempotent writes | 2 | Sustained concurrency under real DB open |
| Idempotency/offline replay | Duplicate suppression, conflict replay, cursor recovery | 2–3 | Multi-replica/unknown-outcome evidence open |
| Messaging | Transactional outbox, deduplication, local RabbitMQ recovery | 3 | Production broker HA open |
| Code quality | Modular files, KDoc, Gradle tests, architecture intent | 2 | Enforceable duplication/architecture gates open |
| Configuration | Fail-closed production security values, Compose overlays | 2–3 | Full target config and rotation open |
| CI | Contracts, Gradle tests, coverage, OIDC E2E, workflow validation | 2–3 | Supply-chain/release gates incomplete |
| Capacity | Local k6 and mutation reconciliation | 3 | Production-like 1M validation open |
| Recovery | Local PostgreSQL/RabbitMQ/BFF recovery probes | 3 | Backup/PITR/HA/RTO/RPO open |
| Release | Local release gate and checklist | 2 | No-go until QA-08 evidence |

### Workstation verification findings (2026-09-20)

These checks were rerun and must be resolved before CI readiness:

| Check | Result | Required action |
|---|---|---|
| `python tools/ops/check_security_hygiene.py` | ❌ Failed: 2 test-only bearer token fixture lines in `tests/acceptance/test_qa05.py` | Narrow the scanner classification or move fixtures; rerun and record pass |
| `uvx yamllint` | ❌ Failed: newline checks for `ci-branch.yml`, `ci-master.yml`, `ci-pr.yml`, `_reusable-ci.yml` | Normalize workflow files; revalidate without weakening other lint rules |
| Production Compose rendering | ✅ Expected failure: missing required image variables | Record this as safety behavior; complete validation with injected references |
| Public-surface validation | ✅ Passed: 45 REST ops, 9 GraphQL roots, 50 Bruno requests, 71 assertion-backed | No action required |
| Repository release validator | ⚠️ Partial: tracked assets pass; restore, security-scan, capacity, rollback open | Complete QA-08 evidence |

---

## Critical findings (release blockers)

### CRIT-01: Settlement not in balance model

> **Plain English**: When someone records a repayment ("I paid you back $50"),
> the system saves the settlement record but the **balance calculation doesn't
> know about it**. The "who owes whom" suggestions keep showing the old amounts
> as if the repayment never happened.

**What happens at 1M+ DAU**: Every repayment appears to fail from the user's
perspective. Users will record duplicate repayments. Settlement suggestions
become meaningless. Financial data integrity is destroyed.

**Where to look in code**:
- `SettlementSuggestionEngine`: derives balances from `ExpenseStore.balances()`
- `ExpenseStore.balances()` SQL: aggregates only from `balance_postings` table
- Settlement recording: writes to `settlements` table, which is **not** part
  of the `balance_postings` aggregation
- Settlement reversal: same problem

**Design patterns to apply**:
- **Ledger Pattern** (a.k.a. Double-Entry Bookkeeping): Every financial state
  change: expense creation, settlement recording, settlement reversal: must
  produce immutable ledger entries (balance postings) that are the single source
  of truth for "who owes whom".

  > 📖 **What is the Ledger Pattern?** Think of it like a bank statement. Every
  > transaction (deposit, withdrawal, transfer) creates a line item. The current
  > balance is always the sum of all line items. You never update a "balance"
  > field directly: you add entries, and the balance is derived.

- **Transactional Outbox Pattern**: The settlement posting, audit trail, and
  outbox event must all commit in the same database transaction. If any part
  fails, none of it persists.

  > 📖 **What is the Transactional Outbox?** Instead of publishing an event to
  > RabbitMQ directly (which could fail after the DB commits), you write the
  > event to an "outbox" table in the same transaction as your business data.
  > A background relay process then reads the outbox and publishes to the
  > broker. This guarantees at-least-once delivery without distributed
  > transactions.

**Acceptance criteria**:
1. `SELECT SUM(amount) FROM balance_postings WHERE group_id = ? AND currency = ?`
   returns 0 for every group/currency (zero-sum invariant) after any combination
   of expenses and settlements.
2. Recording a $50 settlement immediately changes the balance query result.
3. Reversal restores the previous balance state.
4. Concurrent settlement + expense on the same group produce correct totals
   under PostgreSQL serializable or row-level locking.
5. Reconciliation script detects any existing data inconsistency.

**Validation commands**:
```bash
# After implementing, run these:
./gradlew.bat :app:expense-core:test --tests "*SettlementPostingTest*"
./gradlew.bat :app:expense-core:test --tests "*BalanceReconciliationTest*"
make acceptance-live  # Full stack E2E
```

---

### CRIT-02: Idempotency keys ignored

> **Plain English**: When a client sends `Idempotency-Key: abc123` with an
> expense creation request, the server accepts the key but **throws it away**.
> If the client retries (which happens often on mobile networks), a second
> expense is created with different financial postings.

**What happens at 1M+ DAU**: Mobile users on flaky networks retry requests
constantly. At 10M DAU, even a 0.1% retry rate means **10,000 duplicate
expenses per day**. Each creates incorrect balance postings. Financial
reconciliation becomes impossible.

**Where to look in code**:
- `JpaExpenseStore.create()`: accepts `idempotencyKey` parameter but never
  persists it to the database or uses it in any comparison
- Expense ID lookup happens before group ownership verification: security risk:
  a matching expense ID from another group could be returned

**Design pattern to apply**:
- **Idempotent Receiver Pattern**: Persist the idempotency key atomically
  with the side effect. On retry, detect the key collision, verify the payload
  matches, and return the original response.

  > 📖 **What is the Idempotent Receiver Pattern?** When a client sends the
  > same request twice (same idempotency key), the server should do the work
  > only once and return the same response both times. This requires:
  > 1. A database table storing `(idempotency_key, group_id, actor_id, operation, payload_hash, response, created_at)`
  > 2. A unique constraint on `(idempotency_key, group_id, actor_id, operation)`
  > 3. On each request: check if key exists → if yes, verify payload hash → return stored response
  > 4. If key doesn't exist: do the work, store the key + response atomically

**Implementation steps**:
1. Create Flyway migration `V{next}__add_idempotency_records.sql`:
   ```sql
   CREATE TABLE idempotency_records (
       id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
       idempotency_key VARCHAR(255) NOT NULL,
       group_id      UUID NOT NULL,
       actor_id      UUID NOT NULL,
       operation     VARCHAR(64) NOT NULL,
       payload_hash  VARCHAR(64) NOT NULL,
       response_body JSONB,
       http_status   INT NOT NULL,
       created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
       expires_at    TIMESTAMPTZ NOT NULL DEFAULT now() + INTERVAL '24 hours',
       CONSTRAINT uq_idempotency UNIQUE (idempotency_key, group_id, actor_id, operation)
   );
   CREATE INDEX idx_idempotency_expires ON idempotency_records (expires_at);
   ```
2. Create `IdempotencyService` that wraps mutation operations.
3. Add cleanup job for expired records (retention policy).
4. Apply to: expense create, expense update, settlement record, settlement
   reverse, group mutations.

**Acceptance criteria**:
1. Duplicate request with same idempotency key returns identical response
   without creating a second expense or posting.
2. Same key with different payload returns HTTP 409 Conflict.
3. Same key in different group returns HTTP 409 Conflict (not another group's
   data).
4. Concurrent duplicate requests (race condition): only one succeeds.
5. After retention period, key is cleaned up and can be reused.
6. Missing idempotency key on mutation returns HTTP 400 (required for financial
   operations).

---

### CRIT-03: Fail-open authentication defaults

> **Plain English**: The authentication system has a "Plan B" built in: if the
> real identity provider (like Keycloak or Auth0) is not configured, it
> silently falls back to minting its own JWT tokens using a hardcoded secret.
> In production, this means the app could be signing its own tokens instead
> of validating externally-issued ones.

**What happens at 1M+ DAU**: If the OIDC provider bean is missing due to a
deployment misconfiguration, the Accounts service will mint its own JWT tokens
using a deterministic fallback secret. Any attacker who discovers this default
secret (which is in the source code) can forge authentication tokens for any
user.

**Where to look in code**:
- `app/accounts/.../AuthSessionConfiguration.kt`: not profile-restricted,
  wires `InternalJwtTokenProvider` when no `IdentityProviderPort` exists
- Constructor supplies fallback values for JWT secret, issuer, and audience

**Design principles to apply**:
- **Fail-Closed Security** (OWASP principle): If a security component cannot
  determine the correct action, it must deny access. Never fall back to a
  weaker security mechanism silently.

  > 📖 **What is Fail-Closed?** Imagine a physical door lock. If the lock
  > breaks, it should stay locked (fail-closed), not swing open (fail-open).
  > In software: if the OIDC provider is missing, the service should refuse
  > to start, not fall back to a weaker mechanism.

- **Strategy Pattern** for identity provider selection: The system should
  select an identity provider implementation through explicit configuration,
  not through "whatever bean happens to be available".

- **Spring `@Profile` annotation**: Use `@Profile("test", "local")` to ensure
  the internal JWT provider is only available in development/testing.

**Implementation steps**:
1. Add `@Profile("test", "local")` to `AuthSessionConfiguration`
2. Remove all constructor default values for secret, issuer, audience
3. Create a production `@Configuration` class that requires
   `IdentityProviderPort` bean and fails startup if absent
4. Add `@ConfigurationProperties` with `@Validated` and `@NotBlank` for all
   OIDC configuration values
5. Add Spring context test: `@ActiveProfiles("production")` without OIDC
   config → startup fails with clear error message
6. Add integration test: passwordless flow → token is signed by external
   provider, not internal signer

**Acceptance criteria**:
1. Production profile without OIDC configuration: application **fails to
   start** with explicit error message naming the missing configuration.
2. Production profile with OIDC configuration: tokens are validated against
   the external provider only.
3. `InternalJwtTokenProvider` bean does not exist in production Spring context.
4. No fallback secret, issuer, or audience value exists in production
   configuration.

---

### CRIT-04: In-memory broker fallback in production

> **Plain English**: The outbox relay system (which publishes financial events
> to RabbitMQ) has a fallback: if no RabbitMQ publisher is configured, it
> creates a fake in-memory publisher. Financial events are "published" to
> local memory and **lost forever when the process restarts**.

**What happens at 1M+ DAU**: If the operator enables the outbox worker
(`squarewise.outbox.enabled=true`) but forgets to set
`squarewise.outbox.rabbit-enabled=true`, all committed financial events
(expense created, settlement recorded, balance changed) are silently
discarded. Notifications are never sent. The BFF never receives change hints.
Users see stale data. Financial audit trails are incomplete.

**Where to look in code**:
- `app/expense-core/.../OutboxRelayDaemon.kt`: `@ConditionalOnMissingBean(BrokerPublisher::class)`
  supplies `InMemoryBroker`
- `squarewise.outbox.rabbit-enabled` property gates the RabbitMQ publisher

**Design principle**: **Transactional Outbox Pattern** requires a **durable**
downstream. An in-memory publisher violates the fundamental guarantee.

> 📖 **Why is in-memory publishing dangerous?** The Transactional Outbox
> pattern works because: (1) business data and outbox event are in the same
> DB transaction, (2) a relay reads from the durable outbox table, (3) the
> relay publishes to a durable broker. If step 3 goes to memory instead of a
> broker, events are lost on any JVM restart, deployment, crash, or OOM kill.
> At 1M+ DAU with multiple replicas being deployed daily, this is guaranteed
> data loss.

**Implementation steps**:
1. Move `InMemoryBroker` to a `@Profile("test", "local")` bean
2. Remove the `@ConditionalOnMissingBean` fallback in `OutboxRelayDaemon`
3. Make `BrokerPublisher` a required bean in the production context:
   ```kotlin
   @Configuration
   @Profile("!test & !local")
   class OutboxBrokerValidation(
       brokerPublisher: BrokerPublisher? // Spring will inject null if missing
   ) {
       init {
           requireNotNull(brokerPublisher) {
               "Production outbox requires a durable BrokerPublisher. " +
               "Set squarewise.outbox.rabbit-enabled=true or provide a " +
               "BrokerPublisher bean."
           }
       }
   }
   ```
4. Add context test for production profile without RabbitMQ → fails to start
5. Add context test for production profile with RabbitMQ → starts successfully

---

### CRIT-05: In-memory persistence wiring in production

> **Plain English**: Several services have two implementations of their data
> storage: a real one (JPA/PostgreSQL) and a fake one (in-memory HashMap).
> Both are registered as Spring beans. Some services even have constructor
> defaults that pick the in-memory version if nothing else is specified.
> This means a misconfiguration could silently switch production to in-memory
> storage that loses all data on restart.

**What happens at 1M+ DAU**: Notification inbox items, user profiles, or other
data could be stored in a HashMap instead of PostgreSQL. Every service restart
(which happens during deployments, scaling events, and crashes) wipes all data.
Users lose their notification history. Profile changes disappear.

**Where to look in code**:
- `InMemoryNotificationInboxStore`: registered as `@Service` alongside
  `JpaNotificationInboxStore`
- `NotificationInbox` constructor: defaults to `InMemoryNotificationInboxStore`
- `InMemoryProfileStore`: registered alongside JPA profile store
- Similar patterns in export, deletion, synchronization services

**Design pattern**: **Hexagonal Architecture (Port/Adapter Pattern)**:

> 📖 **What is Hexagonal Architecture?** Your business logic defines "ports"
> (interfaces like `NotificationInboxStore`). Concrete implementations
> ("adapters" like `JpaNotificationInboxStore` and
> `InMemoryNotificationInboxStore`) are wired by the dependency injection
> framework. The key rule: **production must always wire the durable adapter**.
> The in-memory adapter is for testing only.

**Implementation steps**:
1. Add `@Profile("test", "local")` to every `InMemory*Store` class
2. Remove constructor default arguments that reference in-memory stores
3. Ensure JPA stores are `@Primary` or the only candidates in production
4. Add context tests for each service:
   ```kotlin
   @SpringBootTest
   @ActiveProfiles("production")
   class ProductionContextTest {
       @Autowired
       lateinit var inboxStore: NotificationInboxStore

       @Test
       fun `production context wires JPA inbox store`() {
           assertThat(inboxStore).isInstanceOf(JpaNotificationInboxStore::class.java)
       }
   }
   ```
5. Verify with restart test: write data → restart service → data persists

---

## High-severity findings

### HIGH-01: Profile IDOR vulnerability

> **Plain English**: Any authenticated user can look up any other user's
> profile by guessing their account UUID. There's no check that the caller
> shares a group with that user or has any legitimate reason to see their
> profile.

**What happens at 1M+ DAU**: An attacker with a valid account can enumerate
UUIDs and harvest display names, timezones, and currencies of all users.
This is an IDOR (Insecure Direct Object Reference) vulnerability: one of the
OWASP Top 10.

> 📖 **What is IDOR?** Insecure Direct Object Reference means the API uses a
> user-controlled identifier (like a UUID) to access data without verifying
> that the requesting user is authorized to access that specific object. It's
> like a hotel where knowing a room number lets you open the door: there's
> no key check.

**Where to look in code**:
- `ProfileController`: `getProfile(accountId)` and batch profile endpoints
- Only check: is the caller authenticated? (global filter)
- Missing check: does the caller share a group with `accountId`?

**Design pattern**: **Authorization Boundary Pattern**: check authorization
at the controller boundary before delegating to the service layer. Use the
**Principle of Least Privilege**: return only the minimum fields needed.

**Implementation steps**:
1. Define the authorization contract:
   - Same user → full profile
   - Shared group member → minimal profile (display name only)
   - Service-to-service (BFF) → explicit service credential or documented
     exception
   - No relationship → HTTP 403
2. Add membership lookup to the profile endpoint
3. Create separate response DTOs for full vs. minimal profile
4. Add negative tests: cross-tenant lookup returns 403
5. Add batch endpoint authorization (every ID in the batch must be authorized)

---

### HIGH-02: Actuator endpoints publicly exposed

> **Plain English**: Spring Boot Actuator exposes operational endpoints like
> `/actuator/health`, `/actuator/info`, `/actuator/prometheus` (metrics). All
> four services currently allow unauthenticated access to all `/actuator/**`
> paths. This exposes internal system metrics, version information, and
> configuration metadata to anyone who can reach the application port.

**What happens at 1M+ DAU**: An attacker can enumerate internal metrics
(connection pool sizes, queue depths, error rates), discover technology
versions (for targeted exploits), and use health endpoints to map the internal
service topology.

**Where to look in code**:
- Security configuration in each service: `.requestMatchers("/actuator/**").permitAll()`
- `application.yml`: `management.endpoints.web.exposure.include: health,info,prometheus`

**Design pattern**: **Defense in Depth**: security controls at multiple layers.

**Implementation steps**:
1. Expose only `/actuator/health/readiness` and `/actuator/health/liveness`
   publicly (for load balancer probes)
2. Require authentication or network restriction for `/actuator/prometheus`,
   `/actuator/info`, and all other actuator paths
3. Consider a separate management port (`management.server.port=9090`) that
   is not exposed through the public ingress
4. Add integration tests: anonymous request to `/actuator/prometheus` → 401/403
5. Add integration tests: anonymous request to `/actuator/health/readiness` → 200

---

### HIGH-03: Unbounded database queries

> **Plain English**: Several database queries load ALL matching rows into
> memory and then apply pagination/filtering in Java code. For a group with
> 10,000 expenses or a user with 50,000 notification inbox items, this means
> loading millions of rows into the JVM heap.

**What happens at 1M+ DAU**: One power user in a large group triggers a query
that loads 100,000+ rows into memory, causing garbage collection pauses that
affect all concurrent requests. Repeated hits cause OutOfMemoryError and full
service crash.

**Where to look in code**:
- `JpaExpenseStore.list()`: loads all matching expenses, applies `take(limit)`
  in Kotlin after the query. The cursor parameter is not applied to the SQL.
- `JpaNotificationInboxStore`: loads and sorts the complete subject history
  before slicing a page
- Group memberships, recurring schedules, balance postings, search, export:
  all return unconstrained `List` results
- `outbox.snapshot()`: reads ALL rows (must remain admin-only)

**Design pattern**: **Keyset Pagination** (cursor-based pagination):

> 📖 **What is Keyset Pagination?** Instead of `OFFSET` + `LIMIT` (which gets
> slower as offset increases), you use the last row's sort key as a cursor:
> ```sql
> SELECT * FROM expenses
> WHERE group_id = ? AND (created_at, id) < (?, ?)
> ORDER BY created_at DESC, id DESC
> LIMIT 20
> ```
> This is O(1) regardless of how deep into the result set you are, because
> PostgreSQL uses the index to jump directly to the right position.

**Implementation steps**:
1. Define a maximum page size (e.g., 100 items) as a shared constant
2. Implement keyset pagination for every list endpoint:
   - Expenses: cursor = `(createdAt, expenseId)`
   - Notifications: cursor = `(timestamp, notificationId)`
   - Memberships: cursor = `(joinedAt, memberId)`
3. Add `Pageable` to JPA repository methods with `LIMIT` in the query
4. Create indexes for each cursor combination
5. Add load tests: 10,000 items in a group → list request returns in <100ms
   with bounded heap usage
6. Restrict `outbox.snapshot()` to admin-only with explicit documentation

---

### HIGH-04: Recurring worker concurrency unsafe

> **Plain English**: The background job that creates expenses from recurring
> schedules selects due schedules as an unconstrained list without locking or
> claiming them. If two worker instances run simultaneously, they process the
> same schedules, potentially creating duplicate expenses and notifications.

**What happens at 1M+ DAU**: With multiple replicas (minimum 3 at scale), the
recurring worker runs on every replica. Without locking, the same schedule is
processed 3 times, creating 3 identical expenses. `maxCatchUpOccurrences` has
no positive upper bound: a misconfiguration could turn one poll into
thousands of operations.

**Design pattern**: **Distributed Lock / Claim Pattern**:

> 📖 **What is the Claim Pattern?** When multiple workers compete for work:
> 1. Worker selects unclaimed items: `SELECT ... WHERE claimed_by IS NULL LIMIT 10 FOR UPDATE SKIP LOCKED`
> 2. Worker claims them: `UPDATE SET claimed_by = ?, claimed_at = ?`
> 3. Worker processes claimed items
> 4. Worker marks completion: `UPDATE SET status = 'DONE'`
>
> `FOR UPDATE SKIP LOCKED` is a PostgreSQL feature that locks selected rows
> and tells other transactions to skip already-locked rows instead of waiting.
> This gives you efficient, non-blocking work distribution.

**Implementation steps**:
1. Add `claimed_by` and `claimed_at` columns to recurring schedules
2. Use `SELECT ... FOR UPDATE SKIP LOCKED` with `LIMIT` for bounded claims
3. Add startup validation for `maxCatchUpOccurrences` (positive upper bound)
4. Add occurrence-level idempotency keys
5. Add multi-worker test: 3 concurrent workers → each schedule processed
   exactly once
6. Add test: stuck claims are released after timeout

---

### HIGH-05: Notification fail-open on preference failure

> **Plain English**: When the notification system can't look up a user's email
> preferences (e.g., database is down), it defaults to "email is enabled" and
> sends the notification anyway. If no email address is found, it **invents**
> a `@squarewise.local` address. If sending fails, the error is swallowed.

**What happens at 1M+ DAU**: A user who opted out of email notifications
starts receiving them during a database outage. Users who haven't provided
email addresses get notifications "sent" to fake addresses, creating a false
delivery record. Failed deliveries are silently acknowledged, losing the
notification forever.

**Design principle**: **Fail-Closed for Privacy**: when you can't determine
user consent, assume they did NOT consent.

**Implementation steps**:
1. Preference lookup failure → skip delivery and park for retry (do NOT
   default to enabled)
2. Missing email address → reject or park, never synthesize
3. Delivery failure → durable retry with exponential backoff and parking/DLQ
4. Separate delivery acknowledgment from inbox processing
5. Add tests: preference DB down → notification parked, not sent
6. Add tests: missing email → notification parked with clear reason
7. Add tests: SMTP failure → notification retried, not acknowledged

---

### HIGH-06: PII in logs

> **Plain English**: Notification and email components log full email
> addresses, including on success, preference lookup, validation, retry, and
> failure paths. Some log lines also include raw exception messages that may
> contain database queries, credentials, or internal state.

**What happens at 1M+ DAU**: Log aggregation systems (ELK, CloudWatch, etc.)
contain millions of email addresses in plaintext. A log system breach exposes
all user email addresses. This violates GDPR Article 32 (security of
processing) and most privacy regulations.

**Design principle**: **Data Minimization** (GDPR Article 5(1)(c)) and
**Log Redaction**.

> 📖 **What is Log Redaction?** Replace sensitive values with opaque
> identifiers in logs:
> - Instead of: `Sending email to user@example.com`
> - Use: `Sending email to account=a1b2c3d4 subject=notif-5678`
>
> The actual email can be looked up from the account ID if needed for
> debugging, but it's not stored in logs.

**Implementation steps**:
1. Create a `LogSafeIdentifier` utility that masks PII
2. Replace all email address logging with account/subject identifiers
3. Replace all raw exception message logging with classified error codes
4. Add redaction tests: grep log output for email patterns → zero matches
5. Add to CI: log redaction scanner as a quality gate

---

### HIGH-07: Shared credentials across services

> **Plain English**: The staging deployment uses the **same PostgreSQL
> username/password** for all three services (Accounts, Expense Core,
> Notifications) and the **same RabbitMQ credentials** for all services.

**What happens at 1M+ DAU**: If one service is compromised (e.g., SQL
injection in Expense Core), the attacker's credentials work for ALL databases.
They can read user profiles from Accounts DB, notification preferences from
Notifications DB, and modify any financial data. This violates the
**Principle of Least Privilege**.

**Design principle**: **Least Privilege** and **Blast Radius Reduction**.

**Implementation steps**:
1. Create separate PostgreSQL roles per service:
   ```sql
   CREATE ROLE squarewise_accounts WITH LOGIN PASSWORD '...';
   CREATE ROLE squarewise_expense WITH LOGIN PASSWORD '...';
   CREATE ROLE squarewise_notifications WITH LOGIN PASSWORD '...';
   GRANT ALL ON DATABASE accounts_db TO squarewise_accounts;
   REVOKE ALL ON DATABASE accounts_db FROM squarewise_expense;
   -- etc.
   ```
2. Create separate RabbitMQ users with vhost permissions
3. Add negative test: Expense Core credentials → cannot access Accounts DB
4. Document rotation procedure for each credential pair
5. Update staging overlay with separate credential injection

---

### HIGH-08: No CI security gates

> **Plain English**: The CI pipeline runs tests and formatting checks but does
> not enforce static analysis (e.g., Detekt/ktlint rules), architecture
> boundary checks, dependency vulnerability scanning (e.g., OWASP Dependency
> Check), secret scanning, license compliance, SBOM generation, or minimum
> test coverage thresholds. JaCoCo generates reports but no threshold gate
> fails the build.

**What happens at 1M+ DAU**: A developer accidentally adds a dependency with
a known CVE. Nobody notices until the vulnerability is exploited. A developer
commits a credential in a config file. Nobody notices until it's in production.

**Design principle**: **Shift-Left Security**: catch security issues as early
as possible in the development lifecycle.

**Implementation steps** (in CI workflow YAML):
1. **Detekt** for Kotlin static analysis with custom rule set
2. **OWASP Dependency-Check** or **Dependabot** for vulnerability scanning
3. **Trivy** for container image scanning
4. **Gitleaks** or **TruffleHog** for secret scanning
5. **CycloneDX** Gradle plugin for SBOM generation
6. **JaCoCo** with enforced thresholds:
   ```kotlin
   // build.gradle.kts
   jacocoTestCoverageVerification {
       violationRules {
           rule {
               limit { minimum = "0.80".toBigDecimal() }
           }
       }
   }
   ```
7. **ArchUnit** tests for architecture boundaries (already partially present)
8. License policy enforcement (reject GPL in MIT project)

---

### HIGH-09: Test fault injection in production

> **Plain English**: Production application code directly honors the
> `X-Acceptance-Fault` request header, which can force rollback, fanout
> errors, or other deliberate failure behavior through normal endpoints.

**What happens at 1M+ DAU**: An attacker who discovers this header can cause
arbitrary failures in production. Even with authentication required, any
authenticated user could disrupt the service for all users.

**Implementation**:
1. Move fault injection to `@Profile("test")` only beans
2. Add integration test: production profile + `X-Acceptance-Fault` header →
   header is ignored, normal processing occurs
3. Remove any fault injection code from production artifacts

---

### HIGH-10: Financial arithmetic overflow

> **Plain English**: Payer totals and settlement balance aggregation use
> regular Kotlin `Long` addition/subtraction without overflow checking.
> Extreme but valid inputs can cause numeric wraparound (e.g., a very large
> expense amount wraps from positive to negative).

**What happens at 1M+ DAU**: A malicious or buggy client sends an expense
with amount `Long.MAX_VALUE - 1`. Adding any payer amount causes overflow.
The system records a negative total instead of rejecting the request.

**Design pattern**: **Bounded Domain Types**: use types that enforce valid
ranges at construction time.

**Implementation steps**:
1. Use `Math.addExact()` / `Math.subtractExact()` (throws
   `ArithmeticException` on overflow) instead of `+` / `-`
2. Define maximum valid amount at the domain level (e.g., 10 billion minor
   units = $100M)
3. Validate at API boundary before any arithmetic
4. Remove duplicate validation in `ExpenseController` (use `ExpenseValidator`
   as single source of truth: **DRY principle**)
5. Add property tests for boundary values: `Long.MAX_VALUE`, `Long.MIN_VALUE`,
   mixed positive/negative allocations

---

### HIGH-11: Authorization race condition

> **Plain English**: When modifying a group, the system checks membership
> authorization BEFORE acquiring the row lock on the group. Between the
> authorization check and the lock acquisition, another request could remove
> the member. The removed member's request then proceeds with the lock,
> modifying the group they no longer belong to.

**What happens at 1M+ DAU**: A user is removed from a group. Their in-flight
request (which started before removal) still modifies the group. At scale,
these race windows are hit regularly.

**Design pattern**: **Authorization Under Lock**: revalidate authorization
within the same transaction that holds the resource lock.

> 📖 **Why revalidate?** Think of it like a concert venue. The security guard
> checks your ticket at the entrance (authorization check). But between the
> check and entering the venue, your ticket could be revoked. A second check
> inside the venue (under lock) catches this.

**Implementation steps**:
1. Move `checkActiveMembership` inside the transaction after `SELECT ... FOR UPDATE`
2. Or: use a single query that both locks the group row AND verifies membership
3. Add race condition tests: concurrent removal + mutation → mutation rejected
4. Cover all mutation types: rename, archive, invite, placeholder, expense,
   settlement, recurring schedule

---

### HIGH-12: Consumer poison-message loops

> **Plain English**: Both notification Rabbit listeners requeue every
> unexpected runtime failure indefinitely. Only malformed envelopes are
> rejected. A database outage or schema error causes the same message to be
> reprocessed thousands of times per second.

**What happens at 1M+ DAU**: One bad message blocks the entire queue. Healthy
messages pile up behind it. The consumer generates massive load on the
database/broker from constant retry. Notification delivery halts entirely.

**Design pattern**: **Dead Letter Exchange (DLQ)** with bounded retry:

> 📖 **What is a Dead Letter Queue?** A "parking lot" for messages that can't
> be processed. After N retries, the message is moved to a DLQ for manual
> inspection rather than blocking the main queue. Configure:
> 1. Bounded redelivery count (e.g., 3 attempts)
> 2. Exponential backoff between retries
> 3. Dead letter exchange after max retries
> 4. Alerting on DLQ depth
> 5. Manual replay/discard tooling for DLQ messages

---

### HIGH-13: Eager ORM fetching on list paths

> **Plain English**: `ExpenseEntity` maps payers and allocations as
> `FetchType.EAGER`. When listing 20 expenses, the ORM loads ALL payer and
> allocation records for ALL 20 expenses. With 5 payers and 5 allocations
> each, that's 200 extra queries (N+1 problem) or a massive JOIN.

**What happens at 1M+ DAU**: A group with 1,000 expenses and 10
payers/allocations each: listing page of 50 = 1,000 extra SQL queries.
Response time goes from 50ms to 5 seconds. Database connection pool exhausts.

**Design pattern**: **Projection** and **Fetch Plan**:

> 📖 **What is the N+1 Query Problem?** When loading a parent entity triggers
> loading each child entity in a separate query. For N parents, you get 1
> parent query + N child queries. Fix: use `@BatchSize`, `JOIN FETCH` in
> JPQL, or separate projection DTOs for list views.

**Implementation steps**:
1. Change `@ManyToOne(fetch = FetchType.LAZY)` for payers and allocations
2. For list endpoints: use a JPQL projection DTO that doesn't include
   child collections
3. For detail endpoints: use `JOIN FETCH` to load children in one query
4. Add query-count assertions in tests
5. Load test with realistic cardinalities (20 payers, 20 allocations per
   expense)

---

### HIGH-14: Error message disclosure

> **Plain English**: `GlobalErrorHandler` copies `ApplicationException.message`
> directly into the public API response (both title and detail). Several
> controllers construct errors from raw exception messages. This can expose
> SQL queries, parser errors, identity provider details, or internal
> implementation information to clients.

**Implementation**: Use the error catalog (ERR-01 through ERR-12 workstream).
Every public error must use a stable code, safe title, and redacted detail.
Internal causes go only to structured server-side logs with request ID
correlation. See [error-flow.md](../architecture/error-flow.md).

---

### HIGH-15: BFF downstream URL defaults

> **Plain English**: BFF configuration defaults downstream service URLs to
> `http://localhost:8081` and `http://localhost:8082`. If production
> configuration is missing, the BFF starts and routes requests to localhost.

**Implementation**: Make URLs required with `@NotBlank` validation. Use
`@ConfigurationProperties(prefix = "squarewise.bff.upstream")` with
`@Validated`. Production startup fails on missing/malformed URLs. Add context
test.

---

### HIGH-16: GraphQL abuse controls missing

> **Plain English**: No query depth limit, complexity limit, request body
> size limit, subscription count limit, message rate limit, or per-identity
> quota. An authenticated user can send arbitrarily complex queries or open
> unlimited WebSocket subscriptions.

**What happens at 1M+ DAU**: A single malicious user sends a deeply nested
query that explodes into millions of resolver calls, consuming all CPU and
memory. Or opens 10,000 WebSocket subscriptions, exhausting server resources.

**Implementation steps**:
1. Add query depth limit: `spring.graphql.graphiql.enabled=false` in
   production; max depth = 10
2. Add query complexity scoring with `graphql-java` instrumentation
3. Add request body size limit at the server level
4. Add per-user subscription limit (e.g., 50 concurrent subscriptions)
5. Add per-user request rate limit
6. Add adversarial tests for each limit

---

### HIGH-17: Deletion is only a flag

> **Plain English**: Account deletion sets a status flag and marks the
> profile, but does not actually delete or pseudonymize data across services.

**Implementation**: Define retention policy. Implement cross-service deletion
workflow. Pseudonymize financial attribution. Test end-to-end with audit
verification. See GDPR Article 17 (Right to Erasure) requirements.

---

### HIGH-18: Participant IDs not membership-validated

> **Plain English**: Expense creation verifies the CALLER is a group member
> but does not verify that every PAYER and ALLOCATION participant is also a
> current group member. Arbitrary UUIDs can be written into ledger postings.

**Implementation**: Validate all participant UUIDs against active
membership/placeholder state within the mutation transaction. All allocation
modes must reject duplicate IDs. Add tests for archived members, removed
members, and non-existent UUIDs.

---

### HIGH-19: Request size limits missing

> **Plain English**: No limits on payer/allocation list sizes, category
> lengths, numeric magnitudes, request body sizes, header sizes, or
> idempotency key lengths.

**Implementation**: Define shared constants for all limits. Enforce at
validation layer and server configuration. Add rejection tests with oversized
payloads. See **Robustness Principle** (be conservative in what you send,
liberal in what you accept: but NOT for security-critical inputs).

---

### HIGH-20: Optimistic locking contract broken

> **Plain English**: `GroupEntity.revision` is documented as supporting
> optimistic locking, but it's not annotated with JPA `@Version`. The
> revision field is manually incremented but doesn't provide JPA's automatic
> conflict detection.

**Implementation**: Either add `@Version` to `revision` and handle
`OptimisticLockException` throughout, or document/enforce row-lock ownership
for every write path. Add concurrent rename/archive/member/expense tests
asserting monotonic revisions.

---

### HIGH-21: Subscription membership revocation gap

> **Plain English**: GraphQL subscription setup checks group access once, but
> the in-memory fanout doesn't invalidate subscriptions when a member is
> removed. A removed member receives updates until their subscription TTL
> expires.

**Implementation**: On membership removal, find and close the member's active
subscriptions. Or: check membership on every delivery. Add test: remove member
→ subsequent group update is NOT delivered to removed member's subscription.

---

### HIGH-22: Unused rate limiter

> **Plain English**: A `DeliveryRateLimiter` class exists but is not wired
> into the actual notification delivery path. The rate limiting policy is
> effectively unenforced.

**Implementation**: Either integrate the rate limiter into the delivery path
with durable/distributed state, or remove the unused class. Add enforcement
and expiry tests. At 1M+ DAU, process-local rate limiting is insufficient;
use Redis or database-backed rate limiting.

---

### HIGH-23: Feature flags default to disabled

> **Plain English**: `squarewise.outbox.enabled` and
> `squarewise.auth-email-outbox.enabled` default to `false`. A production
> service can start successfully while silently not publishing events or
> sending auth emails.

**Implementation**: Required capabilities must fail startup when disabled in
production profiles. Use `@ConditionalOnProperty` with explicit `matchIfMissing = false`
and a production configuration validator that requires these flags.

---

### HIGH-24: No deployment topology controls

> **Plain English**: The production Compose file has no service replicas,
> CPU/memory limits, rolling update strategy, or placement constraints. The
> capacity model assumes ≥3 replicas with autoscaling, but nothing enforces
> this.

**Implementation**: Add `deploy:` blocks with `replicas`, `resources`, and
`update_config` to production Compose. For Kubernetes: define HPA manifests,
PodDisruptionBudgets, and resource requests/limits. Document the minimum
viable production topology.

---

## Ship-blocking gaps (environment-level)

These gaps require a **production-like environment**, not code fixes:

### Capacity and scale
- [ ] The 1M-user model is a starting workload, not measured proof
- [ ] A local mixed run exceeded Expense Core latency thresholds under saturation
- [ ] No sustained production-like soak with representative data volume
- [ ] No proven ≥3 replica deployment under stated workload
- [ ] Hot-group contention, connection saturation, DB I/O, JVM/GC, broker lag,
      and outbox age require measured limits

### Availability and recovery
- [ ] PostgreSQL HA/failover not proven in target topology
- [ ] Backup, PITR, clean restore, RPO/RTO not proven
- [ ] RabbitMQ quorum/cluster behavior, node loss, volume loss, replay not proven
- [ ] Recovery runbooks and alert routing require independent execution

### Security and operations
- [ ] Dependency, container, image, SBOM, license, and signing gates incomplete
- [ ] Secret/key rotation not rehearsed without interruption
- [ ] Ingress, TLS, WAF/rate limiting, WebSocket origin, network segmentation
      not target-environment evidence
- [ ] Threat model and independent security review closure not demonstrated

### Deployment compatibility
- [ ] Expand/contract migration compatibility not proven at representative scale
- [ ] Old/new image coexistence and rollback after partial rollout not proven
- [ ] Immutable artifact promotion, provenance, signing, and rollback automation
      require release evidence

---

## Required release artifacts

Before any production launch, retain the following signed or access-controlled
artifacts:

| Artifact | Purpose | Tool/Command |
|---|---|---|
| Test/static-analysis/contract reports | Code quality evidence | `./gradlew.bat check`, `make contracts` |
| Dependency lockfile and SBOM | Supply chain transparency | CycloneDX Gradle plugin |
| Image digests and deployment manifest | Immutable release identity | `docker inspect --format='{{.Id}}'` |
| Load/soak/burst reports with saturation metrics | Capacity evidence | k6, Gatling |
| DB backup/PITR/restore report with reconciliation | Recovery evidence | `pg_basebackup`, reconciliation script |
| Broker failure/replay/parking report | Messaging durability evidence | RabbitMQ admin + test suite |
| Secret/key rotation report | Operational security evidence | Rotation runbook execution |
| Alert routing and runbook rehearsal report | Incident readiness evidence | PagerDuty/Opsgenie test |
| Migration compatibility evidence | Deployment safety | Flyway + version-skew test |
| Dashboard and alert screenshots | Observability evidence | Grafana export |
| Threat model / security review result | Security evidence | External security audit |
| Completed checklist and approval record | Release decision | Signed by release manager |
| Authorization matrix (positive + negative) | Security evidence | Generated from test suite |

---

## Reassessment rule

Reassess only after the evidence is produced in an approved production-like
environment. **Passing local tests, updating task wording, or adding a
checklist does not change this decision.**

To request reassessment:
1. Complete all CRIT-* findings
2. Complete all HIGH-* findings or obtain explicit waiver with owner and expiry
3. Close all environment-level gaps in a production-like topology
4. Record all evidence with the [required evidence format](../implementation/production-readiness-roadmap.md#required-evidence-format)
5. Submit to release manager for go/no-go decision

---

## Related documents

| Document | Purpose |
|---|---|
| [production-readiness-roadmap.md](../implementation/production-readiness-roadmap.md) | Phase-by-phase execution plan |
| [production-readiness-tracker.md](../tasks/production-readiness-tracker.md) | Workstream status and ownership |
| [production-readiness-plan.md](../operations/production-readiness-plan.md) | Pre-production approval checklist |
| [production-validation.md](../quality/production-validation.md) | QA-08 evidence tracker |
| [QA-08.md](../tasks/details/QA-08.md) | Production-like evidence task |
| [programming-principles.md](../quality/programming-principles.md) | Code quality standards |
| [coding-guidelines.md](../quality/coding-guidelines.md) | Implementation conventions |
| [error-flow.md](../architecture/error-flow.md) | Error handling architecture |
| [pagination.md](../architecture/pagination.md) | Pagination design |
| [testing-strategy.md](../quality/testing-strategy.md) | Test classification and coverage |
