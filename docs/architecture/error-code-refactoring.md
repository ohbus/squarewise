# Error-code and exception refactoring plan

Status: documentation-only decision and registered implementation backlog opened by [`ERRC-01`](../tasks/details/ERRC-01.md).
No implementation task in this document is authorized until the coordinator registers
it with ownership, dependencies, and paths in the task registry.

## Outcome

Squarewise will move from twelve flat `ERR-XX` definitions and free-form exception
messages to immutable six-digit domain/module identities, catalog-controlled public
metadata, governed exceptions/typed outcomes, and explicit handling at every REST,
security, GraphQL, WebSocket, messaging, scheduler, startup, and serialization
boundary.

The migration is additive for API v1:

```json
{
  "code": "NOT_FOUND",
  "numericCode": "213201",
  "errorName": "GROUP_NOT_FOUND"
}
```

The existing symbolic `code` and current status stay intact during the compatibility
window. Replacing `code` with the numeric identity is a future versioned breaking
change. Internal stack traces and cause text are never public, including on 500s.

The design targets a globally deployed product with at least 10M daily active users;
readiness must be demonstrated by compatibility, redaction, concurrency, allocation,
and production-like load evidence. The target is not itself proof.

## Scope and exclusions

In scope:

- canonical domain/module/layer/category and exact-code governance;
- all existing and predictable failure conditions in four applications and shared
  libraries;
- exception/typed-outcome design, controlled overrides, and generic-throw policy;
- REST Problem Details, security filters, GraphQL extensions, WebSocket close/error
  behavior, messaging attempts/parking, scheduled work, startup, and shutdown;
- OpenAPI, JSON Schema, event/GraphQL documentation, catalog, validation, tests,
  observability, runbooks, rollout, and rollback;
- no-reflection runtime architecture and measured performance acceptance.

Out of scope for this documentation task:

- Kotlin, Gradle, Python, schema, contract, or runtime implementation;
- changing an existing HTTP status or removing an existing response field;
- claiming production readiness before implementation evidence exists;
- inventing client-visible distinctions that weaken authentication or
  anti-enumeration policy.

## Evidence-based current state

The September 2026 audit found:

- 128 production constructions of `ApplicationException(ErrorCode...)`: Accounts 30,
  BFF 4, Expense Core 87, Notifications 7;
- current usages: `ERR_02` 42, `ERR_03` 30, `ERR_04` 2, `ERR_05` 31,
  `ERR_06` 17, `ERR_08` 1, and `ERR_11` 5;
- 181 explicit `throw` expressions, 184 `require` calls, 53 catches, and nine REST
  exception handlers requiring classification rather than blind replacement;
- only a small set of custom types: `ApplicationException`,
  `RateLimitStoreUnavailableException`, `InvalidSyncCursorException`,
  `UpstreamServiceException`, `InvalidEnvelopeException`, and mail exceptions;
- four `catch (Throwable)` sites in notification/email consumer adapters that can
  swallow fatal JVM failures;
- REST paths that publish application, root-cause, binding, conversion, optimistic
  locking, and `IllegalArgumentException` text;
- tests that currently assert some of those leaks and therefore need secure contract
  replacement, not preservation;
- controller advice does not cover all servlet/reactive security failures;
- the BFF drops upstream identity and reconstructs errors from status, sometimes with
  a newly generated request ID;
- message retry/poison/acknowledgement decisions are inconsistent, while scheduled
  work lacks a shared typed terminal boundary;
- semantic inconsistencies such as expense deletion reporting authentication failure,
  profile absence alternating between 401/404, archived-group mappings differing,
  and quota exhaustion sharing a code with quota-store outage.

The current contract has eight symbolic public codes and rejects extra fields through
`additionalProperties: false`. OpenAPI files duplicate reduced Problem schemas,
Accounts/Notifications include bodyless security responses, Expense Core lacks broad
non-2xx Problem coverage, and the existing contract validator primarily proves JSON
parseability rather than semantic parity. `ERR-12` is an HTTP 200 governance marker,
not a failure, and is retired without replacement.

## Accepted decisions

### Identity and catalog

- Use the six-character machine form `DMLCEE`; derive `DM-L-C-EE` only for display.
- Store and transmit codes as strings.
- Freeze published numeric code, name, namespace meaning, and legacy alias forever.
- Allocate `01`-`89`, reserve `90`-`98`, and use `99` only for registered
  unclassified fallbacks.
