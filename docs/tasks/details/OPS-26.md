# OPS-26 — Changed-scope PR and branch CI with full master verification

## Status

In progress. The workflow and local scope tests are implemented; hosted CI
execution remains required before closure.

## Objective

Reduce PR and non-master branch CI fan-out to the changed Gradle modules and
their reverse project dependents, and to the affected E2E streams. Master and
manual master runs must retain the complete verification matrix and all E2E
streams.

Intentionally unselected E2E streams must not fail the aggregate gate or be
reported as passed. A selected stream failure, shared preflight failure, or
E2E artifact-preparation failure must fail the gate.

## Owned paths

- `.github/workflows/_reusable-ci.yml`
- `.github/workflows/ci-pr.yml`
- `.github/workflows/ci-branch.yml`
- `.github/workflows/ci-master.yml`
- `tools/ci/changed_scope.py`
- `tests/tools/test_changed_scope.py`
- `docs/operations/ci.md`
- `docs/tasks/details/OPS-26.md`
- `docs/tasks/registry.yaml`
- `docs/tasks/board.md`
- `docs/tasks/progress.md`

## Acceptance criteria

1. Pull-request and non-master push workflows calculate changed paths from the
   PR base or push predecessor; first-push and manual branch runs fall back to
   the full scope.
2. The verification matrix selects changed modules and reverse Gradle
   dependents; repository-wide build, contract, infrastructure, tooling, and
   workflow changes select all modules.
3. Master runs select all nine verification modules, all three E2E streams,
   QA-10 aggregate coverage, Sonar, and the existing image-publishing path.
4. Selected E2E streams receive all application runtime artifacts without
   rerunning the selected module test matrix for unchanged modules.
5. The E2E gate fails on selected-stream, preflight, or artifact failures;
   skipped unselected streams do not fail it; an empty E2E scope is skipped.
6. Global repository-wide checks remain explicitly documented as global and are
   not misrepresented as changed-module checks.
7. Scope behavior has typed unit tests for full, documentation-only, shared
   library, E2E-only, and build-configuration changes.

## Validation commands

```text
uv run --frozen --no-build python -m unittest tests/tools/test_changed_scope.py tests/tools/test_e2e_gate.py
uv run --frozen --no-build mypy tools/ci tests/tools/test_changed_scope.py tests/tools/test_e2e_gate.py
uv run --frozen --no-build yamllint -d '{extends: relaxed, rules: {truthy: disable, line-length: disable}}' .github/workflows
uv run --frozen --no-build python -c "import yaml; from pathlib import Path; [yaml.safe_load(path.read_text(encoding='utf-8')) for path in Path('.github/workflows').glob('*.yml')]"
uv run --frozen --no-build python tools/contracts/validate.py
uv run --frozen --no-build python tools/contracts/validate_public_surface.py
git diff --check
```

Hosted evidence must confirm a documentation-only PR, a single-module PR, a
shared-library PR, an E2E-only PR, a branch first push, and a master push. The
hosted matrix and gate results must show selected/skipped scope explicitly.

The aggregate gate decision is implemented in the typed
`tools/ci/e2e_gate.py` helper and invoked by the reusable workflow. Its focused
tests prove that unselected `skipped` streams are neutral and not reported as
passed, while selected stream, shared preflight, and artifact-preparation
failures remain fatal. Hosted matrix execution evidence is still required.
