# ERRC-01: Design six-digit domain error-code migration

## Opening & Umbrella Milestone

`ERRC-01` serves as the opening architecture baseline and umbrella milestone for Squarewise's six-digit domain error-code migration. It reconciles the current flat 12-error taxonomy (`ERR-01` through `ERR-12`) with the immutable six-digit `DM-L-C-EE` standard, establishing the foundational design patterns, common library responsibilities, and structured phased execution plan spanning **ERRC-02 through ERRC-31**.

## Objective

Produce a comprehensive, documentation-only, implementation-ready architectural decision set and migration plan for replacing flat error identifiers with the six-digit `DM-L-C-EE` standard. The plan fits Squarewise's modular applications, shared technical libraries, public REST/GraphQL/event contracts, security controls, error observability, and 10M+-DAU target without claiming capacity evidence that has not been measured.

## Dependencies and Current-State Constraints

- `ERR-12` and `SEC-02` are complete.
- The current symbolic public error vocabulary and ERR-01 through ERR-12 work are shipped repository state, not a blank slate.
- `ERRC-01` establishes the opening architectural rules and specifications. Follow-on implementation tasks `ERRC-02` through `ERRC-31` are registered and tracked in the task registry and board.
- Strict SOLID file separation, enterprise design patterns, and full static typing are mandatory for all implementation phases.

## Enterprise Design Patterns & Architectural Foundations

The migration strictly adheres to proven enterprise software engineering principles:

1. **Value Object Pattern (`ErrorCode`)**:
   - Inlined, zero-allocation `@JvmInline value class ErrorCode(val value: String)` enforcing `^[1-9]{4}(0[1-9]|[1-9][0-9])$` at construction time. Provides constant-time decomposition into domain, module, layer, category, and sequence.
2. **Ports & Adapters (Hexagonal Architecture)**:
   - Core domain models and business logic never depend on web, messaging, or database error representations. Transport adapters translate domain exceptions at the infrastructure boundary into RFC 9457 Problem Details (REST), GraphQL Extensions (BFF), or Dead-Letter Envelopes (RabbitMQ).
3. **Exception Shielding Pattern**:
   - Internal stack traces, raw error messages, database exceptions (e.g. `PSQLException`), and framework diagnostics are strictly shielded from public responses. `Throwable.message` is never published to client JSON responses.
4. **Constrained Strategy / Sealed Family Override**:
   - Leaf exceptions have baked-in static catalog definitions. Reusable exceptions requiring overrides must use sealed definition families, preventing callers from injecting unvetted or arbitrary error definitions.
5. **Anti-Corruption Layer (ACL)**:
   - The GraphQL BFF preserves upstream REST Problem Details byte-for-byte in GraphQL `extensions`, refusing to synthesize random request IDs or remap errors purely by HTTP status code.
6. **Transactional Outbox & Idempotent Consumer Patterns**:
   - Outbox processing failures never mutate domain events. Event consumers distinguish transient failures (retry with exponential backoff) from poison pills (dead-letter immediately) to prevent queue blocking.
7. **Strangler Fig & Tolerant Reader Patterns**:
   - Additive API v1 migration: existing clients continue consuming the legacy `code` property while modern clients can opt-in to `numericCode` and `errorName`. Legacy code infrastructure is retired only after a formal compatibility window.
8. **Strict SOLID File Separation**:
   - Every class, entity, repository, service, DTO, value object, and enum resides in its own isolated file. Never bundle multiple top-level types into a single `.kt` file.

## Common Libraries & Platform Integration

The architecture leverages Squarewise's shared technical libraries under `libs/`:

- **[`libs/errors`](../../libs/errors/)**:
  - Houses `ErrorCode` value class, metadata enums (`ErrorDomain`, `ErrorLayer`, `ErrorCategory`, `ErrorSeverity`, `RetryPolicy`), `ErrorDefinition` interface, static domain catalogs, governed `SquarewiseException`, web Problem Details advice, and async error boundary templates.