- Make the YAML catalog and domain registry build-time authorities.
- Generate or explicitly compile definitions/manifests; runtime YAML parsing and
  discovery are prohibited.
- Define HTTP, GraphQL, retry, disclosure, severity, headers, ownership, lifecycle,
  and runbook metadata explicitly. None is inferred from digits.

### Public compatibility

- Keep API v1 `code` symbolic and add optional `numericCode` and `errorName` first.
- Do not add `legacyCode` while `code` already carries that value.
- Preserve `type`, `title`, `status`, `source`, `requestId`, `detail`, `timestamp`,
  and existing violation behavior until a reviewed change says otherwise.
- Add `errorId`, `component`, `operation`, and retry metadata only after field-level
  privacy/cardinality and compatibility decisions.
- Preserve existing statuses during rollout. Any semantic status correction receives
  its own contract task and client migration.
- Preserve known upstream identity through the BFF; do not remap by status or text.

### Exceptions and outcomes

- Use one minimal governed exception base. Category digits do not become an inheritance
  hierarchy.
- Bake a default definition into a useful leaf type.
- Permit manual override only through a narrow owner-defined semantic family. An
  arbitrary definition injection point is forbidden.
- Give every predictable escaped failure a definition, but create a leaf class only
  when typed catch/recovery, reusable construction, structured diagnostics, or a public
  library boundary justifies it.
- Prefer typed results for frequent expected rejection paths when measurement shows
  exception stack capture is material.
- Prohibit deliberate generic throws in owned production code; catch generic
  dependency/framework failures only at governed boundaries.

### Containment and fatality

- Public mappers read only catalog metadata and approved diagnostics, never exception
  or cause text.
- Catch non-fatal `Exception` at web boundaries. Do not broadly catch `Throwable`.
- Re-throw VM/linkage/thread fatal failures, preserve cancellation, and restore thread
  interruption.
- Configure static sanitized container fallbacks for failures occurring outside
  application handlers.
- Log once at the final owning boundary; expected client errors normally omit stacks.

### Performance and observability

- Direct properties and static tables only on hot paths; no reflection, classpath
  scanning, annotation discovery, `ServiceLoader`, regex, or message parsing.
- Preconstruct definitions; do not parse/format codes per request.
- Keep metric labels bounded and never label by request/user/resource/message/locale.
- Benchmark stack capture, mapping, serialization, and allocation; load-test regional
  deployment assumptions before acceptance.

## Target architecture

```text
contracts/errors/domains.yaml ─┐
contracts/errors/catalog.yaml ─┼─ build-time typed validator/code generation
                               │
                               └─ static ErrorDefinition objects/manifests
                                                │
typed result or SquarewiseException ────────────┤
                                                │
                         one semantic translation boundary
                                                │
       ┌──────────────────┬─────────────────────┼───────────────────┐
       │                  │                     │                   │
 REST/security       GraphQL/WebSocket      messaging          scheduler/startup
 Problem Details     extensions/close       attempt/parking    terminal records
       │                  │                     │                   │
       └──────────── safe catalog fields + request/trace correlation ───────────┘
```

Transport adapters depend on the definition interface. Domain/application code does
not depend on Spring HTTP, GraphQL, RabbitMQ, or logging implementations. This applies
Dependency Inversion and Ports/Adapters without adding speculative layers.

## Catalog and contract shape

Each exact record includes:

```yaml
numericCode: "213201"
errorName: GROUP_NOT_FOUND
legacyCode: NOT_FOUND
lifecycle: ACTIVE
domain: { code: 2, name: Expense Core }
module: { code: 1, name: Groups }
layer: { code: 3, name: Domain }
category: { code: 2, name: Missing }
sequence: 1
title: Group not found
safeDetail: Group not found
messageKey: errors.group.notFound
owner: expense-core/groups
source: expense-core
component: groups
operation: group.lookup
transports: [REST, GRAPHQL]
httpStatus: 404
graphqlClassification: NOT_FOUND
legacyCompatibility: PRESERVE
retryPolicy: NEVER
severity: INFO
requiredHeaders: []
disclosure: RESOURCE_HIDDEN_WHEN_UNAUTHORIZED
introducedIn: pending
deprecatedIn: null
retiredIn: null
replacedBy: null
runbook: docs/operations/runbooks/errors/group-not-found.md
```

