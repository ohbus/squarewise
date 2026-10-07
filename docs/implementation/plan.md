# Implementation plan

## Current deliverable

The reviewed plans, contracts, scaffold, local operations, CI delivery, and
several backend product slices are now present. Product work remains partial and
tracked; UI implementation and public launch acceptance are not complete. All
app features remain free.

## Delivery gates

1. DOC-01 establishes the canonical registry and task specifications.
2. DOC-02/03 architecture, DOC-04/05/06 contracts, and DOC-07 quality/operations
   authoring proceed in parallel on disjoint paths. Contracts and quality may
   draft against the confirmed scope; DOC-08 reconciles all final documents.
3. DOC-08 validates task links/DAG, API schemas/examples, financial fixtures,
   GraphQL operation mapping, and the accepted JPA/Hibernate decision. Record
   documentation_gate.status=passed only with verification evidence.
4. FND-01 creates the build/apps/libs/UI placeholder. FND-02 local infrastructure
   and FND-03 portable checks can proceed in parallel after that shared scaffold.
5. Continue from the verified scaffold into registered product slices and
   hardening. QA-04, OPS-10, and the Production Hardening milestone (OPS-17
   through OPS-23, ERR-01 through ERR-12) are fully implemented and verified.
   CORE-22, QA-05, DOC-20, and OBS-01 have completed their registered increments.
   BFF-06/07/09/10/11 and CORE-23/24/25 have completed their current increments.

## Parallel implementation schedule

Accounts/security and Expense Core can proceed in parallel with messaging/BFF
contract adapters. One owner controls each shared boundary; delegated subagents
work only on registered, non-overlapping child tasks. Outbox integration
and core financial writes require a scheduled handoff rather than concurrent
edits to the same files. Quality work follows each implemented slice; final
end-to-end and capacity testing depend on a fully integrated stack.

## Scope

MVP: profiles, groups, placeholders and claiming, flexible expense splits,
multiple payers, per-currency balances, audited online edits by all active members,
immediate recorded repayments, recurrence, notifications, search/CSV, and offline
read/create synchronization. GraphQL BFF has HTTPS operations and WebSocket
invalidations; internal request/response APIs are REST. Payment processing,
refund workflows, FX conversion, OCR, attachments, offline editing, cross-group
settlement and UI implementation are deferred.

## Toolchain policy

The earlier Java26 proposal was superseded by the verified Java25 baseline;
versions are centralized in the catalog and wrapper. Record dependency-resolution
evidence for future changes and do not introduce preview versions.

## Verification

Documentation checks run before scaffolding. Scaffold checks cover dependency
resolution, compilation, package boundaries, application packaging, contract
validation, and reproducible local configuration. No passing scaffold check is
reported as proof of product behavior or production capacity. All checks and
limitations are recorded in the registry and final handoff.

## Future delivery

The task registry contains the complete implementation DAG and task detail links.
Future UI work will choose a framework separately and consume the GraphQL schema.
Hosting selection, operating budget and launch-market retention policy are public
launch prerequisites; they do not block portable contracts and the scaffold.

## Production-readiness delivery basis

The security remediation execution plan is tracked as
[`SEC-01`](../tasks/details/SEC-01.md). It preserves the whole-security audit's
NO-GO decision and requires separately registered implementation workstreams
before any production security claim is revised.

The backend is targeting 1–10 million DAU. A production-readiness audit has
identified 5 critical and 24 high-severity findings that must be resolved before
any public launch decision. The audit, roadmap, tracker, and release checklist
form a four-document execution basis:

| Document | Purpose | Location |
|---|---|---|
| **Production Readiness Audit** | Current-state decision with evidence-backed findings (WHAT is wrong) | [`docs/reviews/production-readiness-audit.md`](../reviews/production-readiness-audit.md) |
| **Production Readiness Roadmap** | 8-phase execution plan with design patterns and scale context (HOW to fix it) | [`production-readiness-roadmap.md`](production-readiness-roadmap.md) |
| **Production Readiness Tracker** | 17 workstreams with ownership, dependencies, and acceptance criteria (WHO does WHAT, WHEN) | [`docs/tasks/production-readiness-tracker.md`](../tasks/production-readiness-tracker.md) |
| **Production Readiness Plan** | Pre-launch gate checklist with measurable pass/fail criteria (IS IT READY?) | [`docs/operations/production-readiness-plan.md`](../operations/production-readiness-plan.md) |

The workstreams cover: domain/API reconciliation, authorization matrix,
authentication hardening, financial concurrency and correctness, idempotency and
replay, messaging reliability, code quality and design enforcement, configuration
hygiene, CI and supply-chain controls, capacity proof, disaster recovery, and
final release approval.

