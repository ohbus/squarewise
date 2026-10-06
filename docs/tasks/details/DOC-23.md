# DOC-23: Mandate continuous documentation updates and Javadoc/KDoc comments in working agreement

## Objective

Codify two mandatory rules in `AGENTS.md`, `docs/working-agreement.md`, and `docs/quality/coding-guidelines.md`:
1. **Continuous Documentation Updates**: As tasks are implemented, modified, or completed, agents must continuously update all relevant documentation across the repository (task details, architectural and operational guides, contracts, and tracking ledgers) to reflect the changes immediately rather than deferring documentation.
2. **Javadoc / KDoc-Style Code Comments**: All new or modified public classes, interfaces, methods, models, endpoints, and non-trivial business logic must include structured Javadoc / KDoc-style documentation comments (`/** ... */`) explaining intent, parameters, return values, invariants, and edge cases.

## Dependencies

- `DOC-22`

## Owned paths

- `AGENTS.md`
- `docs/working-agreement.md`
- `docs/quality/coding-guidelines.md`
- `docs/tasks/details/DOC-23.md`

## Acceptance criteria

- `AGENTS.md` adds explicit rules in the working agreement and end-of-task protocol for continuous documentation updates as tasks progress.
- `AGENTS.md` and `docs/quality/coding-guidelines.md` mandate Javadoc / KDoc-style structured comments on code.
- `docs/working-agreement.md` reflects these rules for session continuity.
- `python3 tools/contracts/validate.py`, `make workflow-validate`, and `git diff --check` pass cleanly.

## Validation commands

- `python3 tools/contracts/validate.py`
- `make workflow-validate`
- `git diff --check`

## Evidence

- `AGENTS.md` includes explicit sections/rules for continuous documentation synchronization and Javadoc/KDoc coding documentation.
- `docs/quality/coding-guidelines.md` specifies formatting and requirements for code comments.
- Contract validation and diff checks pass.