The implemented schema should use typed enums and conditional requirements: HTTP
metadata only for HTTP transports, GraphQL metadata only for GraphQL, runbooks for
critical/retryable/data/availability errors, and lifecycle fields when deprecated or
retired. The validator proves digit decomposition, registry ownership, global name and
code uniqueness, monotonic non-reuse, immutable-history compatibility, safe text
bounds, known source/component/operation values, and valid replacement links.

## Complete compatibility categories

The implementation inventory must cover at least:

- request syntax, conversion, bean validation, missing values, path/query/header
  bounds, IDs, cursors, content type, method, negotiation, body size, and output
  serialization;
- unauthenticated, malformed/expired/revoked/replayed credentials, CSRF, origin,
  object authorization, workload authority, anti-enumeration, quotas, and security
  provider/key-store outages;
- resource absence, lifecycle state, duplicate/unique/idempotency/version conflict,
  financial/domain policy, rounding, currency, membership, balance, and sync replay;
- known/unknown persistence constraints, deadlocks, lock/operation timeouts, connection
  pools, schema/migration/configuration, corruption, and commit ambiguity;
- dependency DNS/connect/TLS/protocol/decode/status/circuit/timeout/unavailability;
- event envelope/version/schema, duplicate, poison, transient retry, exhaustion,
  parking, acknowledgement, broker outage, and graceful cancellation;
- scheduler locks, empty/partial/poison batches, item isolation, retry exhaustion, and
  startup/shutdown failures;
- GraphQL parse/validation/resolver/serialization/aggregation, WebSocket handshake,
  quota, disconnect, replay, fanout, and upstream identity preservation;
- logging/tracing/metrics degradation, ID generation, unknown non-fatal errors, fatal
  JVM errors, cancellation, and interruption.

Per-context candidate identities are in [errors/README.md](errors/README.md). The
catalog-freeze task must walk every source and contract line; candidate lists are a
floor, not permission to create speculative client contracts.

## Ordered implementation strategy

Implementation begins only after the tasks below are registered. Each increment is
small, independently reviewable, documented, tested, and committed. Contract changes
precede producer changes; consumer tolerance precedes producer emission.

### Phase 0: governance and frozen evidence

#### [`ERRC-02`](../tasks/details/ERRC-02.md): Freeze audit and baseline behavior

- Depends on: `ERRC-01`.
- Owns: task detail, generated audit reports, no production source.
- Deliverables: repeatable source/contract inventory; current response/status/header
  fixtures; security, messaging, scheduler, startup, and generic-throw inventory;
  accepted semantic corrections list.
- Patterns/principles: characterization testing, single source of truth, evidence over
  assumption, KISS.
- Validation: repository searches, current test suite, contract snapshots, manual
  reconciliation of every throw/catch/boundary.
- Evidence: versioned inventory with counts, commands, limitations, and diff review.

#### [`ERRC-03`](../tasks/details/ERRC-03.md): Freeze registries and catalog schema

- Depends on: `ERRC-02`.
- Owns: `contracts/errors/domains.yaml`, new catalog JSON Schema, catalog lifecycle
  history, architecture decisions.
- Deliverables: accepted namespaces, metadata enums, lifecycle/non-reuse rules,
  conditional schema, compatibility classification.
- Patterns/principles: Repository Pattern for allocation, immutability, explicit
  contracts, Open/Closed.
- Validation: schema validation, duplicate/registry/sequence fixtures, breaking-history
  fixture tests.
- Evidence: reviewed schema examples for REST, GraphQL, async, startup, deprecated, and
  retired records.

#### [`ERRC-04`](../tasks/details/ERRC-04.md): Allocate and review the complete error catalog

- Depends on: `ERRC-03`.
- Owns: `contracts/errors/error-catalog.yaml`, per-context guides.
- Deliverables: exact record for every audited/predictable failure, legacy mapping,
  status/classification, disclosure, retry, headers, owner, runbook links; retire
  `ERR-12` without replacement.
- Patterns/principles: domain ownership, semantic translation, least disclosure,
  YAGNI (no distinction without remediation value).
- Validation: source-to-catalog and contract-to-catalog reconciliation; security and
  operations review.
- Evidence: zero unexplained audited scenarios and explicit intentional collapses.

