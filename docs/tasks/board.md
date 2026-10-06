# Task board

The coordinator owns this board and `registry.yaml`. The registry is the source
of truth. Its JSON formatting is valid YAML 1.2 and permits dependency-free
validation with Python's standard library.

## CQRS data-access and PostgreSQL reader-scaling milestone

The implementation-ready code inventory is maintained in
`docs/implementation/cqrs-code-inventory.md`. These tasks are intentionally
writer-safe: no reader routing is enabled until the shared kernel, route guards,
query classification, and evidence gates are complete.

| ID | Owner | Status | Deliverable |
| --- | --- | --- | --- |
| DB-01 | coordinator | done | Catalog every persistence operation and register the workstream |
| DB-02 | architecture | done | Define command/query, consistency, watermark, fallback, and retry contracts |
| DB-03 | coordinator | done | Implement `libs/db` route context, policies, and transaction guards |
| DB-04 | platform | done | Add separate writer/named-reader pools and writer-only migration wiring |
| DB-05 | platform | done | Add reader lag health, circuit breaking, and bounded fallback |
| DB-06 | core | done | Split Expense Core command/query ports while preserving financial transactions |
| DB-07 | core | done | Pilot bounded Expense Core search projection and measured query optimization |
| DB-08 | accounts | done | Split Accounts command/query ports with writer-only auth state |
| DB-09 | notifications | done | Split Notifications command/query ports with writer-only delivery state |
| DB-10 | coordinator | done | Propagate causal writer watermarks through services and BFF |
| DB-11 | observability | done | Add query operation telemetry and slow-query governance |
| DB-12 | quality | done | Add contention, replica failure, lag, and capacity evidence |
| DB-13 | platform | done | Add optional local/production-like PostgreSQL replica topology |
| DB-14 | coordinator | done | Run one reviewed historical-read replica pilot |
| DB-15 | coordinator | done | Promote only individually approved query capabilities |
| DB-16 | operations | done | Complete failover, restore, rollback, alert, and release gates |
| DB-17 | coordinator | done | Reconcile implementation and evidence against every plan requirement |

## Current milestone: documentation and contracts

## OSS community health

| ID | Owner | Status | Deliverable |
| --- | --- | --- | --- |
| DOC-27 | coordinator | done | GitHub community standards, security reporting, issue/PR templates, MIT license, and accessibility statement |

## Exhaustive public-interface coverage

| ID | Owner | Status | Deliverable |
| --- | --- | --- | --- |
| QA-07 | coordinator | done | Contract-driven REST, GraphQL, WebSocket, negative-path, concurrency, recovery, and evidence matrix |
| QA-08 | coordinator | done | Production-scale, deployment-resilience, security, and unresolved WebSocket protocol evidence |
| QA-09 | coordinator | done | Optimized and parallelized E2E pipeline with artifact reuse |
| QA-10 | coordinator | done | Repository-wide unit, integration, and E2E test gap audit and closure criteria |
| OPS-25 | coordinator | done | Migrate Python tooling to pyproject.toml + uv sync + uv run |
| OPS-26 | coordinator | done | Decouple dev data seeder and prepare local stack for non-docker runtimes |
| OPS-27 | coordinator | done | Register devcontainer version baseline and security hygiene rules |
| OPS-28 | coordinator | done | Create devcontainer scaffolding and multi-service compose integration |
| OPS-29 | coordinator | done | Implement zero-friction startup automation and background data seeding |
| OPS-30 | coordinator | done | Validate devcontainer workflows across IDEs and document clone-and-run experience |
| OPS-31 | coordinator | done | Centralize runtime and toolchain versions and unify Docker anti-drift build patterns |

## Production hardening milestone

