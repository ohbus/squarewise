# Error handling and exception guide

Status: accepted documentation baseline under `ERRC-01`; not implemented.

This guide defines how Squarewise will represent, throw, translate, catch, expose,
and observe failures. The objective is complete, predictable boundary handling, not
an attempt to recover from JVM corruption or to turn every failure into a unique
class.

## Non-negotiable invariants

1. No stack trace, exception class, exception message, root-cause text, SQL,
   parser output, secret, token, or raw payload reaches a public response.
2. Every predictable escaped production failure has a registered
   `ErrorDefinition` and a deliberate transport outcome.
3. Owned production code does not deliberately throw `Exception`,
   `RuntimeException`, `Throwable`, `NullPointerException`, or an unregistered
   generic exception. Static validation enforces the rule.
4. Framework, dependency, JVM, and legacy generic failures are still expected at
   trust boundaries and are mapped fail-closed to a registered internal error.
5. Fatal JVM failures are not converted into an API response or acknowledged as
   processed. The process/container is allowed to fail and restart.
6. Runtime mapping is direct and constant-time. Reflection, classpath scanning,
   annotation discovery, `ServiceLoader`, regex matching, and message parsing are
   prohibited on the request, throw, catch, logging, metric, and serialization paths.
7. A failure is translated once, at the boundary where its semantic meaning changes.

## Definition model

The authoritative catalog supplies immutable, preconstructed definitions. The
planned API is conceptually:

```kotlin
interface ErrorDefinition {
    val numericCode: ErrorCode
    val errorName: String
    val legacyCode: LegacyPublicCode?
    val title: String
    val safeDetail: String
    val messageKey: String
    val httpStatus: Int?
    val graphqlClassification: String?
    val retryPolicy: RetryPolicy
    val severity: ErrorSeverity
    val disclosure: DisclosurePolicy
}
```

The catalog, not exception text, controls all public fields. `ErrorCode` construction,
catalog loading, and formatting do not occur for every failure. Implementations use
static generated definitions or explicit compiled manifests.

## Exception model

Use one small governed base such as `SquarewiseException`. Do not build nine base
classes that mirror category digits; categories are metadata, not substitutable
behavior. Do not create a one-line exception for every code merely to claim custom
exception coverage.

A leaf exception is justified when at least one condition holds:

- callers have a legitimate, documented recovery path that catches the type;
- the same semantic failure is raised at several sites and benefits from one typed
  constructor/factory;
- the type carries bounded structured diagnostics needed by a boundary or retry;
- a library boundary must distinguish it without inspecting text or causes.

Otherwise, throw the governed base with a concrete static definition or return a
typed domain outcome and translate it at the application boundary.

Planned pseudocode:

```kotlin
abstract class SquarewiseException protected constructor(
    val definition: ErrorDefinition,
    val diagnostics: ErrorDiagnostics = ErrorDiagnostics.Empty,
    cause: Throwable? = null,
) : RuntimeException(definition.errorName, cause)

class GroupNotFoundException(
    diagnostics: GroupLookupDiagnostics,
    cause: Throwable? = null,
) : SquarewiseException(ExpenseErrors.GROUP_NOT_FOUND, diagnostics, cause)
```

Handlers must ignore `Throwable.message`, even when the message currently appears
safe. The example base message exists only for internal stack diagnostics.

### Baked default and controlled override

The default definition is baked into a leaf exception. Most leaf exceptions offer
no override. A reusable exception may accept an override only through a narrow,
owner-defined family that proves compatibility:

```kotlin
sealed interface GroupLookupFailure : ErrorDefinition

class GroupLookupException(
    definition: GroupLookupFailure = ExpenseErrors.GROUP_NOT_FOUND,
    diagnostics: GroupLookupDiagnostics,
    cause: Throwable? = null,
) : SquarewiseException(definition, diagnostics, cause)
```

