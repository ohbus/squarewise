# OPS-13: Align CI triggers, workflows, and documentation with master branch

## Objective

Update the GitHub Actions workflows and documentation to target `master` as the authoritative default branch instead of `main`.

## Dependencies

- `OPS-12`

## Owned paths

- `.github/workflows/ci-branch.yml`
- `.github/workflows/ci-main.yml`
- `.github/workflows/ci-master.yml`
- `docs/operations/ci.md`
- `docs/tasks/details/OPS-13.md`

## Acceptance criteria

- `ci-branch.yml` ignores pushes to `master` (`branches-ignore: [master]`).
- `ci-main.yml` is renamed/migrated to `ci-master.yml` triggering on pushes to `master` (`branches: [master]`) with concurrency group `squarewise-master`.
- `docs/operations/ci.md` documents `ci-master.yml` and `master` branch image publishing.
- `make workflow-validate`, `python3 tools/contracts/validate.py`, and `git diff --check` pass.

## Validation commands

- `make workflow-validate`
- `python3 tools/contracts/validate.py`
- `git diff --check`

## Evidence

- `ci-branch.yml` ignores `master`.
- `ci-master.yml` triggers on `master` pushes and manual workflow dispatch.
- Workflow YAML parsing, contract validation, and git diff checks pass cleanly.