### Phase 1: contract-first compatibility

#### [`ERRC-05`](../tasks/details/ERRC-05.md): Define the additive Problem Details contract

- Depends on: `ERRC-04`.
- Owns: `contracts/errors/problem.schema.json`, canonical examples.
- Deliverables: optional `numericCode`/`errorName`, field bounds, violation limits,
  headers and content negotiation, static internal fallback; existing `code` retained.
- Patterns/principles: tolerant-reader migration, RFC Problem Details adapter,
  backward compatibility.
- Validation: positive/negative JSON Schema fixtures, old-response compatibility,
  redaction strings, `additionalProperties` decision.
- Evidence: both old-only and additive responses validate during the transition.

#### [`ERRC-06`](../tasks/details/ERRC-06.md): Reconcile all REST OpenAPI contracts

- Depends on: `ERRC-05`.
- Owns: all REST OpenAPI contracts and API status documentation.
- Deliverables: one equivalent Problem schema per contract or validated shared source;
  response bodies/headers for all applicable non-2xx statuses; explicit justified
  bodyless negotiation cases.
- Patterns/principles: contract-first, DRY through generation/reference where tooling
  supports it, explicit compatibility.
- Validation: full OpenAPI parse/dereference, schema parity, response coverage, example
  validation, breaking diff.
- Evidence: endpoint/status coverage matrix for all three REST services.

#### [`ERRC-07`](../tasks/details/ERRC-07.md): Define GraphQL and WebSocket error contracts

- Depends on: `ERRC-04`.
- Owns: GraphQL contract documentation/schema directives and live-update contracts.
- Deliverables: extension fields, classifications, upstream preservation, request ID,
  handshake/subscription/close/replay/fanout behavior.
- Patterns/principles: Adapter, semantic preservation, reactive context propagation.
- Validation: contract fixtures for upstream, BFF-owned, validation, cancellation, and
  malformed upstream errors.
- Evidence: exhaustive extension/close behavior table.

#### [`ERRC-08`](../tasks/details/ERRC-08.md): Define messaging and background error records

- Depends on: `ERRC-04`.
- Owns: event contracts, retry/parking attempt schema, scheduler/startup error docs.
- Deliverables: error identity location, attempt/terminal states, ack/retry/parking,
  idempotency, safe diagnostics, versioning; no mutation of business events.
- Patterns/principles: Transactional Outbox, idempotent consumer, poison-message
  isolation, bounded retry.
- Validation: JSON/event schema fixtures and failure-state transition model.
- Evidence: matrix from every consumer/scheduler/startup entry point to terminal state.

#### [`ERRC-09`](../tasks/details/ERRC-09.md): Upgrade contract validation and breaking-change gates

- Depends on: `ERRC-03`, `ERRC-05`-`ERRC-08`.
- Owns: typed tools under `tools/contracts/`, CI/Make wiring, fixtures.
- Deliverables: Python-typed validators for catalog/registry, OpenAPI dereference and
  parity, endpoint response coverage, GraphQL/event/source parity, immutable history,
  and breaking diff.
- Patterns/principles: fail fast, defense in depth, pure validation functions, precise
  Python types.
- Validation: `make python-typecheck`, validator unit fixtures, `make contracts`.
- Evidence: each forbidden mutation fails a fixture; no untyped containers/functions.

### Phase 2: static error core, no runtime discovery

#### [`ERRC-10`](../tasks/details/ERRC-10.md): Implement core value and metadata types

- Depends on: `ERRC-09`.
- Owns: narrowly separated files in `libs/errors/.../code/`.
- Deliverables: `ErrorCode`, layer/category/severity/retry/disclosure/transport enums,
  definition interface, KDoc, direct digit access.
- Patterns/principles: Value Object, Interface Segregation, immutability, SRP.
- Validation: construction/property/format tests, invalid boundary tests, allocation
  measurement.
- Evidence: no regex or formatting on normal response path; no ordinal-derived ID.

#### [`ERRC-11`](../tasks/details/ERRC-11.md): Generate or compile static catalogs

- Depends on: `ERRC-10`.
- Owns: build-time generator or explicit manifest inputs/outputs, generated-source
  configuration, catalog parity tests.
- Deliverables: direct static definitions for each owner, deterministic generation,
  reproducible checked/generated artifact policy.
