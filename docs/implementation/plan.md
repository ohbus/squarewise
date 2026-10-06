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
# Error reporting evolution

ERR-01 through ERR-12 established the current symbolic v1 vocabulary and supporting
controls. `ERRC-01` now supplies a documentation-only successor design for immutable
six-digit domain/module identities. The existing runtime and contracts remain the
compatibility baseline until follow-on work is separately registered and implemented.

## Target model

The canonical identity is `DMLCEE`, displayed as `DM-L-C-EE`, and paired with an
immutable symbolic name. Domain/module identifies stable semantic ownership rather
than a service process. API v1 retains its existing symbolic `code` and first adds
optional `numericCode` and `errorName`; replacing `code` requires a future versioned
contract. `source`, component/operation where approved, request correlation, and any
occurrence-level error ID remain separate metadata.

The catalog is the build-time authority for identity, explicit transport mapping,
retry, severity, disclosure, safe text, ownership, lifecycle, and runbooks. Runtime
mapping uses preconstructed definitions or generated static manifests. Reflection,
classpath scanning, annotations, `ServiceLoader`, ordinal identity, exception-message
classification, and runtime YAML parsing are prohibited on operational paths.

## Delivery sequence

The registration-ready sequence is documented in
[the refactoring plan](../architecture/error-code-refactoring.md): freeze evidence and
catalog allocations; update contracts and tolerant consumers; implement the static
core and policy gates; harden REST/security, GraphQL/reactive, messaging, scheduler,
and startup boundaries; migrate each bounded context; then execute adversarial,
performance, observability, mixed-version, regional rollout, and rollback gates.

Each task owns non-overlapping paths, updates documentation with behavior, validates
before committing, and records exact evidence. Contracts precede producers; tolerant
readers precede additive writers.

## Exception and containment policy

Every predictable escaped failure receives a definition, but an exception class exists
only where typed catching/recovery, reusable construction, structured diagnostics, or
a public library boundary warrants it. A useful leaf bakes in its default definition;
manual override is permitted only through a narrow owner-defined compatible family.
Owned production code does not deliberately throw generic exceptions.

Public adapters read only catalog-controlled text and approved bounded diagnostics.
They never expose exception/root-cause text or stack traces, including for 500s.
Fatal JVM failures propagate for process restart, cancellation is preserved, and
interrupt status is restored. This is broader boundary safety, not a blanket
`catch(Throwable)` policy.

## Completion gate

Documentation completion does not establish runtime completion. The future workstream
finishes only when every audited boundary has catalog/contract/test evidence, all
public paths are redaction-safe, upstream identity is preserved, async terminal states
are explicit, static/no-reflection gates pass, metrics remain bounded, mixed-version
rollout and rollback are proven, and measured performance evidence supports, not merely
asserts, the scale target.

Canonical references:

- [Six-digit standard](../architecture/error-code-standard.md)
- [Domain/module registry](../architecture/error-domain-registry.md)
- [Exception and boundary guide](../architecture/error-handling-guide.md)
- [Per-context inventories](../architecture/errors/README.md)
- [Ordered implementation plan](../architecture/error-code-refactoring.md)