| ID | Owner | Status | Deliverable |
| --- | --- | --- | --- |
| PR-41 | coordinator | done | Make BFF financial fanout fail closed |
| PR-42 | coordinator | done | Bound RabbitMQ transient redelivery |
| PR-43 | coordinator | done | Wire configurable GraphQL subscription bounds |
| PR-44 | coordinator | done | Serialize GraphQL subscription admission |
| PR-45 | coordinator | done | Lock recurring schedules during worker claims |
| PR-46 | coordinator | done | Add durable settlement recording idempotency |
| PR-47 | coordinator | done | Add PostgreSQL populated-settlement reconciliation evidence |
| PR-48 | coordinator | done | Add CI dependency vulnerability review gate |
| PR-49 | coordinator | done | Bound GraphQL transport request sizes |
| AUTH-01 | coordinator | done | Provider-neutral OIDC authentication hardening baseline and implementation tracker |
| AUTH-02 | coordinator | done | Remove implicit authentication identities with full boundary evidence |
| AUTH-03 | coordinator | done | Fail-closed provider-neutral OIDC resource-server validation |
| AUTH-04 | coordinator | done | Validate OIDC JWT claims, signatures, expiry, and subjects |
| AUTH-05 | coordinator | done | Remove weaker local authentication modes and require local OIDC parity |
| AUTH-06 | coordinator | done | Keycloak environment, real OIDC journeys, and meaningful Compose hostnames |
| PR-17 | coordinator | done | Financial ledger reconciliation and durable mutation idempotency |
| PR-18 | coordinator | done | Bounded persistence reads and mutation-time authorization |
| PR-19 | coordinator | done | Messaging retry, dead-letter, and poison-message handling |
| PR-20 | coordinator | done | GraphQL abuse controls |
| PR-21 | coordinator | done | CI, security, SBOM, and architecture gates |
| PR-22 | coordinator | done | Remove production in-memory persistence fallbacks |
| PR-23 | coordinator | done | Profile and financial adapter production wiring |
| PR-24 | coordinator | done | Remove Accounts request-service in-memory defaults |
| PR-25 | coordinator | done | Fail closed on production identity-provider wiring |
| PR-26 | coordinator | done | Expense participant and request-bound validation |
| PR-27 | coordinator | done | Notification fail-closed delivery policy |
| PR-28 | coordinator | done | BFF fail-closed upstream configuration |
| PR-29 | coordinator | done | Checked financial arithmetic |
| PR-30 | coordinator | done | Subscription revocation on membership removal |
| PR-31 | coordinator | done | Required production messaging capabilities |
| PR-32 | coordinator | done | Deployment overlay configuration alignment |
| PR-33 | coordinator | done | Security hygiene fixture classification |
| PR-34 | coordinator | done | Notification log redaction |
| PR-35 | coordinator | done | Actuator exposure hardening |
| PR-36 | coordinator | done | Versioned notification queue topology |
| PR-37 | coordinator | done | GraphQL abuse-control transport evidence |
| PR-38 | coordinator | done | CI workflow and release-gate parity |
| PR-39 | coordinator | done | Production application topology controls |
| PR-40 | coordinator | done | Idempotency retention and cleanup |
| AUTH-07 | coordinator | done | Squarewise-owned passwordless login, token lifecycle, provider portability, and authorization evidence |
| AUTH-08 | coordinator | done | RFC-aligned authentication/session hardening, endpoint protection, cache consistency, and full local security evidence |
| SEC-01 | coordinator | done | Whole-security remediation plan for SEC-001 through SEC-013 |
| SEC-01A | coordinator | done | Establish operational token authority and asymmetric signing (SEC-001, SEC-009) |
| SEC-01B | coordinator | done | Make durable identity independent of email with (issuer, subject) mapping (SEC-002) |
| SEC-01C | coordinator | done | Enforce object authorization on profiles and workload trust boundary (SEC-004, SEC-005) |
| SEC-01D | coordinator | done | Close browser mutation CSRF and WebSocket subscription continuity gaps (SEC-003, SEC-006) |
| SEC-01E | coordinator | done | Formalize rate-limit proxy topology and bearer-revocation guarantees (SEC-007, SEC-008) |
| SEC-01F | coordinator | done | Make security operations, telemetry, supply chain, and release evidence executable (SEC-010 - SEC-013) |
| OPS-24 | coordinator | done | Remove undeclared Ruby dependency and E2E Compose host-port collisions from CI |
| OPS-32 | coordinator | in_progress | Select changed-scope PR and branch CI while keeping master full |
| OPS-17 | coordinator | done | Stable error taxonomy and service/source attribution |
| OPS-18 | coordinator | done | Micrometer and Prometheus metrics for all services |
| OPS-19 | operations | done | Dashboards, alerts, SLOs, and runbooks baseline |
| OPS-20 | platform | done | Reliability, security, and capacity release gates |
| OPS-21 | platform | done | Modular k6 load tests for high-value endpoints |
| OPS-22 | platform | done | 1M-user capacity baseline and production readiness evidence |
| OPS-23 | platform | done | Fixture-backed mutation capacity scenarios |

## Error reporting hardening workstream

