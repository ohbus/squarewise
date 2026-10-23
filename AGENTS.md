# Working agreement

## Fresh-session context protocol

Before changing files, every new agent session must load the repository context in
this order:

1. Read this file and `README.md`.
2. Read [`docs/tasks/registry.yaml`](docs/tasks/registry.yaml) and
   [`docs/tasks/README.md`](docs/tasks/README.md). If
   `execution_tracking.cutover_status` is `complete`, follow the mapped GitHub
   issue and Project for live state; otherwise use
   [`docs/tasks/board.md`](docs/tasks/board.md) as the fallback queue. Read
   [`docs/tasks/progress.md`](docs/tasks/progress.md) for verified history.
3. Read [`docs/implementation/plan.md`](docs/implementation/plan.md) and the
   relevant task detail under [`docs/tasks/details/`](docs/tasks/details/).
4. Read the applicable product, architecture, contract, quality, and operations
   documents before making design or implementation decisions:
   - Product scope: [`docs/product/mvp.md`](docs/product/mvp.md)
   - Architecture: [`docs/architecture/overview.md`](docs/architecture/overview.md),
     [`docs/architecture/project-structure.md`](docs/architecture/project-structure.md),
     [`docs/architecture/error-flow.md`](docs/architecture/error-flow.md),
     [`docs/architecture/pagination.md`](docs/architecture/pagination.md)
   - Technology decisions: [`docs/implementation/technology-decisions.md`](docs/implementation/technology-decisions.md)
   - API and event contracts: [`contracts/`](contracts/),
     [`docs/api/implementation-status.md`](docs/api/implementation-status.md)
   - Quality and acceptance: [`docs/quality/testing-strategy.md`](docs/quality/testing-strategy.md),
     [`docs/quality/acceptance.md`](docs/quality/acceptance.md),
     [`docs/quality/programming-principles.md`](docs/quality/programming-principles.md),
     [`docs/quality/coding-guidelines.md`](docs/quality/coding-guidelines.md)
   - Operations: [`docs/operations/README.md`](docs/operations/README.md),
     [`docs/operations/quickstart.md`](docs/operations/quickstart.md),
     [`docs/operations/ci.md`](docs/operations/ci.md),
     [`docs/operations/compose-topology.md`](docs/operations/compose-topology.md),
     [`docs/operations/intellij.md`](docs/operations/intellij.md)
   - Working agreement: [`docs/working-agreement.md`](docs/working-agreement.md)
5. Inspect `git status --short`, recent commits, and the relevant owned paths.
   Confirm that the task is registered, its dependencies are complete, and its
   owned paths do not overlap another active task without a handoff.
6. State the task ID, objective, dependencies, owned paths, planned files,
   validation commands, and expected evidence in the progress update before
   editing.

The complete documentation navigation is maintained in [`docs/README.md`](docs/README.md).
Use it to discover documents that are not part of every fresh-session read, including
security, reviews, extended operations, quality, implementation, contract, and visual
documentation. The task-detail directory is intentionally navigated through the
[`docs/tasks/registry.yaml`](docs/tasks/registry.yaml), which identifies the
relevant detail file and mapped GitHub issue for active work.

Do not begin implementation from memory or from an isolated prompt. Every delegated
subagent must explicitly read and strictly adhere to all relevant documentation
listed above before beginning any implementation. If these documents disagree,
stop and resolve the conflict through the coordinator. Accepted user decisions
control scope, the authority selected by the registry cutover controls execution
state, and contracts control externally visible behavior.

## Source-of-truth map