The constructor must reject or make unrepresentable an override with a different
semantic family, disclosure policy, or transport expectations. An arbitrary
`ErrorDefinition` parameter on every custom exception is forbidden: it recreates
today's throw-site inconsistency. Cross-domain reuse should normally translate into
the owning domain's exception instead.

### Structured diagnostics

Diagnostics are typed and bounded: approved IDs, attempt number, dependency key,
constraint key, cursor version, or operation enum. They never contain free-form
payloads, authentication material, personal data, financial descriptions, raw
amounts, email addresses, or exception messages. Each diagnostic field declares
log, trace, metric, and public disclosure eligibility.

## Throwing rules

- Validate expected user input with typed validation outcomes or registered
  exceptions; never expose `require`/`check` messages at a boundary.
- `require` and `check` are permitted only for programmer-only invariants wholly
  inside a component. If they can escape, translate them before the boundary.
- Do not throw `NullPointerException` deliberately. Kotlin nullability, explicit
  validation, and typed missing/state errors own predictable absence.
- Preserve the cause for internal diagnostics when translating infrastructure or
  dependency failures; never preserve its text in public metadata.
- Never choose a raw numeric code or public message at a throw site.
- Do not allocate a new code solely because an internal JVM exception differs.

## Catching rules

Catch the narrowest type where recovery or semantic translation is possible. A
boundary catch is deliberately broad enough to prevent generic dependency failures
from escaping, but it follows JVM fatality rules.

### Fatal, cancellation, and interruption policy

- Re-throw `VirtualMachineError` (including `OutOfMemoryError`), `ThreadDeath`, and
  `LinkageError`. Do not allocate a problem document, log a large object graph, retry,
  acknowledge a message, or continue normal work.
- Preserve coroutine/reactive cancellation; never translate cancellation into a
  business error.
- On `InterruptedException`, restore the interrupt flag before propagating or
  performing the explicitly documented shutdown action.
- Reactive adapters must apply Reactor's fatal-exception predicate before mapping.
- Web request boundaries normally catch `Exception`, not `Throwable`. Container-level
  configuration supplies a static sanitized fallback if the framework emits a 500
  before application advice can run.

`catch (Throwable)` is prohibited in ordinary application code. Where a framework
callback forces it, the first operation is the shared fatal/cancellation classifier;
the documented non-fatal remainder is then translated. This narrowly scoped adapter
is reviewed and tested.

## Boundary policy

### Servlet REST

The advice maps registered exceptions directly, maps known Spring exceptions through
explicit tables, and maps the remaining non-fatal `Exception` to a static internal
definition. Malformed bodies receive a catalog message, never Jackson's root cause.
Security filter-chain failures use an authentication entry point and access-denied
handler because controller advice does not reliably own pre-controller failures.

Validation violations are sorted deterministically, deduplicated, capped in count,
and length-bounded. They include field paths and catalog messages only, never rejected
values or annotation/framework text without review. Oversized bodies, unsupported
media types/methods, negotiation failures, response serialization, async timeouts,
and disconnected clients have explicit policies.

### Reactive/WebFlux and GraphQL

Reactor context, not thread-local state, carries request/trace identity. The BFF
preserves a valid upstream `numericCode`, `errorName`, legacy `code`, source, and
request ID. It creates BFF-owned codes only for gateway input, transport, protocol,
aggregation, or subscription failures.

Timeout, DNS, connection refusal, TLS, circuit-open, invalid status, invalid content
type, malformed problem body, upstream cancellation, and response decode failures
have distinct governed mappings where remediation differs. Resolvers never infer an
error by matching English text or reconstruct identity from HTTP status alone.

### Messaging

Consumers distinguish business rejection, malformed/unsupported envelope, duplicate,
transient dependency failure, permanent poison message, retry exhaustion, parking
failure, and acknowledgement failure. The business event is not mutated to carry
processing errors; attempt and parking records carry operational error identity.

