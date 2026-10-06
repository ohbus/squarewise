# Six-digit domain/module error-code standard

Status: frozen and authoritative under `ERRC-03`.

This document defines Squarewise's stable error identity. It does not change the
current REST or GraphQL contract. The implementation and compatibility sequence
is specified in [error-code-refactoring.md](error-code-refactoring.md).

## Canonical identity

Every governed failure has an immutable six-character machine identity:

```text
D M L C E E
│ │ │ │ └─┴── Exact error sequence, 01-99
│ │ │ └────── Category, 1-9
│ │ └──────── Published semantic layer, 1-9
│ └────────── Module within the domain, 1-9
└──────────── Stable bounded-context domain, 1-9
```

- Machine form: `213201`
- Display form: `21-3-2-01`
- Symbolic name: `GROUP_NOT_FOUND`
- Public v1 additive field: `"numericCode": "213201"`

The machine form is authoritative. The display form is derived for humans and
must never be stored as a second allocation. Codes are strings, not integers, so
the two-digit sequence remains explicit.

Validation pattern:

```text
^[1-9]{4}(0[1-9]|[1-9][0-9])$
```

Zero is invalid in every position and sequence `00` is forbidden.

## Identity semantics

The first two digits form the ownership namespace. They identify a stable
bounded context and capability, not a service process, Gradle project, team,
database, region, or deployment.

The layer digit describes where the published meaning belongs, not the class
that happened to throw. A PostgreSQL uniqueness failure translated to
`GROUP_NAME_CONFLICT` is a domain conflict even though the database detected it.
An unclassified connection-pool failure remains a persistence availability
error.

The category describes the kind of remediation. Neither layer nor category
determines HTTP status, GraphQL classification, legacy alias, retry behavior, or
message disposition. Those are explicit catalog fields because the same category
can legitimately map to different transports and statuses.

## Domain registry

| D | Domain | Stable meaning |
|:---:|---|---|
| `1` | Accounts | Profiles, authentication, sessions, lifecycle, identity |
| `2` | Expense Core | Groups, membership, expenses, settlements, recurrence, sync, search, outbox |
| `3` | Notifications | Inbox, delivery, preferences, email |
| `4` | BFF | GraphQL, upstream transport, live updates |
| `5`-`8` | Reserved | Future bounded contexts; no allocation without an architecture decision |
| `9` | Platform | Shared error, security, persistence, messaging, observability, and ID foundations |

The detailed immutable module assignments are in
[error-domain-registry.md](error-domain-registry.md) and the machine-readable
proposal is [contracts/errors/domains.yaml](../../contracts/errors/domains.yaml).

## Layer registry

| L | Layer | Published semantic origin |
|:---:|---|---|
| `1` | Interface | REST, GraphQL, WebSocket, CLI, or input/output boundary |
| `2` | Application | Use-case orchestration and workflow policy |
| `3` | Domain | Business rules and domain invariants |
| `4` | Persistence | Repository, database, cache, and storage behavior |
| `5` | Messaging | Producer, consumer, broker, acknowledgement, retry, or parking behavior |
| `6` | Integration | Downstream or external provider communication |
| `7` | Security | Authentication, authorization, tokens, CSRF, and security policy |
| `8` | Infrastructure | Startup, configuration, runtime environment, and framework infrastructure |
| `9` | Shared runtime | Generic shared-boundary behavior that cannot be owned more specifically |

Layer assignments are frozen when a code is published. Moving implementation
between a controller, service, worker, library, or deployable does not renumber a
semantically unchanged failure.

## Category registry

| C | Category | Meaning |
|:---:|---|---|
| `1` | Validation | Malformed, missing, out-of-range, or semantically invalid input |
| `2` | Missing | Resource or value absent, including deliberate existence hiding |
| `3` | Conflict | Duplicate, uniqueness, idempotency, version, or competing operation |
| `4` | State | Operation invalid for current lifecycle or workflow state |
| `5` | Policy/business rule | Domain or security policy rejected the operation |
| `6` | Data/consistency | Corruption, integrity loss, impossible persisted state, or reconciliation failure |
| `7` | Communication/protocol | Invalid protocol, serialization, delivery, or dependency response |
| `8` | Availability/timeout/quota | Temporary unavailability, timeout, admission, or bounded resource exhaustion |
| `9` | Internal/unexpected | Unclassified defect or invariant failure at the named layer |