- Patterns/principles: Code Generation/Manifest, single source of truth, DRY.
- Validation: clean regeneration diff, catalog parity, duplicate compile failure.
- Evidence: startup/request path contains no YAML parse, reflection, scanning,
  annotations, or `ServiceLoader`.

#### [`ERRC-12`](../tasks/details/ERRC-12.md): Implement governed exception and typed-diagnostics contracts

- Depends on: `ERRC-10`, `ERRC-11`.
- Owns: separate exception/diagnostic files in `libs/errors`.
- Deliverables: minimal base, internal shield type where needed, fatal/cancellation
  classifier, bounded diagnostics, constrained family override mechanism, KDoc.
- Patterns/principles: Exception Shielding, constrained Strategy, composition over
  inheritance, make invalid states unrepresentable.
- Validation: type-level override tests, cause retention, public mapper cannot access
  raw message, fatal/cancellation/interrupt tests.
- Evidence: arbitrary `ErrorDefinition` override is impossible on leaf APIs.

#### [`ERRC-13`](../tasks/details/ERRC-13.md): Add static policy and reflection/generic-throw gates

- Depends on: `ERRC-12`.
- Owns: lint/static-analysis rules, allowlist with expiry metadata, CI wiring.
- Deliverables: fail on deliberate generic constructors, raw numeric codes, direct
  Problem construction outside adapters, message/root-cause publication, reflective
  discovery, message matching, broad `catch(Throwable)` outside reviewed adapters.
- Patterns/principles: policy as code, narrow allowlists, fail closed.
- Validation: positive/negative source fixtures and repository scan.
- Evidence: every allowlist item has owner, reason, scope, and removal task.

#### [`ERRC-14`](../tasks/details/ERRC-14.md): Implement logging, metrics, and trace adapter

- Depends on: `ERRC-11`, `ERRC-12`.
- Owns: `libs/errors` observability adapter and configuration docs.
- Deliverables: log-once helper, severity/stack sampling, MDC/Reactor-context hygiene,
  bounded metrics, trace attributes, telemetry-failure fallback.
- Patterns/principles: Decorator/Adapter, bounded cardinality, privacy by design.
- Validation: MDC cleanup/concurrency, cardinality allowlist, no recursive telemetry,
  redaction tests.
- Evidence: expected 4xx and unexpected 5xx fixtures demonstrate different treatment.

### Phase 3: transport boundaries

#### [`ERRC-15`](../tasks/details/ERRC-15.md): Implement servlet Problem mapper and containment

- Depends on: `ERRC-05`, `ERRC-12`, `ERRC-14`.
- Owns: `libs/errors` HTTP DTO/mapper/advice and tests.
- Deliverables: direct registered mapping; explicit Spring exception table; bounded
  deterministic violations; safe catchall; required headers; media negotiation;
  serialization/async/container fallback.
- Patterns/principles: Adapter, Chain of Responsibility through explicit handlers,
  exception shielding, fail closed.
- Validation: handler matrix, adversarial leak corpus, response serialization failure,
  headers/content types, old/additive schema validation.
- Evidence: no response path reads `Throwable.message` or root cause.

#### [`ERRC-16`](../tasks/details/ERRC-16.md): Implement servlet and reactive security boundaries

- Depends on: `ERRC-15`.
- Owns: security entry points/access-denied handlers and app security tests.
- Deliverables: structured 401/403/404-hiding, CSRF/origin, challenge headers, token
  exception translation, reactive context identity.
- Patterns/principles: Adapter, least disclosure, defense in depth.
- Validation: MockMvc/WebTestClient filter-chain tests before controller invocation,
  security redaction and challenge fixtures.
- Evidence: no bodyless security response except explicitly documented negotiation.

#### [`ERRC-17`](../tasks/details/ERRC-17.md): Implement GraphQL mapper and upstream problem client

- Depends on: `ERRC-07`, `ERRC-12`, `ERRC-14`.
- Owns: `libs/errors` GraphQL adapter and BFF upstream problem model/client.
- Deliverables: identity-preserving parse/validation, BFF transport taxonomy, Reactor
  request ID, safe extensions, no status/message reconstruction.
- Patterns/principles: Anti-Corruption Layer, Adapter, semantic preservation.
- Validation: WireMock/upstream matrices, malformed problems, network taxonomy,
  cancellation and context tests.
- Evidence: known upstream codes survive byte-for-byte; unknown inputs become BFF code.

