# DOC-17C: Current implementation and contract drift review

Date: 2026-09-18  
Scope: the uncommitted CORE-18, BFF-06, NOT-09, and ACC-05 increments, their
contracts/tests, and the broader MVP/tracker claims. This is a review artifact;
it does not change implementation or task state.

## Executive summary

The four increments add real code, contract declarations, and focused tests, but
they should not yet be treated as four fully verified completed tasks. CORE-18
does not implement its stated audit behavior or the architecture's sync/outbox
side effects. BFF-06 does not populate members for the `groups` query and has no
test for its new mutation or REST gateway calls. NOT-09 is the closest to its
declared scope. ACC-05 implements a bounded batch lookup but leaves authentication,
response ordering/duplicate semantics, and contract coverage ambiguous.

More broadly, the registry marks every original product, acceptance, and launch
task done (apart from the lint task), while task details and source inspection
still identify unimplemented MVP behavior and unexecuted launch evidence. Passing
unit tests and schema validation therefore must not be read as product or launch
completion.

## Findings

### Critical: tracker completion exceeds implemented MVP and evidence

`CORE-01`, `CORE-05`, `CORE-06`, `QA-01`, and `OPS-02` are marked `done`, but the
authoritative product scope still requires group archive, named placeholders,
membership administration/removal, invitation revocation, recurrence transport
and invalid-membership notification, and authorized search/CSV export
(`docs/product/mvp.md:16-32`). There are no REST mappings for those group,
recurrence, or export operations. `docs/tasks/details/CORE-05.md:28-36` and
`docs/tasks/details/CORE-06.md:28-32` also explicitly describe material work as
pending. `docs/tasks/details/OPS-02.md` says its probes are not evidence of cloud
capacity, backup restoration, or provider pricing, contrary to the registry's
done state and the public-launch gate in `docs/quality/acceptance.md:13-17`.

Recommended follow-up: reopen the overstated parent tasks or, preferably, mark
their completed increments accurately and register small children for (1) group
archive/placeholders/member administration/invite revocation, (2) recurrence REST
management plus invalid-membership notification, (3) authorized persistent
search and bounded CSV transport, (4) authenticated real-dependency acceptance,
and (5) actual restore/capacity/cost evidence. Do not use `done` for launch gates
until the prescribed evidence exists.

### High: CORE-18 rename is not an auditable synchronized group change

`JpaGroupStore.update` only checks membership, changes the entity, and increments
its numeric revision (`JpaGroupStore.kt:132-139`). `GroupEntity` has no optimistic
`@Version` field and the update does not lock the group, so concurrent renames can
read the same revision and lose one update. It also writes no audit record, sync
change, or outbox event, despite the task objective promising audit persistence
and the accepted architecture requiring committed effects/change hints to flow
through local transactions and the outbox. The tests cover a single rename and a
non-member, not concurrency or atomic side effects.

Recommended follow-up: register a narrowly owned CORE task to define expected
revision/concurrency semantics in the REST contract, lock or version the update,
and atomically append audit, sync, and outbox records. Add a two-transaction
PostgreSQL concurrency test and public-interface evidence. Until then, narrow the
CORE-18 objective/evidence rather than claiming audited behavior.

### High: BFF-06 does not satisfy its list/member and test acceptance

The `groups` resolver still directly returns `gateway.listGroups(...)`
(`GroupGraphqlController.kt:30-35`), and `listGroups` deserializes only group
responses. Members are fetched only by `getGroup` (`RestGateway.kt:178-187`). Thus
the non-null GraphQL `Group.members` field is an empty default for the groups list,
not the member discovery promised by the task detail. In addition,
`GroupGraphqlControllerTest` adds assertions only to the existing single-group
test; it has no `updateGroup` test, invalidation assertion for that mutation, or
gateway HTTP test for `updateGroup`/`listMembers`. `getGroup` also converts a
member-fetch error into an empty list, hiding authorization or upstream failure
behind a successful non-null field, contrary to the product's explicit
partial/error-state requirement.