Authorization denial uses security layer plus policy category. A protected
resource intentionally hidden from the caller uses security layer plus missing
category and retains HTTP 404.

## Exact sequence and capacity

Within each `DM-L-C` namespace:

- `01`-`89`: normal permanent allocations;
- `90`-`98`: reserved for future standard conventions;
- `99`: generic/unclassified only when a more precise registered condition is
  genuinely unavailable.

Numbers are allocated monotonically and gaps are never reused. Each
domain/module pair has 8,019 possible codes; the complete format has 649,539.
Capacity is not permission to allocate speculative errors.

## Required symbolic identity and catalog metadata

Every numeric code has one globally unique, immutable symbolic name. Runtime
code references symbols, never string or integer literals.

Each catalog record must define:

| Field | Rule |
|---|---|
| `numericCode` | Six-character immutable machine form |
| `errorName` | Globally unique `SCREAMING_SNAKE_CASE` name |
| `legacyCode` | Explicit current v1 symbolic alias when one exists; never derived from digits |
| `domain`, `module`, `layer`, `category`, `sequence` | Must agree with the numeric identity and registries |
| `lifecycle` | `ACTIVE`, `DEPRECATED`, or `RETIRED` |
| `owner` | Stable owning capability and review group |
| `source`, `component`, `operation` | Bounded public/diagnostic attribution values |
| `title`, `safeDetail`, `messageKey` | Catalog-controlled, locale-neutral, non-sensitive public text and client localization key |
| `httpStatus` | Explicit default REST status when REST-applicable |
| `graphqlClassification` | Explicit classification when GraphQL-applicable |
| `transports` | REST, GraphQL, WebSocket, messaging, scheduled work, startup, or internal |
| `retryPolicy` | Explicit remediation such as never, retry-after, reauthenticate, refresh, resync, or same-idempotency-key |
| `severity` | Bounded operational severity, not inferred from status |
| `requiredHeaders` | For example `WWW-Authenticate`, `Allow`, or `Retry-After` |
| `disclosure` | Allowed public fields and redaction requirements |
| `introducedIn`, `deprecatedIn`, `retiredIn`, `replacedBy` | Immutable lifecycle history |
| `runbook` | Required for critical, retryable, data-consistency, and availability failures |

An async-only failure does not receive an invented HTTP status. `httpStatus` is
absent when the code cannot cross an HTTP boundary.

## HTTP mapping policy

Status is explicit per definition. These are defaults and compatibility rules,
not digit-derived behavior.

| HTTP | Use |
|:---:|---|
| `400` | Malformed JSON, validation, conversion, missing request value, invalid identifier or cursor |
| `401` | Missing, malformed, invalid, expired, revoked, or replayed authentication; preserve `WWW-Authenticate` |
| `403` | Authenticated authorization, CSRF, or origin-policy rejection when existence may be disclosed |
| `404` | Missing resource or deliberate anti-enumeration response |
| `405` | Unsupported method; preserve `Allow` |
| `406` | No acceptable representation; may remain bodyless when a problem document cannot be negotiated |
| `409` | Optimistic version, uniqueness, idempotency, competing write, or lifecycle conflict |
| `410` | Genuinely expired resource/cursor only after an approved contract change |
| `413` | Request body exceeds a declared bound |
| `415` | Unsupported request media type |
| `422` | Structurally valid request rejected by a distinct domain policy |
| `429` | Rate/admission/quota rejection with bounded `Retry-After` where meaningful |
| `500` | Unexpected, invariant, data-consistency, response-serialization, or internal failure |
| `502` | Invalid, failed, or unparseable upstream response/protocol |
| `503` | Required dependency, capacity, or circuit unavailable |
| `504` | Upstream timeout |

Existing v1 statuses remain unchanged until a separately reviewed contract
change. In particular, sync cursor expiry currently remains 400 and
rate-limiter-store failure remains 429 during compatibility rollout.

## Allocation decision

Create a new error only when at least one of these differs:

