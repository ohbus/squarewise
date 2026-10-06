# OPS-14: Enable E2E smoke checks on feature branch CI

## Objective

Configure feature branch CI (`ci-branch.yml`) to run the E2E contract and deployment smoke stage (`run_e2e: true`) while preserving read-only permissions and prohibiting artifact or container image publishing outside the default `master` branch.

## Dependencies

- `OPS-13`

## Owned paths

- `.github/workflows/ci-branch.yml`
- `docs/operations/ci.md`
- `docs/tasks/details/OPS-14.md`

## Acceptance criteria

- `ci-branch.yml` invokes `_reusable-ci.yml` with `with: { run_e2e: true }`.
- Feature branches execute E2E checks with `permissions: { contents: read }` and do not publish container images or artifacts.
- Documentation in `docs/operations/ci.md` reflects that branch CI executes the E2E smoke stage.
- All workflows pass `make workflow-validate`, `python3 tools/contracts/validate.py`, and `git diff --check`.

## Validation commands

- `make workflow-validate`
- `python3 tools/contracts/validate.py`
- `git diff --check`

## Evidence

- `ci-branch.yml` delegates with `run_e2e: true`.
- Workflow validation, contract validation, and git diff checks pass cleanly.
