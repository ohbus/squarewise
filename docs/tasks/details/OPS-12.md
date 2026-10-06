# OPS-12: Isolate GHCR image publishing from reusable verification workflow

## Objective

Resolve the GitHub Actions workflow validation failure where `ci-branch.yml` (and `ci-pr.yml`) fail with:
`The nested job 'images' is requesting 'packages: write', but is only allowed 'packages: none'.`

GitHub Actions statically requires that caller workflows grant all permissions requested by any nested job in a reusable workflow, even when the nested job is skipped via an `if:` condition. Because feature branches and PRs must strictly adhere to least privilege (`contents: read`), image publishing with `packages: write` must be isolated from the reusable verification workflow.

## Dependencies

- `OPS-04`
- `OPS-11`

## Owned paths

- `.github/workflows/_reusable-ci.yml`
- `.github/workflows/ci-branch.yml`
- `.github/workflows/ci-pr.yml`
- `.github/workflows/ci-main.yml`
- `docs/operations/ci.md`
- `docs/tasks/details/OPS-12.md`

## Acceptance criteria

- `_reusable-ci.yml` requires only `contents: read` permissions and contains no nested jobs requesting `packages: write`.
- `ci-branch.yml` and `ci-pr.yml` call `_reusable-ci.yml` with `contents: read` without GitHub Actions permission validation errors.
- `ci-main.yml` calls `_reusable-ci.yml` for verification and executes the `images` publishing job only upon verification success, using its top-level `packages: write` permission.
- All workflow files pass YAML validation (`make workflow-validate`).
- Contract validation (`python3 tools/contracts/validate.py`) and whitespace/diff checks (`git diff --check`) pass.
- Operations documentation (`docs/operations/ci.md`) reflects the workflow separation.

## Validation commands

- `make workflow-validate`
- `python3 tools/contracts/validate.py`
- `git diff --check`

## Evidence

- Local Ruby YAML validation passes for all workflow files.
- `_reusable-ci.yml` contains only verification, lint, preflight, and E2E jobs under `contents: read`.
- `images` job resides in `ci-main.yml` and runs conditionally upon `ci` job success.
