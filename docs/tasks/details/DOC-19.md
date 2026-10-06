# DOC-19: Backfill legacy tracker ownership and evidence

Backfill historical owners, validation, evidence, and individual task details
where Git history supports them; record unavailable facts as limitations. Attribute
the untracked historical `.gitignore` maintenance commit and qualify
documentation-gate counts as historical snapshots. Depends on DOC-17.

## Deliverables & Decisions

1. **Owner Agent Backfills**:
   - Explicitly assigned historical `owner_agent` values in `docs/tasks/registry.yaml` for tasks previously set to `null` (e.g. DOC-08, CORE-03 mapped to `/root`).
   - Qualified documentation-gate task count as historical snapshot (25 tasks registered at the time of documentation gate passage on 2026-09-17).

2. **Per-Task Specification Files**:
   - Added dedicated task detail specification files `docs/tasks/details/DOC-11.md`, `docs/tasks/details/DOC-15A.md`, and `docs/tasks/details/DOC-15B.md` satisfying the 1:1 specification link rule.
   - Updated `docs/tasks/registry.yaml` specification references accordingly.

3. **Repository Maintenance (.gitignore) Attribution**:
   - Attributed untracked repository-level `.gitignore` updates to coordinator maintenance and documented `.kotlin/` compiler artifacts exclusion.

## Owned Paths

- `docs/tasks/registry.yaml`
- `docs/tasks/board.md`
- `docs/tasks/progress.md`
- `docs/tasks/details/DOC-19.md`
- `.gitignore`

## Verification Evidence

- `python3 tools/contracts/validate.py`: All 6 JSON contracts, GraphQL declarations, and 117 task registry entries valid.
- `git diff --check`: Clean formatting with zero errors.