| Concern | Authoritative location | Required update |
|---|---|---|
| Scope and product behavior | `docs/product/mvp.md` | Update the product document and a linked task before implementation |
| Architecture and boundaries | `docs/architecture/` | Update the relevant decision before changing service boundaries |
| Technology and versions | `docs/implementation/technology-decisions.md`, `gradle/libs.versions.toml`, `infra/versions.env.example` | Record evidence for version changes; never duplicate versions in build files |
| REST, GraphQL, events, errors | `contracts/` | Change and validate the contract before changing an API |
| Work status, assignment, priority, schedule | Mapped GitHub issue and `Squarewise Delivery` after cutover; registry/board before cutover | Update the active authority before work starts and when execution state changes |
| Task specification and stable ID | `docs/tasks/details/`, `docs/tasks/registry.yaml` | Keep objective, owned paths, acceptance, validation, and issue mapping synchronized |
| Verification history | `docs/tasks/progress.md` | Record commands, evidence, limitations, and commit hashes for every completed increment |
| Test policy | `docs/quality/` and `tests/` | Add meaningful tests for public behavior and update acceptance evidence |
| Operations and local development | `infra/`, `Makefile`, `docs/operations/` | Keep clone-and-run workflows and version references synchronized |

After cutover, GitHub is the execution state machine; before cutover, the
registry and board remain the fallback. The progress ledger is the evidence log,
and task detail files are the implementation specifications. None are optional.

## End-of-task protocol

Before reporting completion:

- Update the owned task detail with decisions, implementation notes, and known
  limitations.
- Update all affected repository documentation across `docs/` (architecture,
  operations, API status, workflows, contracts) as changes are made; never defer
  documentation updates.
- Ensure all new and touched public classes, interfaces, functions, endpoints,
  and non-trivial domain logic have structured Javadoc/KDoc doc comments (`/** ... */`)
  specifying intent, parameters, return types, invariants, and edge cases.
- Ask the coordinator to update the active GitHub issue/Project and durable task
  records if you are not the coordinator; never edit shared tracking directly.
- Run the task's declared validation commands and record exact results.
- Review the diff for unrelated files, generated artifacts, credentials, and
  duplicated dependency versions.
- Commit one coherent increment with a descriptive message. The coordinator then
  records the commit hash in `docs/tasks/progress.md`.
- Leave the working tree clean unless an explicitly documented handoff requires
  otherwise.

## Delivery order

Write and review the documentation, API contracts, implementation task details,
and task registry before creating application/build scaffolding. The coordinator
records the documentation gate in `docs/tasks/registry.yaml`. Product feature
implementation is a later milestone; the initial execution delivers reviewed
contracts and a verified scaffold with a reserved UI workspace.

## Task tracking and parallel work

- Every delegated subagent must have a registered task ID before starting work.
- Only the coordinator edits `docs/tasks/registry.yaml` and the fallback
  `docs/tasks/board.md`, or changes shared GitHub Project fields and workflows.
- Each task has one owner, dependencies, owned paths, acceptance criteria, and
  verification evidence. Report blockers and scope changes promptly.
- Register child tasks before further delegation. Do not create untracked work.
- Do not edit another agent's files or shared build files without a handoff.
- Completion requires reviewed deliverables and verification, not merely an
  agent's completion message. Keep planning and implementation status separate.
- User decisions take precedence over earlier proposals. Never claim unrun checks
  passed or that scaffold completion means product features are implemented.

## Accepted architecture

- Kotlin, Spring Boot, Gradle Kotlin DSL; independently deployable apps under
  `app/`; narrowly scoped technical libraries under `libs/`.
- Spring Data JPA with Hibernate is the persistence default. Use targeted native
  SQL for locking, background claims, and specialized queries. Flyway owns schema
  changes; Hibernate validates. Disable Open Session in View.
- Three REST services: Accounts, Expense Core, Notifications. A database-free
  GraphQL BFF uses HTTPS operations and WebSocket change subscriptions.
- PostgreSQL owns financial state. RabbitMQ and a transactional outbox handle
  background delivery. No cross-service SQL, entities, or repositories.
- Each service is internally modular. Financial postings, audit, sync changes,
  and outbox records share one local transaction in Expense Core.
