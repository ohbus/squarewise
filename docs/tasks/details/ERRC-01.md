# ERRC-01: Design six-digit domain error-code migration

## Objective

Produce a documentation-only, implementation-ready decision set and migration
plan for replacing the current flat error identifiers with the six-digit
`DM-L-C-EE` standard. The plan must fit Squarewise's existing applications,
libraries, public contracts, security controls, error observability, and
10M+-DAU target without claiming capacity evidence that has not been measured.

## Dependencies and current-state constraint

- `ERR-12` and `SEC-02` are complete.
- The current symbolic public error vocabulary and ERR-01 through ERR-12 work are
  shipped repository state, not a blank slate.
- This task changes documentation, allocation contracts, and task tracking only.
  It does not authorize Kotlin, OpenAPI response-shape, GraphQL, test, or CI
  implementation changes.
- `QA-10` remains active and also owns the shared progress ledger. The same
  coordinator serializes tracker edits; delegated reviewers do not edit it.

## Owned deliverables

- `contracts/errors/domains.yaml`
- `docs/architecture/error-code-standard.md`
- `docs/architecture/error-domain-registry.md`
- `docs/architecture/error-code-refactoring.md`
- `docs/architecture/error-handling-guide.md`
- `docs/architecture/errors/` per-context guides
- navigation and current-state cross-links in `docs/README.md`,
  `docs/architecture/error-flow.md`, and `docs/implementation/plan.md`
- this task detail and the three read-only audit task details
- coordinator-only registry, board, and progress updates

## Required decisions

1. Canonical numeric and symbolic identities, ownership, reservation, lifecycle,
   and allocation authority.
2. Exhaustive failure-family and HTTP-status policy, including validation,
   authentication, authorization, conflict, state, business, persistence,
   messaging, integration, availability, and unexpected failures.
3. Typed exception design with a baked-in default error definition and a safe,
   constrained explicit override for legitimate reuse.
4. A deliberate-generic-throw prohibition and boundary policy for framework,
   third-party, JVM-fatal, coroutine, reactive, scheduled, and asynchronous
   failures.
5. A reflection-free runtime mapping path with build-time validation and measured
   performance acceptance rather than unsupported speed claims.
6. Contract-first backward-compatible rollout, rollback, tests, observability,
   runbooks, documentation, and onboarding.
7. Registration-ready ordered follow-on implementation tasks with
   non-overlapping ownership, dependencies, checks, and evidence. They remain
   planning artifacts until separately registered by the coordinator.

## Acceptance

The final documentation must distinguish approved decisions from proposals,
remove inaccurate clean-tree or implementation claims, identify open decisions,
and be detailed enough for a new engineer to allocate and implement an error
without inventing identifiers, statuses, messages, or handling behavior.

## Validation

```sh
uv run python tools/contracts/validate.py
uv run python tools/errors/validate_catalog.py
git diff --check
```

## Programming principles

The design uses one allocation authority, compile-time constants, narrow
contracts, bounded-context ownership, explicit translation at adapter
boundaries, and minimal runtime work. It avoids reflection, ordinal-derived
identities, exception-message classification, duplicated status maps, and a
single application-wide catch-all enum.

## Completion evidence

- Canonical identity, ownership, allocation, lifecycle, HTTP, compatibility,
  reflection, privacy, localization, and onboarding rules are recorded in
  `docs/architecture/error-code-standard.md` and the domain registry.
- `docs/architecture/error-handling-guide.md` defines the minimal governed base,
  useful-leaf rule, baked defaults, constrained family overrides, deliberate generic
  throw prohibition, fatal/cancellation/interrupt behavior, every transport boundary,
  observability, and performance gates.
- Per-context guides inventory candidate public, internal, async, persistence,
  dependency, scheduler, security, and startup families for all applications and
  platform libraries.
- `docs/architecture/error-code-refactoring.md` defines `ERRC-02` through `ERRC-31`
  as registration-ready increments with dependencies, ownership classes,
  deliverables, patterns/principles, validation, evidence, rollout, and rollback.
- Architecture documentation commit: `33e5cea`.
- No runtime implementation or existing public response schema changed.