- **[`libs/ids`](../../libs/ids/)**:
  - Centralizes API endpoint constants (`ApiEndpoints`) and identifier format validators.
- **[`libs/observability`](../../libs/observability/)**:
  - Structured error logging (`ErrorLogger`), Micrometer metrics recording with bounded cardinality (`squarewise_errors_total`), and OpenTelemetry span error enrichment.
- **[`libs/security`](../../libs/security/)**:
  - Structured security entry points and access-denied handlers for both Servlet and Reactive stacks, preventing user enumeration via uniform 401/404 handling.
- **[`libs/db`](../../libs/db/)**:
  - Database constraint exception translation, read/write pool routing, and transaction rollback protection.
- **[`libs/test-support`](../../libs/test-support/)**:
  - Shared testing fixtures, mock problem builders, and adversarial leak test suites.

## Phased Implementation Roadmap (ERRC-02 through ERRC-31)

`ERRC-01` serves as the opening gateway to five structured execution phases:

### Phase 0: Governance & Frozen Evidence
- [`ERRC-02: Freeze audit and baseline behavior`](ERRC-02.md) — Characterization snapshots of all 128 throw sites and baseline responses.
- [`ERRC-03: Freeze registries and catalog schema`](ERRC-03.md) — Lock `domains.yaml` and create `error-catalog.schema.json`.
- [`ERRC-04: Allocate and review the complete error catalog`](ERRC-04.md) — Complete authoritative allocations in `error-catalog.yaml` and retire `ERR-12`.

### Phase 1: Contract-First Compatibility
- [`ERRC-05: Define the additive Problem Details contract`](ERRC-05.md) — Update RFC 9457 JSON schema with optional `numericCode` and `errorName`.
- [`ERRC-06: Reconcile all REST OpenAPI contracts`](ERRC-06.md) — Update Accounts, Expense Core, and Notifications OpenAPI specs with non-2xx responses.
- [`ERRC-07: Define GraphQL and WebSocket error contracts`](ERRC-07.md) — GraphQL `extensions` schema and WebSocket custom close codes.
- [`ERRC-08: Define messaging and background error records`](ERRC-08.md) — Dead-letter and scheduler execution failure contracts.
- [`ERRC-09: Upgrade contract validation and breaking-change gates`](ERRC-09.md) — Typed Python validators for catalog rules and breaking-change detection.

### Phase 2: Static Error Core, No Runtime Discovery
- [`ERRC-10: Implement core value and metadata types`](ERRC-10.md) — `ErrorCode` value class, metadata enums, and `ErrorDefinition` in `libs/errors`.
- [`ERRC-11: Generate or compile static catalogs`](ERRC-11.md) — Preconstructed static catalog objects for each domain; zero reflection.
- [`ERRC-12: Implement governed exception and typed-diagnostics contracts`](ERRC-12.md) — `SquarewiseException`, bounded diagnostics, and fatal error classifiers.
- [`ERRC-13: Add static policy and reflection/generic-throw gates`](ERRC-13.md) — Automated CI scanner and allowlist prohibiting raw throws and reflection.
- [`ERRC-14: Implement logging, metrics, and trace adapter`](ERRC-14.md) — Structured logging, bounded Micrometer counters, and OpenTelemetry adapters.

### Phase 3: Transport Boundaries
- [`ERRC-15: Implement servlet Problem mapper and containment`](ERRC-15.md) — Spring MVC global error advice and static container error filters.
- [`ERRC-16: Implement servlet and reactive security boundaries`](ERRC-16.md) — Structured 401/403/404 ProblemDetails for Servlet and WebFlux security filters.
- [`ERRC-17: Implement GraphQL mapper and upstream problem client`](ERRC-17.md) — BFF DataFetcher exception resolver and WebClient problem decoder.
- [`ERRC-18: Implement messaging/background boundary toolkit`](ERRC-18.md) — Fatal-safe `AsyncExecutionTemplate` and dead-letter disposition strategies.