These documents are planning artifacts until the coordinator registers the
workstreams in `docs/tasks/registry.yaml` and `docs/tasks/board.md`. The audit
decision: **do not approve production launch**: remains in effect until
evidence is produced in an approved production-like environment.
# Error reporting evolution: Six-digit domain error-code migration

ERR-01 through ERR-12 established the initial symbolic v1 vocabulary and baseline controls. [`ERRC-01`](../tasks/details/ERRC-01.md) now serves as the opening baseline and umbrella specification for the six-digit `DM-L-C-EE` domain/module error-code migration. The implementation sequence is registered in [`docs/tasks/registry.yaml`](../tasks/registry.yaml) and tracked on [`docs/tasks/board.md`](../tasks/board.md) across tasks **ERRC-02 through ERRC-31**.

## Target Model & Identity Invariants

The canonical identity is `DMLCEE` (machine string) displayed as `DM-L-C-EE` (human display) and paired with an immutable symbolic name (`SCREAMING_SNAKE_CASE`):
- `D`: Bounded context domain (1=Accounts, 2=Expense Core, 3=Notifications, 4=BFF, 9=Platform; 5–8 reserved).
- `M`: Stable capability module within the domain.
- `L`: Semantic layer of origin (1=Interface, 2=Application, 3=Domain, 4=Persistence, 5=Messaging, 6=Integration, 7=Security, 8=Infrastructure, 9=Shared Runtime).
- `C`: Category of remediation (1=Validation, 2=Missing, 3=Conflict, 4=State, 5=Business Rule, 6=Consistency, 7=Communication, 8=Availability, 9=Internal).
- `EE`: Monotonic sequence (01–89 normal, 90–98 reserved, 99 unclassified fallback).

API v1 retains its existing symbolic `code` (`"NOT_FOUND"`) and first adds optional `numericCode` (`"213201"`) and `errorName` (`"GROUP_NOT_FOUND"`). Replacing `code` with numeric identity is deferred to an approved future major API version ([`ERRC-31`](../tasks/details/ERRC-31.md)).

## Enterprise Design Patterns & Platform Libraries

The implementation strictly embodies proven enterprise architecture patterns:
1. **Value Object Pattern**: Zero-allocation `@JvmInline value class ErrorCode(val value: String)` in [`libs/errors`](../../libs/errors/) validating `^[1-9]{4}(0[1-9]|[1-9][0-9])$` and providing constant-time digit extraction.
2. **Ports & Adapters (Hexagonal Architecture)**: Domain logic depends purely on domain concepts and interfaces; transport adapters in [`libs/errors`](../../libs/errors/) and [`libs/security`](../../libs/security/) translate domain exceptions into RFC 9457 Problem Details (REST), GraphQL Extensions (BFF), or Dead-Letter Envelopes (RabbitMQ).
3. **Exception Shielding Pattern**: Public responses strictly read from catalog `title` and `safeDetail`. Internal stack traces, raw exception messages (`Throwable.message`), and SQL vendor exceptions are strictly prohibited from public payloads.
4. **Constrained Strategy / Sealed Family Overrides**: Leaf exceptions bake in default catalog definitions. Manual overrides are restricted to sealed interface families, preventing callers from injecting arbitrary uncataloged definitions.
5. **Anti-Corruption Layer (ACL)**: The BFF gateway preserves upstream REST Problem Details byte-for-byte in GraphQL `extensions`, refusing to synthesize random request IDs or classify errors solely by HTTP status.
6. **Transactional Outbox & Idempotent Consumer**: Database mutations, audit records, and outbox publications share single ACID transactions in [`libs/db`](../../libs/db/). Event consumers in [`libs/errors`](../../libs/errors/) use `AsyncExecutionTemplate` to isolate poison messages without blocking queues.
7. **Strangler Fig & Tolerant Reader**: Consumers deploy with tolerant reader logic first; producers deploy additive fields second; legacy enum infrastructure in [`libs/errors`](../../libs/errors/) is retired only after compatibility certification ([`ERRC-30`](../tasks/details/ERRC-30.md)).
8. **Strict SOLID File Separation**: Every entity, interface, service, DTO, value object, and enum resides in its own dedicated file with structured KDoc comments.

## Phased Implementation Roadmap

The workstream is organized into five sequential execution phases:

### Phase 0: Governance & Frozen Evidence
- [`ERRC-02`](../tasks/details/ERRC-02.md): Freeze audit and characterization baseline across all 128 production throw sites.
- [`ERRC-03`](../tasks/details/ERRC-03.md): Freeze namespace registries and author machine-readable `error-catalog.schema.json`.
- [`ERRC-04`](../tasks/details/ERRC-04.md): Allocate and review complete six-digit error catalog in `error-catalog.yaml`; retire `ERR-12`.

