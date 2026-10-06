# DOC-24: Mandate strict SOLID file separation, comprehensive documentation linking, and pre-implementation documentation review

## Objective

Update repository standards in `AGENTS.md`, `GEMINI.md`, `docs/working-agreement.md`, `docs/quality/programming-principles.md`, `docs/quality/coding-guidelines.md`, and `docs/architecture/project-structure.md` to mandate:
1. **Strict SOLID file separation**: In enterprise-ready code, Entities, Repositories, Services/Adapters, and Domain Models/DTOs must never be bundled into a single file. Every class/interface must reside in its own cohesive file with dedicated single responsibility (SRP).
2. **Pre-implementation documentation review**: Every delegated subagent must thoroughly review all relevant documentation before writing any code.
3. **Comprehensive markdown linking**: Every document referenced across `AGENTS.md`, `GEMINI.md`, and architectural guidelines must be an active, valid markdown link so agents and developers can navigate directly to authoritative standards.

## Dependencies

- `DOC-23`

## Owned paths

- `AGENTS.md`
- `GEMINI.md`
- `docs/working-agreement.md`
- `docs/quality/programming-principles.md`
- `docs/quality/coding-guidelines.md`
- `docs/architecture/project-structure.md`
- `docs/tasks/details/DOC-24.md`

## Acceptance criteria

- `AGENTS.md` and `GEMINI.md` list all core documentation with valid, verified markdown links.
- `AGENTS.md` and `GEMINI.md` mandate that every delegated subagent must read and strictly adhere to all relevant documentation prior to any implementation.
- `AGENTS.md`, `GEMINI.md`, `docs/quality/coding-guidelines.md`, and `docs/quality/programming-principles.md` mandate strict Single Responsibility Principle (SRP) per file: separate entity, repository, and service/adapter files.
- `python3 tools/contracts/validate.py` and `git diff --check` pass cleanly.

## Validation commands

- `python3 tools/contracts/validate.py`
- `git diff --check`

## Evidence

- Recorded in `docs/tasks/progress.md`.