#### [`ERRC-18`](../tasks/details/ERRC-18.md): Implement messaging/background boundary toolkit

- Depends on: `ERRC-08`, `ERRC-12`, `ERRC-14`.
- Owns: shared messaging adapters and background-operation boundary.
- Deliverables: fatal/cancellation-safe wrapper, retry policy, terminal record, parking
  and ack outcome, scheduled item/batch boundary.
- Patterns/principles: Template Method only for stable lifecycle, Strategy for retry,
  idempotent consumer, bulkhead/item isolation.
- Validation: redelivery/crash/retry/parking/ack/fatal/cancel/interrupt/concurrency tests.
- Evidence: no reviewed path acknowledges fatal or loses a terminal failure.

### Phase 4: bounded-context migration

#### [`ERRC-19`](../tasks/details/ERRC-19.md): Migrate Accounts definitions and failures

- Depends on: `ERRC-16`, `ERRC-18`.
- Owns: Accounts sources/tests/contracts already prepared in Phase 1.
- Deliverables: all Accounts predictable failures use typed definitions/outcomes;
  controlled exceptions; email/background boundaries; legacy mapping preserved.
- Patterns/principles: domain ownership, Factory for constrained construction where
  useful, transaction/idempotency clarity, least disclosure.
- Validation: guide test matrix, application test suite, contract fixtures, source
  parity, generic-throw gate.
- Evidence: zero unexplained Accounts throws/catches and explicit auth oracle review.

#### [`ERRC-20`](../tasks/details/ERRC-20.md): Migrate Expense Core definitions and failures

- Depends on: `ERRC-15`, `ERRC-18`.
- Owns: Expense Core sources/tests/contracts already prepared in Phase 1.
- Deliverables: all use cases/repositories/outbox/schedulers migrated; financial
  transaction outcome maintained; semantic bugs corrected only where compatible.
- Patterns/principles: domain exceptions/outcomes, Repository translation, Unit of
  Work, Transactional Outbox, idempotency.
- Validation: guide test matrix, concurrency/transaction/commit ambiguity, catalog
  parity, application/integration tests.
- Evidence: zero unexplained throws/catches and audited posting/audit/sync/outbox state.

#### [`ERRC-21`](../tasks/details/ERRC-21.md): Migrate Notifications definitions and failures

- Depends on: `ERRC-15`, `ERRC-18`.
- Owns: Notifications sources/tests/contracts already prepared in Phase 1.
- Deliverables: inbox HTTP and every consumer/delivery adapter migrated; remove unsafe
  broad catches; explicit suppression/duplicate/retry/parking/ack outcomes.
- Patterns/principles: idempotent consumer, poison-message isolation, Adapter.
- Validation: guide matrix, broker/provider integration, crash/redelivery/fatality,
  application tests.
- Evidence: every message state is terminal, retried, or parked by documented policy.

#### [`ERRC-22`](../tasks/details/ERRC-22.md): Migrate BFF definitions and failures

- Depends on: `ERRC-17`.
- Owns: BFF sources/tests/contracts already prepared in Phase 1.
- Deliverables: resolver validation, upstream preservation, transport taxonomy,
  aggregation, subscriptions, fanout, context propagation; remove message matching.
- Patterns/principles: Anti-Corruption Layer, Adapter, reactive composition.
- Validation: guide matrix, GraphQL/WebSocket E2E, upstream fault injection.
- Evidence: no status-only mapping, random replacement request ID, or English matching.

#### [`ERRC-23`](../tasks/details/ERRC-23.md): Migrate shared libraries and startup failures

- Depends on: `ERRC-12`-`ERRC-18`.
- Owns: relevant library boundaries and startup tests; no business errors relocated.
- Deliverables: persistence/security/messaging/observability/IDs/config failures;
  library propagation/translation policy; safe startup termination.
- Patterns/principles: Ports/Adapters, domain ownership, fail fast, graceful degradation
  only where explicitly safe.
- Validation: library tests, startup fault injection, no-reflection/generic-throw gates.
- Evidence: each shared failure either stays platform-owned or has one documented
  semantic translation.

### Phase 5: clients, acceptance, scale, rollout

#### [`ERRC-24`](../tasks/details/ERRC-24.md): Update client fixtures, Bruno, and end-to-end acceptance

