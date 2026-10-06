# DOC-22: Programming principles and current-state documentation reconciliation

## Objective

Make the listed programming principles enforceable repository policy and update
current documentation to match the implemented repository, task tracker, CI,
Compose topologies, and known limitations.

## Dependencies

- `DOC-17`
- `DOC-20`
- `OPS-09`
- `OPS-11`

## Owned paths

- `AGENTS.md`
- `README.md`
- `docs/quality/programming-principles.md`
- `docs/quality/coding-guidelines.md`
- `docs/api/implementation-status.md`
- `docs/implementation/plan.md`
- `docs/implementation/technology-decisions.md`
- `docs/operations/quickstart.md`
- `docs/operations/intellij.md`
- `docs/operations/ci.md`
- `docs/tasks/details/DOC-22.md`

## Acceptance criteria

- KISS, YAGNI, simplest-thing-that-works, separation of concerns, DRY/single
  source of truth, maintainer-first code, no premature optimization, low
  coupling/Law of Demeter, composition, orthogonality, robustness/tolerant
  readers, inversion of control, cohesion, LSP, OCP, SRP, information hiding,
  Curly's Law, encapsulated variation, interface segregation, Boy Scout rule,
  CQS, Murphy's law, Brooks's law, and Linus's law are documented as practical
  coding and review rules.
- AGENTS.md requires every implementation/review to apply these principles and
  record exceptions or trade-offs.
- README and current operational/API/technology documents describe the actual
  repository state without claiming MVP or launch acceptance prematurely.
- Historical audit reports are explicitly labeled snapshots when their counts
  differ from the current registry.

## Implementation notes

- Added the mandatory principle policy and fresh-session workflow to the shared
  agent guidance and quality documentation.
- Updated README and current API/plan/acceptance documents to describe the
  partially implemented backend without implying MVP or launch completion.

## Validation commands

- `python3 tools/contracts/validate.py`
- `make workflow-validate`
- `git diff --check`
