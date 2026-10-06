# Error domain and module registry

Status: accepted documentation baseline under `ERRC-01`; not implemented.

This is the proposed canonical ownership table for Squarewise error namespaces.
It becomes the allocation authority when the implementation gate accepts and
validates [the machine-readable registry](../../contracts/errors/domains.yaml).
After publication, domain and module numbers are immutable.

The namespace names a stable capability, never a deployable, team, database,
region, environment, transport, or current package. A capability may move between
applications and libraries without changing its prefix.

## Domain 1: Accounts

Identity, profiles, preferences, authentication, and lifecycle.

| DM | Module | Owns | Planned declaration owner |
|:---:|---|---|---|
| **11** | Profile | Profile CRUD, preferences, subject validation | Accounts error catalog |
| **12** | Authentication | Login start, verification, credential management | Accounts error catalog |
| **13** | Session | Token minting, refresh rotation, family revocation | Accounts error catalog |
| **14** | Lifecycle | Deletion requests, export requests, GDPR | Accounts error catalog |
| **15** | Identity | Account identity mapping by issuer and subject | Accounts error catalog |

## Domain 2: Expense Core

Groups, membership, expenses, allocation, ledger, settlements, recurrence,
synchronization, and outbox.

| DM | Module | Owns | Planned declaration owner |
|:---:|---|---|---|
| **21** | Groups | Group lifecycle, rename, archive | Expense Core error catalog |
| **22** | Membership | Participants, invitations, claiming | Expense Core error catalog |
| **23** | Expenses | Expense CRUD, allocation, categories | Expense Core error catalog |
| **24** | Settlements | Repayments, reversals, suggestions | Expense Core error catalog |
| **25** | Recurrence | Recurring schedules, occurrences, runner | Expense Core error catalog |
| **26** | Sync | Change feed, cursors, offline replay | Expense Core error catalog |
| **27** | Search | Filtering, export, CSV | Expense Core error catalog |
| **28** | Outbox | Transactional outbox relay and event publication | Expense Core error catalog |

## Domain 3: Notifications

Notification inbox, delivery, preferences, and email dispatch.

| DM | Module | Owns | Planned declaration owner |
|:---:|---|---|---|
| **31** | Inbox | Notification inbox and read state | Notifications error catalog |
| **32** | Delivery | Delivery jobs and channel dispatch | Notifications error catalog |
| **33** | Preferences | Channel opt-in and delivery settings | Notifications error catalog |
| **34** | Email | SMTP dispatch and template rendering | Notifications error catalog |

## Domain 4: BFF

Client-facing GraphQL gateway, upstream REST transport, and live update fanout.

| DM | Module | Owns | Planned declaration owner |
|:---:|---|---|---|
| **41** | GraphQL | Resolvers and schema mapping | BFF error catalog |
| **42** | Transport | Upstream REST client and error preservation | BFF error catalog |
| **43** | Live update | WebSocket fanout and subscriptions | BFF error catalog |

## Domains 5–8: Reserved

Available for future bounded contexts. Not yet allocated.

## Domain 9: Platform

Cross-cutting infrastructure shared by all applications via `libs/`.

| DM | Module | Owns | Planned declaration owner |
|:---:|---|---|---|
| **91** | Error framework | Codes, definitions, exception boundary, transport adapters | `libs/errors` |
| **92** | Security | Token validation, OIDC, CSRF, origin policy | Security libraries and adapters |
| **93** | Persistence | JPA/Flyway configuration and database health | Persistence library |
| **94** | Messaging | RabbitMQ transport, dead-letter, acknowledgement | Messaging library |
| **95** | Observability | Tracing, metrics, structured logging | Observability configuration |
| **96** | IDs | Identifier generation and endpoint constants | IDs library |

## Allocation rules

1. A domain is a long-lived bounded context or platform capability.
2. A module is a stable capability within that domain; it need not match a Gradle
   module or Spring application.
3. `DM` is the primary namespace. A semantically unchanged `23xxxx` error remains
   `23xxxx` after code movement or service decomposition.
4. Published domain and module digits are never renamed to a different meaning,
   reassigned, or reused after retirement.
5. A new allocation requires an architecture decision, registry and guide update,
   owner, migration impact, and catalog validation evidence.
6. Domains `5`-`8` are reserved, not a first-come pool. Allocation requires a new
   bounded-context decision.
7. Domain `9` is only for cross-cutting infrastructure. A reusable library that
   implements a business capability retains the business domain namespace.
8. Application identity, geography, tenancy, technology, and team ownership remain
   metadata and never enter the six digits.

## Add a module

1. Prove that no existing module owns the semantic capability.
2. Record the stable name, scope boundaries, owner, affected applications and
   libraries, and migration consequences.
3. Select an unused digit in the owning domain; do not reuse a retired digit.
4. Update the registry, this document, the owning context guide, and validation
   fixtures in one reviewed change.
5. Allocate exact errors only after the namespace change is accepted.

## Add a domain

1. Record a bounded-context decision showing why Domains `1`-`4` and `9` cannot
   own the capability.
2. Select one reserved digit and define at least one module.
3. Define ownership, data boundaries, transport boundaries, compatibility, and
   long-term stability before allocation.
4. Update the registry, architecture, catalog tooling, context guide, and tasks
   atomically.
5. Obtain architecture and contract-owner review before any code is published.

## Registry governance

The YAML registry is the future machine authority; this Markdown view explains
intent. CI must eventually prove the two agree. Runtime code must not parse this
file, scan classpaths, or discover implementations reflectively. Definitions are
compiled or generated at build time and referenced directly.

Proposed error families and compatibility mappings are documented per context in
[errors/README.md](errors/README.md). Exact entries remain proposals until the
catalog-freeze implementation task validates them against every throw and boundary.