- Depends on: `ERRC-19`-`ERRC-23`.
- Owns: Bruno collections, E2E tests, acceptance docs.
- Deliverables: assertions for status, headers, legacy code, numeric code, name,
  request correlation, safe detail, violations, GraphQL extensions, live updates.
- Patterns/principles: consumer-driven compatibility, behavior over implementation.
- Validation: complete collections/E2E across services and failure injection.
- Evidence: old-client and new-client fixture suites both pass.

#### [`ERRC-25`](../tasks/details/ERRC-25.md): Security and privacy leakage campaign

- Depends on: `ERRC-24`.
- Owns: adversarial tests and security review evidence.
- Deliverables: corpus containing SQL, stack frames, class names, parser text, secrets,
  tokens, PII, control characters, oversized values, nested causes, invalid encodings.
- Patterns/principles: negative testing, defense in depth, data minimization.
- Validation: REST/GraphQL/WebSocket/container/log/trace response inspection and fuzzing.
- Evidence: zero prohibited public leakage; internal access/residency controls reviewed.

#### [`ERRC-26`](../tasks/details/ERRC-26.md): Performance, allocation, and regional load evidence

- Depends on: `ERRC-19`-`ERRC-25`.
- Owns: JMH/load scenarios, reports, performance budgets; not production claims.
- Deliverables: definition lookup, exception/result creation, mapping, serialization,
  MDC/Reactor context, validation list, concurrent failure storm benchmarks; regional
  latency/error-cardinality scenarios.
- Patterns/principles: measure before optimize, bounded resources, backpressure.
- Validation: repeatable JMH/allocation profiles and production-like load/fault tests.
- Evidence: recorded hardware/JVM/config, thresholds, regressions, and limitations.

#### [`ERRC-27`](../tasks/details/ERRC-27.md): Observability, alerts, and runbooks

- Depends on: `ERRC-14`, `ERRC-19`-`ERRC-23`.
- Owns: operations docs, dashboards/alerts/runbooks, SLO mappings.
- Deliverables: bounded dimensions, alert ownership, retry/data/availability runbooks,
  cross-region aggregation without region in identity, support lookup workflow.
- Patterns/principles: actionable alerting, cardinality control, operational ownership.
- Validation: synthetic error signals and runbook tabletop exercises.
- Evidence: every critical/retryable/data/availability definition resolves to an owner
  and tested runbook.

#### [`ERRC-28`](../tasks/details/ERRC-28.md): Dual-read/dual-write staged rollout

- Depends on: `ERRC-24`-`ERRC-27`.
- Owns: rollout configuration/docs/evidence.
- Deliverables: deploy tolerant consumers first, then additive producers by service;
  compatibility window, unknown-field behavior, canary and regional order, rollback
  triggers, catalog/version telemetry.
- Patterns/principles: Expand/Contract, canary, feature flag only where reversible,
  backward compatibility.
- Validation: mixed-version matrix, canary smoke/load, rollback rehearsal.
- Evidence: each region/service gate records field/status/schema and client health.

#### [`ERRC-29`](../tasks/details/ERRC-29.md): Make additive fields required after compatibility window

- Depends on: `ERRC-28` and explicit product/client approval.
- Owns: schemas/OpenAPI/GraphQL contracts and migration notice.
- Deliverables: require `numericCode`/`errorName`, remove transitional omission paths,
  retain symbolic `code` for v1.
- Patterns/principles: Expand/Contract completion, explicit breaking review.
- Validation: client adoption evidence, breaking diff, E2E and rollback plan.
- Evidence: approved compatibility-window exit; no inferred deadline.

#### [`ERRC-30`](../tasks/details/ERRC-30.md): Retire legacy infrastructure, not the v1 field

- Depends on: `ERRC-29`.
- Owns: old enum/mappers/dead code and final docs.
- Deliverables: remove `ERR-XX` runtime implementation and duplicate mappers; preserve
  v1 symbolic `code` from catalog while v1 exists; retain retired catalog history.
- Patterns/principles: Strangler completion, DRY, immutable history.
- Validation: source searches, complete suites, catalog parity, clean dependency graph.
- Evidence: zero old enum runtime references and no loss of contract history.

#### [`ERRC-31`](../tasks/details/ERRC-31.md): Optional future major-version numeric `code`

