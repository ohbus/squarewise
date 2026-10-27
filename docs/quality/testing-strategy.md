# Testing strategy

Use the approved toolchain evidence in the architecture documents; earlier
proposed version numbers are not dependencies until resolved successfully.

QA-07's contract-driven public-interface matrix is maintained in
[`public-interface-coverage.md`](public-interface-coverage.md). It is the
required cross-layer checklist for every REST method/path pair, GraphQL field,
and WebSocket subscription lifecycle. Contract parsing, controller tests, and
mocked gateway tests are not interchangeable with live integration evidence.

The repository-wide implementation-to-test gap register is
[`test-coverage-gap-audit.md`](test-coverage-gap-audit.md). It is the source
for missing branch, unit, persistence/messaging integration, and deployed E2E
acceptance criteria; this strategy document and the public operation matrix
must link to it rather than treating line coverage or operation inventory as
complete behavior coverage.

Coverage integrity is part of the quality contract: close gaps by adding
behavioral tests and evidence against the existing implementation. Do not
delete implementation logic, remove branches, or weaken public contracts merely
to improve JaCoCo results. A behavior change requires an observed defect or
explicit requirement, a focused regression test, and documented justification.
The corrective history for the current branch is recorded in
[`test-coverage-change-audit.md`](test-coverage-change-audit.md).
Reports must identify the highest evidence level actually executed.

For QA-10 closure, the gap audit's per-record contract is mandatory: every
remaining JaCoCo method record must name its exact test, invariant, evidence
layer, and side-effect assertion. Every public operation must also account for
authentication, authorization, input/failure behavior, durable state,
asynchronous state, replay/concurrency, isolation, and redaction. A source
reference or line-coverage increase is discovery evidence only; it is not a
substitute for the required test layer. The current QA-10 discovery baseline
contains 39 branch-gap method records and 78 missed branches, while the
operation inventory contains 54 operations. Every operation now has a literal
E2E source signal, but source presence is not complete acceptance evidence. These are tracked acceptance work, not
permission to delete implementation branches or infer closure from a shared
journey.

The audit's event-type and routing inventory is also authoritative for
messaging gaps. It distinguishes local parser/unit evidence from the required
versioned event registry, producer/consumer mapping, outbox atomicity, real
RabbitMQ routing/acknowledgement/retry/DLQ, deduplication, and credential
redaction artifacts. An event literal or mocked listener test does not close
those integration or deployed criteria.

For the practical test layout, commands, environment prerequisites, CI gates,
coverage dimensions, and contribution workflow, see the
[`test-operations-guide.md`](test-operations-guide.md).

The reviewed local Qodana findings and false-positive decisions are recorded in
[`qodana-audit.md`](qodana-audit.md).

| Layer | Tooling and purpose |
| --- | --- |
| Domain | JUnit Jupiter and AssertJ; deterministic allocation, balances, schedule policy and authorization decisions |
| Properties | Kotest property library under JUnit; allocation invariants with reproducible seeds |
| Persistence | Testcontainers PostgreSQL; real constraints, JPA versioning, locks, Flyway and native SQL interaction |
| Messaging | Testcontainers RabbitMQ; confirms, retries, deduplication, worker crashes and broker outages |
| HTTP/GraphQL | Spring test clients and GraphQlTester; status/error mapping, real authorization and contracts |
| Architecture | ArchUnit; no domain dependency on transport, no cross-service persistence imports, module APIs only |
| Contracts | OpenAPI validation and breaking-change diff; GraphQL validation/diff; JSON Schema event/example checks |
| Capacity | k6 or equivalent pinned runner; representative HTTP and subscription workloads |
| Supply chain | Dependency inventory/SBOM, vulnerability and secret scanning with reviewed findings |

Every JVM subproject applies JaCoCo and emits XML and HTML reports. Product
milestones must add a meaningful threshold for their changed modules; the empty
scaffold does not impose a misleading global percentage because most behavior
does not exist yet.

The `coverage.yml` workflow publishes aggregate JVM line coverage as
informational GitHub Code Quality data for `master` and master-targeting pull
requests. It also retains the Cobertura artifact for review and attests it when
the workflow token permits. This publication is a visibility aid, not a test
or acceptance claim: line coverage does not replace the behavioral, persistence,
messaging, E2E, security, or redaction evidence required by QA-07 and QA-10.
Python coverage is intentionally outside this initial JVM report scope.

Scaffold checks only need meaningful boot/context, packaging, module and contract
verification. Do not create placeholder business tests that merely repeat empty
implementations. Introduce business suites with their implementing task.

The first scaffold quality slice contains deterministic allocation tests and a
Spring application-context smoke test. `tests/e2e/contract-smoke.sh` validates
the public contract and every Compose configuration. It is an infrastructure
smoke test, not a claim that product journeys work; QA-01 adds API-driven E2E
journeys after those endpoints exist.

Contract tooling may be Node-based without making the backend or reserved UI a
Node application. Pin versions and lock dependencies when introducing those tools.
Keep portable entry points runnable without a specific CI provider.

## Persistence-specific checks

Flyway owns schema evolution; Hibernate runs validation, not schema mutation.
Disable Open Session in View. API responses use DTOs rather than entities. Exercise
lazy relationship access inside declared transaction boundaries and detect N+1
queries on representative list endpoints through measured SQL counts.

Native update/claim queries must explicitly account for pending JPA flushes and
stale persistence-context state. Verify version checks cannot be bypassed by a
native mutation. Simultaneous workers use independent transactions/connections;
tests enclosed in one automatically rolled-back transaction cannot prove these
behaviors. Bound timeouts so deadlocks fail promptly with useful traces.

## CI ordering

Run fast schema/style/unit/architecture checks before database and broker suites.
Package each app, scan artifacts, then run integrated acceptance for product
changes. Load and destructive recovery suites run in isolated, explicitly selected
environments. Never execute them against a developer's reused personal database
or production. Retain failing seeds and logs with sensitive values redacted.

The scaffold phase must document the exact supported local commands after they
exist. This file intentionally does not claim a command or test suite already
exists. Hosting-specific tests and browser tests are later milestone work.