- All features remain free. No payment processing or billing in the MVP.
- UI implementation is deferred; reserve `app/web/` without selecting a framework.
- Offline scope: cached reads and queued expense creation only. All active group
  members can edit online; repayments update immediately with audit history.

## Validation

Use actual dependency resolution/build evidence to select a compatible stable
toolchain. Earlier planning version numbers are candidates, not proof that an
artifact exists. Record any correction and its evidence before changing the
version baseline. Do not choose preview versions or silently lower requirements.

## Programming principles (mandatory)

### Python typing rule

All repository Python code, including tests and operational scripts, must use
strong static typing. Every function and method must declare parameter and
return types; public containers and structured payloads must use precise generic
types or typed models rather than untyped `dict`/`list`; and new dynamic escape
hatches require a documented justification. Use `uv`/`uvx` for Python tooling
and isolated environments; do not install project tooling into the system
interpreter. Run `make python-typecheck` for Python changes. CI owns the same
gate, and future sessions must preserve this rule when adding or modifying
Python code.

All implementation and review work must follow
[`docs/quality/programming-principles.md`](docs/quality/programming-principles.md)
and [`docs/quality/coding-guidelines.md`](docs/quality/coding-guidelines.md).
Choose the simplest current solution; separate concerns; keep modules cohesive
and loosely coupled; hide implementation details behind narrow contracts; keep
one authoritative source for each rule; preserve command/query separation;
prefer composition and dependency inversion; and write for maintainers.

In particular:
- **Pre-implementation design thinking:** Think carefully through the implementation
  details, edge cases, domain invariants, and data flows before writing any code.
  Never rush to edit files; thoroughly read existing code first to become well-versed
  in existing capabilities, analyze constraints, and choose the cleanest path forward.
- **Search before building & maximum reusability:** Before implementing any new logic,
  inspect existing utilities, libraries (`libs/ids`, `libs/errors`, etc.), helpers,
  and domain services to verify whether similar functionality already exists. Reuse,
  compose, and generalize existing patterns instead of rewriting or duplicating.
- **Write/generate minimal code:** The explicit goal is to generate as little new
  code as possible while satisfying requirements. Avoid boilerplate, verbose
  abstractions, or speculative features. Keep modules compact, focused, and cohesive.
- **Cognitive simplicity & readability:** Code must be cognitively light and easy
  to reason about for any human reviewer. Prefer straightforward, idiomatic Kotlin
  over convoluted or clever constructs. Maintain clear, self-explanatory naming,
  small functions, and obvious data flow.
- **KISS & DRY (Minimal code duplication):** Keep solutions as simple as possible
  (KISS) while rigorously avoiding duplicated business logic, rules, or representations
  (DRY). Derive consumers from single authoritative sources of truth.
- **Proven enterprise design patterns:** Use well-established design patterns where
  appropriate (e.g. Repository pattern, Adapter/Port pattern, Factory/Builder,
  Strategy, and Transactional Outbox) without introducing speculative or gratuitous
  abstractions (YAGNI).
- **Strict SOLID file separation:** Every class, entity, repository, and service/store
  must have one coherent, single responsibility and reside in its own dedicated file.
  Never combine JPA `@Entity` classes, Spring Data `@Repository` interfaces, and
  business `@Service` / store adapter implementations in a single file. Follow
  enterprise-grade modular packaging (`entities/`, `repositories/`, `services/` or
  cohesive feature-slice files with one primary type per file).
- **Continuous documentation updates:** Keep documentation synchronized with
  implementation. As tasks make progress, immediately update affected architecture,
  operations, API, and task specifications; never defer documentation updates to a
  future session.
- **Javadoc / KDoc-style coding comments:** Add structured doc comments
  (`/** ... */`) to public classes, interfaces, methods, models, and non-trivial
  domain logic, detailing intent, parameters, return values, invariants, and edge
  cases for long-term maintainability.

Every progress update must state how the change follows these principles. Any
intentional exception must be recorded in the task detail with its trade-off,
owner, and expiry or review condition.