- Depends on: separately approved API major version; not part of this migration.
- Owns: future versioned contracts and client migration.
- Deliverables: decide whether `code` becomes numeric, symbolic compatibility field,
  deprecation timeline, SDK/client support.
- Patterns/principles: versioned contract, no silent breakage.
- Validation: major-version consumer certification and parallel-version tests.
- Evidence: explicit product/contract approval. Never inferred from ERRC-30 completion.

## Commit and tracking protocol

Every registered task:

1. reads repository context and its task detail;
2. declares objective, dependencies, owned paths, planned files, validation, and
   evidence before editing;
3. updates contracts/docs in the same increment as behavior;
4. provides KDoc for all public/non-trivial APIs;
5. runs declared validation and records exact commands/results/limitations;
6. reviews unrelated changes, generated artifacts, secrets, and version duplication;
7. commits one coherent increment with a descriptive message;
8. lets the coordinator update registry/board/progress with the commit hash.

Parallel work is allowed only with registered non-overlapping ownership. Shared files
such as catalogs, build logic, registry, and board remain coordinator-owned or require
an explicit handoff.

## Rollout and rollback

### Rollout order

1. Ship schemas and clients that tolerate absent/present new fields.
2. Ship static definition/core infrastructure dark, without public emission.
3. Enable additive fields for one service/canary, then Accounts, Expense Core,
   Notifications, and BFF according to dependency order.
4. Verify upstream identity preservation and mixed-version GraphQL behavior.
5. Expand by region while monitoring unknown-code, schema rejection, status/header,
   latency/allocation, log volume, and metric cardinality.
6. Keep fields optional through the announced compatibility window.
7. Require fields only through `ERRC-29` approval.

### Rollback rules

- Additive field emission can be disabled without reverting catalog identity.
- Never reuse or renumber a code emitted by a canary; deprecate/retire it if wrong.
- Rollback must preserve current symbolic `code`, status, headers, and safe response.
- A producer rollback cannot require a consumer rollback; tolerant readers deploy first.
- Messaging retry/parking schema changes require forward/backward readers before write.
- If latency, allocation, log cardinality, leakage, or client errors breach a gate,
  stop expansion and retain the last compatible contract.

## Risk register

| Risk | Impact | Required mitigation/evidence |
|---|---|---|
| Client rejects additive fields because schemas are closed | High | Tolerant clients/contracts first; mixed-version tests |
| Numeric identity collides or changes | High | Build-time authority, immutable history, duplicate/non-reuse gates |
| Arbitrary exception overrides recreate inconsistency | High | Narrow family types and compile-time negative tests |
| Cause/parser/framework text leaks | Critical | Catalog-only mapper, adversarial corpus, container fallback tests |
| Fatal error is swallowed/acknowledged | Critical | Fatal classifier, removal of broad catches, crash/ack tests |
| Security filter bypasses controller advice | High | Dedicated entry point/access-denied adapters and filter tests |
| BFF loses or fabricates upstream identity | High | Typed problem client, preservation fixtures, no status/text mapping |
| Error-class explosion harms maintainability | Medium | Leaf-class justification rule; typed outcomes/base otherwise |
| Reflection/scanning adds startup/request cost | Medium | Static generation/manifests and CI prohibition |
| Exact-code metrics create cardinality growth | Medium | Finite catalog budget, bounded labels, cardinality tests |
| Exception stack capture overloads expected hot paths | Medium | Profile first; typed results or narrowly approved suppression |
| Status correction breaks clients | High | Preserve v1; separate versioned contract decision |
| Async failure loses side-effect truth | Critical | Terminal attempt model, idempotency, transaction/ack fault tests |
| Multi-region text/privacy diverges | High | Global identity, localization keys, residency/access review |
| “10M ready” asserted without proof | High | Explicit JMH/load gates and recorded limitations |

## Documentation map

- [Canonical code standard](error-code-standard.md)
- [Domain/module registry](error-domain-registry.md)
- [Exception and boundary guide](error-handling-guide.md)
- [Per-context ownership guides](errors/README.md)
- [Current implemented flow](error-flow.md)
- [Machine-readable namespace proposal](../../contracts/errors/domains.yaml)

This plan is intentionally implementation-ready but not implementation-authorizing.
The next action after documentation acceptance is to register `ERRC-02` through the
required implementation sequence, assign non-overlapping owners, and begin with the
repeatable baseline, not with runtime code.