Fatal errors and cancellation are never acknowledged. Transient retry is bounded,
jittered, idempotent, and routed by explicit retry policy. Permanent failures are
parked once with safe metadata; failure to park is separately observable. Handlers
must not catch `Throwable` and continue.

### Scheduled/background work

Every scheduled entry point has a named operation boundary. It records a terminal
definition for lock contention, dependency unavailability, item poison, partial batch,
retry exhaustion, and unexpected failure. One bad item does not silently terminate a
batch; continuing versus aborting is explicit and side-effect safe.

### Startup and shutdown

Invalid configuration, missing secret reference, incompatible schema, migration
failure, broker/database unavailability, and duplicate instance ownership are
registered platform errors. Startup logs safe codes and terminates; it does not expose
configuration values. Shutdown cancellation and expected connection closure are not
reported as server defects.

## Public transport contract

During the v1 compatibility window, REST keeps the current symbolic `code` and adds
optional `numericCode` and `errorName`:

```json
{
  "type": "https://squarewise.example/problems/group-not-found",
  "title": "Group not found",
  "status": 404,
  "code": "NOT_FOUND",
  "numericCode": "213201",
  "errorName": "GROUP_NOT_FOUND",
  "source": "expense-core",
  "requestId": "req_example",
  "detail": "Group not found",
  "timestamp": "2026-09-30T15:00:00Z"
}
```

`numericCode` is the canonical machine form; clients may format it for humans. New
fields become required only after all producers and consumers have completed the
compatibility window. Changing `code` itself to the numeric identity requires a
versioned breaking contract. No redundant `legacyCode` field is introduced while
`code` already carries that value.

GraphQL uses equivalent extensions and never exposes exception details. Messaging,
startup, and scheduled failures use their native operational record rather than
inventing an HTTP problem.

## Logging, metrics, and traces

- Log each failure once at its owning boundary.
- Expected 4xx failures normally log without stacks and may be sampled.
- Unexpected 5xx, data-consistency, and terminal dependency failures log the cause
  once with regional privacy controls.
- Never log both at translation and at final mapping unless the two records describe
  different side-effect states.
- Metrics use bounded labels. Primary labels are service, exact registered error code,
  and status class; domain/module/layer/category can be derived or pre-aggregated.
  Operation is allowed only from a fixed enum. Request ID, trace ID, user, tenant,
  resource, host, message, locale, and region are forbidden metric labels.
- Deployment region is infrastructure metadata, not error identity.
- Traces may record the code/name and bounded operation. Stack recording follows
  sampling and disclosure policy.

## Performance policy

Correctness and containment come first, then measured optimization. Definitions and
lookup tables are static; mapping is direct; request paths do not parse YAML, scan
classes, format display codes, or use reflection. Expected high-frequency rejection
paths may use typed results instead of exceptions where profiling shows stack capture
dominates, while the boundary still emits the same definition.

Stack suppression is not a default optimization. It requires benchmarks, preserves
diagnosability through structured context, and is limited to a measured expected
failure type. JMH, allocation profiling, concurrency tests, and production-like load
tests must support any performance claim. “10M DAU ready” is a target, not evidence.

## Verification gates

Implementation tasks must provide:

- catalog format, uniqueness, immutability, registry, and lifecycle validation;
- static detection of deliberate generic throws, raw codes, message-derived mapping,
  and prohibited reflective discovery;
- REST, security-filter, GraphQL, WebSocket, messaging, scheduler, startup, and
  serialization boundary tests;
- redaction and adversarial tests for stack/cause/parser/SQL/secret leakage;
- contract parity and breaking-change comparison for every OpenAPI and schema;
- concurrency, cancellation, interrupt, fatality, retry, idempotency, and partial
  side-effect tests;
- JMH/allocation/load evidence before performance acceptance.

See [the refactoring plan](error-code-refactoring.md) for ordered work and
[the per-context guides](errors/README.md) for owned failure inventories.
