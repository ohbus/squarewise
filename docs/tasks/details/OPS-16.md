# OPS-16: Standardize top-level CI environment and Node 24 runtime enforcement

## Objective

Configure `FORCE_JAVASCRIPT_ACTIONS_TO_NODE24: 'true'` centrally across CI workflows at the top-level `env` block so all workflow jobs, steps, and composite actions run with Node 24 ahead of GitHub Actions' upcoming Node runtime deprecations.

## Root Cause & Architecture Decisions

- GitHub Actions is deprecating older Node runtimes for JavaScript actions and transitioning default runners to Node 24. Setting `FORCE_JAVASCRIPT_ACTIONS_TO_NODE24=true` opts into the Node 24 execution environment proactively.
- GitHub Actions supports top-level workflow `env` declarations, which are automatically inherited by all jobs and steps in that file.
- Reusable workflows (`workflow_call`) do not inherit top-level `env` from caller workflows. Therefore, setting `FORCE_JAVASCRIPT_ACTIONS_TO_NODE24: 'true'` in `_reusable-ci.yml` centrally governs all verification (`preflight`, `lint`, `verify` matrix, `e2e`) jobs and their steps.
- Setting it at the top level of `ci-master.yml` covers the standalone `images` container publishing job.

## Dependencies

- `OPS-15`

## Owned paths

- `.github/workflows/_reusable-ci.yml`
- `.github/workflows/ci-master.yml`
- `docs/operations/ci.md`
- `docs/tasks/details/OPS-16.md`

## Acceptance criteria

- `.github/workflows/_reusable-ci.yml` declares `FORCE_JAVASCRIPT_ACTIONS_TO_NODE24: 'true'` in its top-level `env:` block.
- `.github/workflows/ci-master.yml` declares `FORCE_JAVASCRIPT_ACTIONS_TO_NODE24: 'true'` in its top-level `env:` block.
- `docs/operations/ci.md` documents this global environment policy.
- `make workflow-validate`, `python3 tools/contracts/validate.py`, and `git diff --check` pass.

## Validation commands

- `make workflow-validate`
- `python3 tools/contracts/validate.py`
- `git diff --check`

## Evidence

- Workflows validate with Ruby YAML parser and contract validator.
- Top-level `env` block is present and properly formatted.