### Phase 4: Bounded-Context Migration
- [`ERRC-19: Migrate Accounts definitions and failures`](ERRC-19.md) — Replace 30 legacy throw sites with `AccountsErrors`.
- [`ERRC-20: Migrate Expense Core definitions and failures`](ERRC-20.md) — Replace 87 legacy throw sites with `ExpenseErrors` while preserving ACID rules.
- [`ERRC-21: Migrate Notifications definitions and failures`](ERRC-21.md) — Replace 7 throw sites, eliminate broad catches, and wrap event consumers.
- [`ERRC-22: Migrate BFF definitions and failures`](ERRC-22.md) — Replace 4 throw sites, remove string matching, and preserve upstream identities.
- [`ERRC-23: Migrate shared libraries and startup failures`](ERRC-23.md) — Migrate `libs/` to `PlatformErrors` and empty the hygiene allowlist.

### Phase 5: Clients, Acceptance, Scale & Rollout
- [`ERRC-24: Update client fixtures, Bruno, and end-to-end acceptance`](ERRC-24.md) — Bruno collections and live multi-service acceptance tests.
- [`ERRC-25: Security and privacy leakage campaign`](ERRC-25.md) — Adversarial fuzzing campaign verifying zero internal leakage or PII escape.
- [`ERRC-26: Performance, allocation, and regional load evidence`](ERRC-26.md) — JMH micro-benchmarks (<50ns lookup) and k6 error storm load tests.
- [`ERRC-27: Observability, alerts, and runbooks`](ERRC-27.md) — Grafana dashboards, Prometheus alert rules, and operator runbooks.
- [`ERRC-28: Dual-read/dual-write staged rollout`](ERRC-28.md) — Canary deployment across microservices with automated rollback triggers.
- [`ERRC-29: Make additive fields required after compatibility window`](ERRC-29.md) — Promote `numericCode` and `errorName` to required fields in schemas.
- [`ERRC-30: Retire legacy infrastructure, not the v1 field`](ERRC-30.md) — Safely delete legacy `ERR_XX` enums while preserving v1 response mappings.
- [`ERRC-31: Optional future major-version numeric code`](ERRC-31.md) — Architecture decision and prototype specifications for future API v2.

## Owned Deliverables

- `contracts/errors/domains.yaml`
- `docs/architecture/error-code-standard.md`
- `docs/architecture/error-domain-registry.md`
- `docs/architecture/error-code-refactoring.md`
- `docs/architecture/error-handling-guide.md`
- `docs/architecture/errors/` per-context guides
- Navigation and cross-links in `README.md`, `docs/README.md`, `docs/architecture/error-flow.md`, and `docs/implementation/plan.md`
- Task detail specifications: `ERRC-01.md`, `ERRC-01A.md`, `ERRC-01B.md`, `ERRC-01C.md`, and `ERRC-02.md` through `ERRC-31.md`
- Coordinator-owned registry, board, and progress ledgers

## Acceptance

The architecture documentation must distinguish approved decisions from proposals, maintain zero-reflection and zero-leakage guarantees, establish immutable registries, and provide complete, testable specifications for every follow-on increment.

## Validation Commands

```powershell
uv run python tools/contracts/validate.py
uv run python tools/errors/validate_catalog.py
git diff --check
```

## Completion Evidence

- Canonical identity, ownership, allocation, lifecycle, HTTP mapping, and reflection rules are recorded in `docs/architecture/error-code-standard.md` and `docs/architecture/error-domain-registry.md`.
- `docs/architecture/error-handling-guide.md` defines the minimal governed base, leaf exceptions, baked defaults, sealed family overrides, fatal classifier, transport boundaries, and observability adapters.
- Context guides in `docs/architecture/errors/` inventory all predictable failures for Accounts, Expense Core, Notifications, BFF, and Platform libraries.
- Complete task specifications [`ERRC-02.md`](ERRC-02.md) through [`ERRC-31.md`](ERRC-31.md) are authored with enterprise design patterns, common library mappings, and validation gates.