- client or operator remediation;
- disclosure or authorization behavior;
- retry/idempotency policy;
- public transport status/classification;
- durable side-effect outcome;
- alert/runbook ownership.

Do not create a code solely because a different JVM exception class, database
vendor code, endpoint, deployment, or region produced the same semantic result.

## Translation rules

- Preserve a lower-layer code when its semantic meaning remains intact.
- Translate once at the boundary that changes meaning, retain the original as
  the cause, and never publish the cause text.
- Known database constraints become domain codes only when the constraint maps
  unambiguously to a domain rule.
- The BFF preserves valid upstream identity and creates BFF codes only for
  failures introduced by gateway transport, protocol, aggregation, or live
  update behavior.
- Messaging failures are recorded on processing attempts, retry/parking state,
  logs, metrics, and traces without modifying the original business event.

## Lifecycle and immutability

Once published, numeric identity, symbolic name, ownership meaning, and legacy
alias are immutable. A status change is a contract change and requires impact
review. Deprecated or retired entries remain in history and cannot be deleted or
reassigned.

`ERR-12` is a governance success marker with HTTP 200, not an error. Its future
mapping is `RETIRED` with no six-digit replacement.

## Performance and reflection policy

Runtime error handling must use direct property access to preconstructed static
definitions. The application path must not use reflection, classpath scanning,
annotation discovery, `ServiceLoader`, exception-class lookup tables, regexes,
or message parsing.

The authoritative YAML catalog is validated at build time. Follow-on work may
generate static Kotlin definitions or explicit manifests. Generated or explicit
lists are preferred over runtime discovery.

Any proposed reflection exception requires a separate ADR proving that no
simpler design works, that discovery happens only at startup, that results are
cached, and that benchmarks show an acceptable cost. Reflection is never allowed
per request, throw, mapping, log, metric, or serialization operation.

Codes are built once, not on every throw. Formatting is outside the hot response
path. No document may claim nanosecond behavior or 10M-DAU readiness without
JMH/allocation evidence and production-like load measurements.

## Privacy, localization, and global deployment

Codes and names are identical in India, the EU, the US, Canada, and every future
region. Region, cloud, zone, tenant, customer, locale, host, pod, database,
provider, and team are never encoded.

Public fallback text is safe and locale-neutral. Clients localize by stable
`errorName`/`messageKey`; they never parse `detail`. Error metadata must not
contain email addresses, tokens, invitation secrets, financial descriptions,
raw amounts, SQL, payloads, stack traces, exception class names, or unapproved
resource identifiers. Logs and traces follow regional residency, retention, and
access policies.

## New-code onboarding procedure

1. Confirm the condition is distinct using the allocation decision above.
2. Choose the semantic owner from the domain/module registry.
3. Choose the published layer and category; do not use the stack frame.
4. Find the next unused sequence in the authoritative catalog; never fill a
   retired gap.
5. Define every required catalog field, including explicit compatibility and
   transport metadata.
6. Review public safe text, bounded diagnostic fields, privacy, authorization
   hiding, retry, idempotency, and side effects.
7. Add or reuse a governed exception or typed outcome. A new code does not
   automatically require a new exception class.
8. Update the owning context guide, REST/GraphQL/event contract where applicable,
   tests, dashboard/alert decision, and runbook.
9. Run catalog, contract, source-parity, generic-throw, redaction, unit,
   integration, and public-interface checks.
10. Publish only after compatibility and rollback review.

## Prohibited practices

- Deriving codes from enum ordinals, class names, stack traces, exception text,
  HTTP status, database codes, tickets, or runtime counters.
- Reassigning retired numbers or names.
- Choosing arbitrary raw numeric strings at a throw site.
- Deriving legacy aliases, statuses, or retry policy from layer/category alone.
- Returning `Throwable.message`, root-cause text, stack traces, SQL, parser
  output, or arbitrary validation values.
- Catching fatal JVM failures and continuing.
- Logging every expected client error with a stack trace.
- Claiming a local benchmark proves global or multi-region capacity.

## Related documents

- [Error domain registry](error-domain-registry.md)
- [Error handling and exception guide](error-handling-guide.md)
- [Refactoring and migration plan](error-code-refactoring.md)
- [Current error flow](error-flow.md)
- [Per-context guides](errors/README.md)