Recommended follow-up: keep BFF-06 open or add a small correction task. Decide
whether list queries must fan out (with a documented bound) or whether members
are resolved as a field/batched loader; do not silently return empty on upstream
errors. Add WebClient request/response tests, GraphQL transport tests, mutation
invalidation tests, and error-code propagation tests.

### High: live invalidation remains process-local, not architecture-complete

`updateGroup` emits directly into the current controller's in-memory fanout after
the REST call (`GroupGraphqlController.kt:45-55`). CORE-18 emits no outbox event,
and this path therefore cannot notify another BFF replica or a client connected
elsewhere. This conflicts with the RabbitMQ per-replica topology in
`docs/architecture/overview.md` and RT-01 in `docs/quality/acceptance.md:62-63`.

Recommended follow-up: make Expense Core the source of the committed group-change
event and connect each BFF replica's queue to the fanout. Retain direct emission
only if deduplication is defined, or remove it to avoid duplicate hints. Verify
two-replica delivery and reconnect/resync through an integration scenario.

### Medium: contract and operation-status documentation is stale/incomplete

`contracts/graphql/operation-mapping.md:14-17` still says `me`, `group`,
`createExpense`, and `recordRepayment` are planned, although their controllers
were committed in BFF-03; it also does not map the new `updateGroup` or members
field. New OpenAPI operations are inconsistent about common responses: ACC-05
declares 200/400 but no 401; CORE-18 declares 200/404 but no validation 400 or
401; NOT-09 declares 204/401/404 but uses the generic Problem response for 404.
The contracts validate structurally, but structural validation does not resolve
these behavioral omissions.

Recommended follow-up: create a contract-only reconciliation task to update the
operation map, apply the shared problem/error vocabulary consistently, state
authentication requirements, and add operation-level implementation status
rather than relying on a path-level marker that covers both GET and PATCH.

### Medium: ACC-05 batch semantics and security are underspecified

The request is bounded to 1..100 IDs and both stores return existing profiles,
which matches the basic task slice. However, neither the task nor OpenAPI defines
whether results preserve request order, retain duplicate IDs, or deliberately
deduplicate. JPA `findAllById` and the in-memory map scan provide no common order,
so callers cannot rely on positional correlation. The controller takes no
principal and its MVC tests intentionally call the endpoint without a user;
the contract likewise omits 401 even though the objective calls this an internal
service/client-aggregator operation and the architecture requires authenticated
service boundaries.

Recommended follow-up: specify keyed/order semantics and the caller authorization
model before BFF adoption. Prefer a response keyed by account ID or explicitly
guarantee unique input and stable request order. Add security-enabled MVC tests,
duplicate/order tests for both adapters, and a 101-ID boundary test.

### Low: NOT-09 is implemented but lacks transport/security edge evidence

The controller and both stores enforce subject scoping and persist `read=true`;
repeated marking is harmless. Focused tests cover success, unknown ID, wrong
subject, and persistence. Missing evidence is limited to security-enabled 401,
malformed UUID/problem mapping, and concurrent/repeated request behavior through
the HTTP boundary.

Recommended follow-up: keep any correction small: add authenticated transport
coverage and specify whether repeated marks remain 204 (recommended idempotent
behavior). No new persistence design is needed.

## Validation and limitations

- Inspected current `git status`, recent history, all four uncommitted diffs,
  relevant contracts, task details, product/architecture/quality documents, and
  controller mappings.
- `./gradlew :app:accounts:test :app:expense-core:test :app:notifications:test
  :app:bff:test --no-daemon` did not complete: Gradle failed in Notifications
  while reading `build/test-results/test/binary/in-progress-results-generic.bin`.
  Concurrent agents share this worktree/build directory, so this is not evidence
  of a product assertion failure; it is also not a passing verification run.
- `python3 tools/contracts/validate.py` passed: all JSON contracts, the GraphQL
  declaration set, and the 79-task registry validated.
- `git diff --check` passed for the shared worktree after this report was added.
- No application, contract, tracker, board, registry, or progress file was edited
  by DOC-17C.
