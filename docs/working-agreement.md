# Working agreement and fresh-session workflow

<p align="left">
  <img src="visuals/squarewise-logo.svg" alt="Squarewise Logo" width="300">
</p>

This repository is documentation-first and tracker-driven. After the DOC-28
cutover, mapped GitHub issues and the `Squarewise Delivery` project are the live
execution state machine. Until then, the registry and board remain the fallback.
Task details are scoped specifications and the progress ledger is the evidence
log. Code, CI, and operations must agree with those sources.

## Starting in the middle of work

1. Read `AGENTS.md` and `README.md`.
2. Read `docs/tasks/registry.yaml` and `docs/tasks/README.md`; use the mapped
   GitHub issue/Project for live state only when the recorded cutover is
   complete, otherwise read `docs/tasks/board.md`. Then read
   `docs/tasks/progress.md` for the latest evidence.
3. Read `docs/implementation/plan.md`, the task detail, and the relevant
   product, architecture, contract, quality, and operations documents.
   Every delegated subagent must thoroughly review all relevant documentation
   and adhere strictly to project standards before writing any code.
4. Inspect `git status --short`, recent commits, and the diff/history for the
   owned paths. Check for overlapping active work before editing.
5. In a progress update, state the GitHub issue number (or legacy task ID before
   cutover), objective, dependencies, owned and planned paths, validation
   commands, expected evidence, and any assumptions.

## During implementation

Think through implementation details, invariants, and edge cases before editing.
Always inspect existing code, technical libraries (`libs/ids`, `libs/errors`), and
services to identify existing functionality and maximize reuse. Avoid rewriting or
duplicating logic. Strive to generate/write as little code as possible while keeping
modules cohesive, modular, and maintainable. Ensure code is cognitively light, clear,
and easy for human maintainers to comprehend at a glance.
Keep changes small and coherent, following KISS (simplest viable design) and DRY
(minimizing duplication). Apply established enterprise design patterns (repository,
adapter/port, strategy, outbox) without introducing speculative complexity (YAGNI).
Enforce strict SOLID file separation: entities, Spring Data repositories, and
services/store adapters must never be bundled into a single file; each class/interface
must have its own dedicated, single-responsibility file. Continuously update relevant
documentation across `docs/` as tasks make progress to ensure documentation accurately
reflects code changes. Write structured Javadoc / KDoc doc comments (`/** ... */`) on
all public types, methods, and non-trivial domain logic. Update the task detail when
a decision, limitation, or contract changes. Use the principle policy and review
questions; do not create unregistered work or silently expand scope. Preserve user
edits, avoid generated artifacts and credentials, and use the repository's existing
validation and Make targets. If work must be split, register the child task before
delegating it and record the handoff.

## Completing an increment

Run the task's declared checks and record exact results, limitations, and the
commit hash in `docs/tasks/progress.md`. Review the diff for unrelated files,
duplicate versions, stale documentation, and principle violations. Commit one
coherent increment with a descriptive message. The coordinator alone updates
shared GitHub Project state, `docs/tasks/registry.yaml`, and the fallback board.
After cutover, status changes belong only in GitHub; evidence still belongs in
the progress ledger. Leave the tree clean unless a documented handoff says
otherwise.

## Source-of-truth order

User decisions control scope. The execution authority selected by
`registry.yaml.execution_tracking.cutover_status` controls live task state;
accepted contracts control externally visible behavior; architecture and
technology decisions control boundaries and versions; operations docs control
clone-and-run workflows. When sources disagree, stop and resolve the conflict
through the coordinator before implementation.
