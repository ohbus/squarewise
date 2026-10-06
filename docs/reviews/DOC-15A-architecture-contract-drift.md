# DOC-15A architecture, contract, and documentation drift review

Date: 2026-09-17  
Reviewer: `/root/drift_architecture`  
Scope: accepted architecture, product scope, REST/GraphQL/event contracts, diagrams, and service boundaries.

## Review evidence

The review compared the accepted documents with the repository source and
contract files. The following checks were run:

```text
python3 tools/contracts/validate.py                         # passed; 45 tasks
./gradlew test --no-daemon                                  # passed
git status --short                                           # pre-review had coordinator tracker changes
rg '@(Get|Post|Put|Delete|Patch)Mapping' app --glob '*.kt'  # endpoint inventory
```

The Gradle run completed successfully in 4 seconds with 28 actionable tasks;
many tasks were up to date. This is build evidence, not evidence that all
product journeys or persistence integrations are complete.

## Findings

### D-01: High: REST contracts do not cover implemented public endpoints

The Expense Core source exposes synchronization (`/snapshot`, `/changes`),
allocation preview (`/preview`), settlements and reversal, and invite claim;
the Expense Core OpenAPI document currently lists only `/groups` and invite
creation. Accounts exposes `PATCH /me`, while the Accounts contract lists only
`GET /me` and deletion request. Notifications inbox and preferences are aligned
with their current mappings, but their behavior is only partially represented
by schemas. The GraphQL schema contains only the currently implemented group
surface and does not map the newer synchronization, settlement, category, or
notification operations.

Classification: contract/documentation drift.  
Impact: generated clients and acceptance tests cannot reliably discover or
validate the public surface.  
Remediation: register a contract expansion task; update OpenAPI and GraphQL
operation mapping before treating the corresponding product slices as
complete; rerun `python3 tools/contracts/validate.py` and public-interface
tests.

### D-02: High: architecture promises durable ownership while most implemented slices are in-memory

The architecture assigns PostgreSQL ownership of financial records, group
membership, synchronization, audit, and outbox state to Expense Core and
durable inbox/preferences/delivery state to Notifications. Source inspection
shows concurrent maps for groups/invites, synchronization state, settlements,
outbox, inbox, preferences, and delivery decisions. Accounts has a JPA profile
adapter, but deletion/export and some profile paths remain in-memory.

Classification: implementation maturity drift, explicitly acknowledged by
several existing task notes but not yet reconciled in a milestone status.
Impact: restart safety, multi-replica behavior, transactional atomicity, and
cross-instance authorization are not demonstrated by the current code.
Remediation: keep these tasks in progress until Testcontainers PostgreSQL,
Flyway migrations, transactional boundaries, and concurrent integration tests
provide evidence. Do not describe the scaffold as production-ready product
behavior.

### D-03: Medium: accepted architecture says three REST services, but BFF is an additional deployable

The architecture text correctly calls this “three REST services plus a BFF,”
and the project contains four applications. This is consistent, but some
planning language refers to “three services” without explicitly distinguishing
the client-facing BFF from the REST resource services.

Classification: terminology drift, low risk.  
Remediation: use “three resource REST services plus one database-free GraphQL
BFF” consistently in plans, CI, deployment, and acceptance documents.

### D-04: Medium: toolchain statements are inconsistent

`AGENTS.md` and the current build use Java 25, while
`docs/implementation/technology-decisions.md` and the implementation plan
retain Java 26 as a candidate target. The plan also names Spring Boot 4.1.1 and
Gradle 9.7.0 as earlier proposals while the repository baseline differs.

Classification: technology documentation drift.  
Remediation: record one verified baseline and mark historical candidates as
superseded, with dependency-resolution evidence in the technology decision and
version catalog.

### D-05: Medium: product scope claims MVP capabilities ahead of public behavior evidence

The product document lists expenses, balances, repayments, recurrence, offline
creation, search/export, and notifications as MVP behavior. The acceptance plan
correctly says product acceptance requires authenticated API tests against real
PostgreSQL/RabbitMQ, but the current implementation has only contract/domain
slices for many of these capabilities.

Classification: milestone/status drift rather than a product-scope defect.
Remediation: retain the scope, but label each capability as planned, slice
implemented, or end-to-end accepted in the task registry and acceptance report.

### D-06: Low: UUIDv7 policy has deterministic identity exceptions that need explicit documentation

The shared generator uses UUIDv7 for newly generated IDs. Occurrence identity
and account linkage use deterministic UUID derivation, and tests use random UUIDs
as fixtures. These are valid exceptions, but the architecture/technology
documents do not enumerate them.

Classification: documentation gap.  
Remediation: document that externally supplied IDs, deterministic idempotency or
occurrence identities, and test fixtures are exempt from UUIDv7 generation.

## Confirmed alignment

- Service boundaries keep financial state in Expense Core and keep the BFF free
  of financial persistence.
- REST is the internal synchronous boundary; GraphQL HTTPS and WebSocket hints
  are BFF-facing, matching the accepted architecture.
- Money is represented as minor-unit values internally and string-backed values
  at public boundaries in the reviewed contracts.
- Error-flow documentation defines stable problem codes and request correlation.
- Product scope remains free, with payment processing and UI explicitly deferred.
- The architecture diagram reflects REST calls, outbox/RabbitMQ delivery, and
  recoverable WebSocket invalidations.

## Review conclusion

DOC-15A identifies no unauthorized service boundary change. The principal drift
is completeness and evidence: contracts and durable integration lag the breadth
of the documented MVP. The repository is buildable and contract syntax is valid,
but the product milestone must not be marked fully accepted until D-01 and D-02
are resolved with updated contracts and real persistence/messaging evidence.