| ID | Owner | Status | Deliverable |
| --- | --- | --- | --- |
| ERR-01 | coordinator | done | Governed error taxonomy, catalog, naming, and validation |
| ERR-02 | coordinator | done | Typed shared error definitions, exceptions, problem envelope, and error IDs |
| ERR-03 | coordinator | done | Structured 401/403 security errors across REST services and BFF |
| ERR-04 | accounts | done | Accounts-specific error migration |
| ERR-05 | core | done | Expense Core groups and membership error migration |
| ERR-06 | core | done | Expense Core expense and idempotency error migration |
| ERR-07 | core | done | Settlement, recurrence, sync, and outbox error migration |
| ERR-08 | notifications | done | Notifications and event-consumer error migration |
| ERR-09 | bff | done | Upstream and GraphQL error mapping |
| ERR-10 | quality | done | Contract, unit, integration, Bruno, and acceptance coverage |
| ERR-11 | operations | done | Bounded error metrics, dashboards, and alerts |
| ERR-12 | coordinator | done | Governance review, release evidence, and completion gate |

## Six-digit error-code migration workstream

This workstream manages the six-digit `DM-L-C-EE` domain/module error-code standard and migration across all Squarewise deployables and shared libraries. [`ERRC-01`](details/ERRC-01.md) serves as the opening architecture baseline and umbrella milestone. The implementation roadmap is partitioned into five distinct, sequentially ordered phases with strict SOLID boundaries, proven enterprise design patterns, and full backward compatibility for API v1 consumers.

### Opening & Umbrella Architecture Milestone

| ID | Owner | Status | Deliverable |
| --- | --- | --- | --- |
| [ERRC-01](details/ERRC-01.md) | coordinator | done | Opening baseline: canonical standard, registry, decisions, and exhaustive migration roadmap |
| [ERRC-01A](details/ERRC-01A.md) | review | done | Kotlin throw/catch/boundary inventory audit |
| [ERRC-01B](details/ERRC-01B.md) | contracts | done | REST, GraphQL, event, and compatibility audit |
| [ERRC-01C](details/ERRC-01C.md) | architecture | done | Exception, performance, fatal-failure, and operations policy review |

### Phase 0: Governance and Frozen Evidence

| ID | Owner | Status | Deliverable |
| --- | --- | --- | --- |
| [ERRC-02](details/ERRC-02.md) | quality | done | Freeze audit and characterization baseline across all 131 production throw sites |
| [ERRC-03](details/ERRC-03.md) | architecture | done | Freeze namespace registries and author `error-catalog.schema.json` |
| [ERRC-04](details/ERRC-04.md) | coordinator | done | Allocate and review complete six-digit error catalog; retire `ERR-12` |

### Phase 1: Contract-First Compatibility

| ID | Owner | Status | Deliverable |
| --- | --- | --- | --- |
| [ERRC-05](details/ERRC-05.md) | contracts | done | Define additive RFC 9457 Problem Details contract with optional `numericCode`/`errorName` |
| [ERRC-06](details/ERRC-06.md) | contracts | done | Reconcile all REST OpenAPI contracts with complete non-2xx responses and headers |
| [ERRC-07](details/ERRC-07.md) | contracts | done | Define GraphQL error extensions and WebSocket custom close code contracts |
| [ERRC-08](details/ERRC-08.md) | contracts | done | Define messaging, outbox, and background execution failure contracts |
| [ERRC-09](details/ERRC-09.md) | quality | done | Upgrade Python contract validators and CI breaking-change detection gates |

### Phase 2: Static Error Core, No Runtime Discovery

| ID | Owner | Status | Deliverable |
| --- | --- | --- | --- |
| [ERRC-10](details/ERRC-10.md) | platform | done | Implement zero-allocation `ErrorCode` value class and core metadata enums in `libs/errors` |
| [ERRC-11](details/ERRC-11.md) | platform | done | Generate compile-time static error catalog objects for all domains (zero reflection) |
| [ERRC-12](details/ERRC-12.md) | platform | done | Implement governed `SquarewiseException`, bounded diagnostics, and fatal classifiers |
| [ERRC-13](details/ERRC-13.md) | quality | todo | Add static analysis rules and CI gates prohibiting raw throws, reflection, and broad catches |
| [ERRC-14](details/ERRC-14.md) | observability | todo | Implement structured logging, bounded Micrometer metrics, and OpenTelemetry trace adapters |

### Phase 3: Transport Boundaries

