# DOC-17: Current-state task, Git, and drift audit

## Objective

Reconcile the task tracker with repository history and the current worktree,
map changed files to registered work, identify implementation and documentation
drift, and register concrete follow-up tasks for verified gaps.

## Dependencies

- `DOC-15`
- `DOC-16`

## Owned paths

- `docs/reviews/current-state-audit.md`
- `docs/tasks/registry.yaml`
- `docs/tasks/board.md`
- `docs/tasks/progress.md`
- `docs/tasks/details/DOC-17*.md`

## Validation commands

- `python3 tools/contracts/validate.py`
- `./gradlew test --no-daemon`
- `git diff --check`

## Expected evidence

- Every current worktree path is mapped to one registered task.
- Registry, board, task details, evidence, and Git history agree on task state.
- Confirmed gaps have scoped follow-up tasks with dependencies, ownership, and
  validation commands.