### Phase 1: Contract-First Compatibility
- [`ERRC-05`](../tasks/details/ERRC-05.md): Define additive RFC 9457 Problem Details contract with optional `numericCode`/`errorName`.
- [`ERRC-06`](../tasks/details/ERRC-06.md): Reconcile all REST OpenAPI contracts with complete non-2xx responses and headers.
- [`ERRC-07`](../tasks/details/ERRC-07.md): Define GraphQL error extensions and WebSocket custom close code contracts.
- [`ERRC-08`](../tasks/details/ERRC-08.md): Define messaging, outbox, and background execution failure contracts.
- [`ERRC-09`](../tasks/details/ERRC-09.md): Upgrade Python contract validators and CI breaking-change detection gates.

### Phase 2: Static Error Core, No Runtime Discovery
- [`ERRC-10`](../tasks/details/ERRC-10.md): Implement zero-allocation `ErrorCode` value class and core metadata enums in `libs/errors`.
- [`ERRC-11`](../tasks/details/ERRC-11.md): Generate compile-time static error catalog objects for all domains (zero reflection).
- [`ERRC-12`](../tasks/details/ERRC-12.md): Implement governed `SquarewiseException`, bounded diagnostics, and fatal classifiers.
- [`ERRC-13`](../tasks/details/ERRC-13.md): Add static analysis rules and CI gates prohibiting raw throws, reflection, and broad catches.
- [`ERRC-14`](../tasks/details/ERRC-14.md): Implement structured logging, bounded Micrometer metrics, and OpenTelemetry trace adapters in `libs/observability`.

### Phase 3: Transport Boundaries
- [`ERRC-15`](../tasks/details/ERRC-15.md): Implement Spring Web MVC Problem Details advice and static container fallbacks.
- [`ERRC-16`](../tasks/details/ERRC-16.md): Implement structured 401/403/404 ProblemDetails for Servlet and Reactive security filters in `libs/security`.
- [`ERRC-17`](../tasks/details/ERRC-17.md): Implement BFF GraphQL exception resolver and upstream WebClient problem client.
- [`ERRC-18`](../tasks/details/ERRC-18.md): Implement fatal-safe `AsyncExecutionTemplate` and dead-letter disposition strategies.

### Phase 4: Bounded-Context Migration
- [`ERRC-19`](../tasks/details/ERRC-19.md): Migrate Accounts service definitions and throw sites to `AccountsErrors`.
- [`ERRC-20`](../tasks/details/ERRC-20.md): Migrate Expense Core service definitions and throw sites while preserving ACID invariants.
- [`ERRC-21`](../tasks/details/ERRC-21.md): Migrate Notifications service definitions, remove broad catches, and wrap consumers.
- [`ERRC-22`](../tasks/details/ERRC-22.md): Migrate BFF resolvers, remove string matching, and preserve upstream error identities.
- [`ERRC-23`](../tasks/details/ERRC-23.md): Migrate shared platform libraries (`libs/`) to `PlatformErrors` and empty hygiene allowlist.

### Phase 5: Clients, Acceptance, Scale, Rollout
- [`ERRC-24`](../tasks/details/ERRC-24.md): Update Bruno collections and live multi-service end-to-end acceptance test suites.
- [`ERRC-25`](../tasks/details/ERRC-25.md): Execute adversarial fuzzing campaign proving zero stack, SQL, secret, or PII leakage.
- [`ERRC-26`](../tasks/details/ERRC-26.md): Gather JMH (<50ns lookup) and k6 error storm evidence supporting 10M+-DAU scale.
- [`ERRC-27`](../tasks/details/ERRC-27.md): Create Grafana dashboards, Prometheus alert rules, and operational runbooks.
- [`ERRC-28`](../tasks/details/ERRC-28.md): Execute canary-driven staged production rollout across microservices.
- [`ERRC-29`](../tasks/details/ERRC-29.md): Promote `numericCode` and `errorName` to required fields after compatibility window.
- [`ERRC-30`](../tasks/details/ERRC-30.md): Safely decommission legacy `ERR_XX` enums while retaining v1 response compatibility.
- [`ERRC-31`](../tasks/details/ERRC-31.md): Author architectural decision and prototype specifications for future API v2.

Canonical references:
- [Opening umbrella specification](../tasks/details/ERRC-01.md)
- [Six-digit error code standard](../architecture/error-code-standard.md)
- [Domain/module namespace registry](../architecture/error-domain-registry.md)
- [Exception and boundary handling guide](../architecture/error-handling-guide.md)
- [Per-context failure inventories](../architecture/errors/README.md)
- [Refactoring and migration plan](../architecture/error-code-refactoring.md)
- [Authoritative task board](../tasks/board.md)