| ID | Owner | Status | Deliverable |
| --- | --- | --- | --- |
| [ERRC-15](details/ERRC-15.md) | platform | todo | Implement Spring Web MVC Problem Details advice and static container fallbacks |
| [ERRC-16](details/ERRC-16.md) | security | todo | Implement structured 401/403/404 ProblemDetails for Servlet and Reactive security filters |
| [ERRC-17](details/ERRC-17.md) | platform | todo | Implement BFF GraphQL exception resolver and upstream WebClient problem client |
| [ERRC-18](details/ERRC-18.md) | platform | todo | Implement fatal-safe `AsyncExecutionTemplate` and dead-letter disposition strategies |

### Phase 4: Bounded-Context Migration

| ID | Owner | Status | Deliverable |
| --- | --- | --- | --- |
| [ERRC-19](details/ERRC-19.md) | accounts | todo | Migrate Accounts service definitions and throw sites to `AccountsErrors` |
| [ERRC-20](details/ERRC-20.md) | core | todo | Migrate Expense Core service definitions and throw sites while preserving ACID invariants |
| [ERRC-21](details/ERRC-21.md) | notifications | todo | Migrate Notifications service definitions, remove broad catches, and wrap consumers |
| [ERRC-22](details/ERRC-22.md) | bff | todo | Migrate BFF resolvers, remove string matching, and preserve upstream error identities |
| [ERRC-23](details/ERRC-23.md) | platform | todo | Migrate shared platform libraries (`libs/`) to `PlatformErrors` and empty hygiene allowlist |

### Phase 5: Clients, Acceptance, Scale, Rollout

| ID | Owner | Status | Deliverable |
| --- | --- | --- | --- |
| [ERRC-24](details/ERRC-24.md) | quality | todo | Update Bruno collections and live multi-service end-to-end acceptance test suites |
| [ERRC-25](details/ERRC-25.md) | security | todo | Execute adversarial fuzzing campaign proving zero stack, SQL, secret, or PII leakage |
| [ERRC-26](details/ERRC-26.md) | quality | todo | Gather JMH (<50ns lookup) and k6 error storm evidence supporting 10M+-DAU scale |
| [ERRC-27](details/ERRC-27.md) | operations | todo | Create Grafana dashboards, Prometheus alert rules, and operational runbooks |
| [ERRC-28](details/ERRC-28.md) | operations | todo | Execute canary-driven staged production rollout across microservices |
| [ERRC-29](details/ERRC-29.md) | contracts | todo | Promote `numericCode` and `errorName` to required fields after compatibility window |
| [ERRC-30](details/ERRC-30.md) | coordinator | todo | Safely decommission legacy `ERR_XX` enums while retaining v1 response compatibility |
| [ERRC-31](details/ERRC-31.md) | architecture | todo | Author architectural decision and prototype specifications for future API v2 |

