# ERRC-01C: Review exception architecture, performance, and operations policy

Review the proposed value-object/catalog/typed-exception design against the
repository architecture, JVM/Spring/GraphQL/reactive/messaging behavior,
security requirements, observability policy, and target scale. Report findings
to the coordinator; make no files or implementation changes.

The review must cover reflection-free runtime dispatch, safe explicit code
overrides, fatal `Throwable` policy, coroutine/reactive cancellation,
interruption, asynchronous acknowledgement/retry semantics, stack-trace
containment, cardinality, allocation cost, exception proliferation, and
measurable performance acceptance.

## Findings delivered

- Rejected arbitrary definition injection and category-based exception inheritance.
  Recommended a minimal governed base, fixed defaults, narrow owner-defined override
  families, and leaf classes only where typed recovery or reuse adds value.
- Defined fatal propagation for VM/linkage/thread failures, cancellation preservation,
  interrupt restoration, Reactor fatal checks, and static container fallbacks rather
  than blanket `catch(Throwable)` recovery.
- Required direct static definitions or build-time generation; runtime reflection,
  scanning, annotations, `ServiceLoader`, YAML parsing, regex, and message matching are
  excluded from operational paths.
- Distinguished request-thread and Reactor correlation, required bounded validation
  output and diagnostic fields, and specified log-once, stack sampling, cardinality,
  localization, privacy, and regional-residency constraints.
- Required JMH/allocation/concurrency/production-like load evidence before claiming
  performance or 10M-DAU readiness; exception stack suppression remains measured and
  exceptional rather than the default.

This was a read-only review. The coordinator incorporated the findings into commit
`33e5cea`; no runtime implementation was changed by this child task.