| ID | Owner | Status | Deliverable |
| --- | --- | --- | --- |
| DOC-01 | coordinator | done | Tracker, working agreement, task details |
| DOC-02 | architecture agent | done | Product scope and ownership |
| DOC-03 | architecture agent | done | Technology, security, persistence, structure |
| DOC-04 | coordinator | done | Financial rules and REST contracts |
| DOC-05 | coordinator | done | Events, recurrence, offline contracts |
| DOC-06 | coordinator | done | GraphQL schema and operation mapping |
| DOC-07 | quality agent | done | Acceptance, operations, recovery and capacity |
| DOC-08 | coordinator | done | Cross-review and documentation gate |
| DOC-09 | coordinator | done | Mermaid visual documentation with Docker Compose rendering |
| DOC-10 | coordinator | done | Version-controlled IntelliJ run configurations |
| DOC-11 | coordinator | done | Endpoint-specific pagination policy and bounded collection guidance |
| DOC-12 | coordinator | done | Establish compatible Kotlin quality and coding guidelines |
| DOC-13 | coordinator | done | Repository-wide implementation review and empty-folder cleanup |
| DOC-14 | lint_research | done | Evaluate Kotlin lint compatibility |
| DOC-15 | coordinator | done | Repository-wide plan and implementation drift audit |
| DOC-15A | drift_architecture | done | Architecture, contract, and documentation drift review |
| DOC-15B | drift_build | done | Code, build, CI, and test drift review |
| DOC-16 | contract_reconciliation | done | Reconcile API contracts with implemented endpoints |
| FND-01 | coordinator | done | Verified Gradle scaffold and UI placeholder |
| FND-02 | platform | done | Local infrastructure and migration foundations |
| FND-03 | quality | done | CI and executable quality checks |
| FND-04 | coordinator | done | Repository workflow Makefile |
| FND-05 | coordinator | done | Shared REST error flow |
| FND-06 | coordinator | done | Central dependency and plugin catalog |
| FND-07 | uuidv7 | done | Centralized UUIDv7 generation abstraction |
| QA-01 | acceptance_harness | done | Integrated product acceptance harness |
| QA-02 | coordinator | done | Scaffold tests, smoke E2E, and coverage baseline |
| OPS-01 | deployment_release | done | Deployment telemetry and release hardening |
| OPS-02 | recovery_capacity | done | Recovery, capacity, cost, and launch verification |
| OPS-03 | coordinator | done | Modular Dockerfiles and Compose operations |
| OPS-04 | coordinator | done | Configurable PR, branch, and main CI workflows with GHCR image delivery |
| OPS-05 | coordinator | done | CI hardening review and local parity improvements |
| OPS-06 | coordinator | done | Centralized aggressive reusable CI caching |
| OPS-07 | coordinator | done | Dependency service containers and local health checks |
| ACC-01 | accounts_slice | done | Accounts profile contract slice with validation and authenticated access |
| ACC-02 | accounts_persistence | done | Durable account deletion and export request persistence |
| CORE-01 | groups_membership | done | Groups, membership, and invitations |
| CORE-02 | coordinator | done | Allocation preview and financial domain foundation |
| CORE-03 | coordinator | done | Settlement record and idempotent reversal domain slice |
| CORE-04 | sync_slice | done | Offline synchronization and cursor snapshots |
| CORE-05 | recurrence_slice | done | Recurring expense generation |
| CORE-06 | search_export | done | Search and export boundaries |
| CORE-07 | expense_categories | done | Categorized expenses and category management |
| CORE-08 | expense_persistence | done | Durable Expense Core settlement persistence adapter |
| CORE-09 | group_persistence | done | Durable group, membership, and invitation persistence |
| CORE-10 | outbox_persistence | done | Durable transactional outbox persistence |
| CORE-11 | sync_persistence | done | Durable synchronization change persistence |
| CORE-12 | outbox_relay_daemon | done | Outbox background polling relay daemon |
| CORE-13 | expense_persistence | done | Durable expense ledger entity and posting persistence |
| CORE-14 | expense_persistence | done | Durable expense update, deletion, and posting reversal |
| MSG-01 | outbox_delivery | done | Outbox and broker delivery |
| NOT-01 | notifications_slice | done | Notification inbox and delivery |
| NOT-02 | notification_persistence | done | Durable Notifications preference persistence adapter |
| NOT-03 | inbox_persistence | done | Durable notification inbox persistence |
| NOT-04 | notification_consumer | done | Transactional notification event consumption |
| NOT-05 | rabbit_listener | done | RabbitMQ listener and acknowledgement adapter |
| BFF-01 | bff_gateway | done | GraphQL BFF gateway adapters |
| BFF-02 | live_updates_slice | done | BFF live update fanout |
| BFF-03 | bff_gateway | done | GraphQL BFF query and mutation resolvers |
| CORE-15 | recurring_agent | done | Durable recurring expense schedules, occurrences, and runner |
| QA-03 | acceptance_agent | done | Real multi-service acceptance test scenarios |
| NOT-06 | email_agent | done | SMTP email dispatch adapter in Notifications service |
| ACC-03 | accounts_agent | done | Expose GDPR export request REST endpoints in Accounts service |
| CORE-16 | settlement_agent | done | Debt simplification and settlement suggestions engine |
| BFF-04 | bff_agent | done | Settlement suggestions GraphQL resolver |
| NOT-07 | consumer_agent | done | Connect notification consumer with email dispatch |
| OPS-08 | devops_agent | done | Compose dev environment configuration hardening and live acceptance workflow |
| CORE-17 | group_agent | done | Expose group members endpoint and durable member listing in Expense Core |
| BFF-05 | subscription_agent | done | Implement groupChanged GraphQL subscription with reactive live update sink |
| NOT-08 | pref_agent | done | Complete Notifications preferences contract schema and persistence validation |
| ACC-04 | account_agent | done | Expose public profile lookup endpoint by account ID in Accounts service |
| CORE-18 | group_update_agent | done | Group rename slice and hardening complete |
| BFF-06 | group_graphql_agent | done | Group update/member slice completed with bounded member resolution |
| NOT-09 | read_agent | done | Add mark inbox notification as read endpoint in Notifications service |
| ACC-05 | batch_account_agent | done | Expose batch profile lookup REST endpoint in Accounts service |
| DOC-17 | coordinator | done | Reconcile task state, Git ownership, and repository drift |
| DOC-17A | task_state_audit | done | Audit tracker state and evidence consistency |
| DOC-17B | git_task_map | done | Map commits and worktree files to tasks |
| DOC-17C | drift_review | done | Review implementation, contracts, tests, and scope drift |
| OPS-09 | compose_topology | done | Add dependency-only, standalone-app, and full-stack Compose workflows |
| CORE-19 | core | done | Complete group lifecycle and membership administration |
| CORE-20 | core | done | Expose recurring schedule management and pause notifications |
| CORE-21 | core | done | Complete authorized persistent search and CSV transport |
| CORE-22 | coordinator | done | Harden group updates with transactional change effects |
| BFF-07 | coordinator | done | Complete bounded GraphQL group-member resolution |
| MSG-02 | messaging | done | Deliver committed group changes to every BFF replica |
| QA-04 | coordinator | done | Execute authenticated real-dependency product acceptance |
| OPS-10 | platform | done | Produce public-launch restore, capacity, and cost evidence |
| DOC-18 | contracts | done | Reconcile current API operation and error contracts |
| DOC-19 | coordinator | done | Backfill legacy tracker ownership and evidence |
| DOC-20 | coordinator | done | Establish a non-breaking Kotlin formatting baseline |
| OPS-11 | coordinator | done | Fix Buildx GHA cache export for main image publishing |
| DOC-22 | coordinator | done | Apply programming principles and reconcile current documentation |
| DOC-23 | coordinator | done | Mandate continuous documentation updates and Javadoc/KDoc comments in working agreement |
| OPS-12 | coordinator | done | Isolate GHCR image publishing from reusable verification workflow |
| OPS-13 | coordinator | done | Align CI triggers, workflows, and documentation with master branch |
| OPS-14 | coordinator | done | Enable E2E smoke checks on feature branch CI |
| CORE-23 | core23_agent | done | Verify group-rename atomic rollback and effect payloads |
| CORE-24 | core | done | Cover group-rename request and authorization edge cases |
| CORE-25 | core25_agent | done | Verify group-rename concurrency against PostgreSQL |
| BFF-08 | bff | done | Test bounded group-member fanout behavior |
| IDE-01 | coordinator | done | Resolve IntelliJ GraphQL schema detection and eliminate unnecessary constructor field injection |
| BFF-09 | bff09_agent | done | Verify GraphQL transport error mapping for group operations |
| QA-05 | qa05_agent | done | Add public-interface edge-case acceptance journeys |
| BFF-10 | bff10_audit | done | Resolve GraphQL scalar deprecation warnings |
| BFF-11 | bff11_agent | done | Expose and verify the GraphQL HTTP transport route |
| QA-06 | coordinator | done | Isolate JpaOutboxStoreTest from inter-suite database pollution |
| OPS-15 | coordinator | done | Publish CI test results, pass down built application artifacts, and optimize caching |
| OPS-16 | coordinator | done | Standardize top-level CI environment and Node 24 runtime enforcement |
| DOC-24 | coordinator | done | Mandate strict SOLID file separation, comprehensive documentation linking, and pre-implementation documentation review |
| CORE-26 | core | done | Refactor Expense Core groups and settlements persistence into separated SOLID files |
| ACC-06 | accounts | done | Refactor Accounts persistence into separated SOLID files |
| NOT-10 | notifications | done | Refactor Notifications persistence into separated SOLID files |
| CORE-27 | core | done | Refactor Expense Core expenses, sync, and outbox persistence into separated SOLID files |
| CORE-28 | core | done | Refactor Expense Core recurring persistence into separated SOLID files |
| FND-08 | coordinator | done | Refactor domain ports, in-memory stores, and consumer services into dedicated files |
| OBS-01 | coordinator | done | Implement cross-cutting structured logging, MDC correlation, and observability tools |
| AUTH-09 | coordinator | in_progress | Implement distributed Redis rate limiting and request-path security controls |
| DOC-26 | coordinator | done | Reconcile local smoke-demo delivery evidence and Bruno API collection |
| SEC-02 | coordinator | done | Implement and verify whole security audit remediation (H-1, H-2, M-1 to M-5, L-1 to L-6) |
