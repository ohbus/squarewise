# OPS-32: Changed-scope PR and branch CI with full master verification

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
- `docs/operations/intellij.md`
- `docs/implementation/technology-decisions.md`
- `settings.gradle.kts`
- `gradle.properties`
- `docs/tasks/details/OPS-32.md`
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
8. The Gradle build applies Develocity plugin 4.5.0; CI Gradle invocations
   publish tagged Build Scans without affecting local publication behavior.

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

Hosted API inspection also confirmed that master run `37355348264` and PR run
`37348315292` completed successfully with all selected E2E jobs and retained
E2E artifacts. A later feature-branch run (`37367449305`) was cancelled, so its
skipped jobs are not treated as a passing matrix result. These runs predate the
current branch HEAD and require a current-revision rerun for closure.

## Develocity build scans

The build applies `com.gradle.develocity` version `4.5.0` in
`settings.gradle.kts`, using the root `gradle.properties` value because Gradle
resolves settings plugins before generating the project version-catalog accessors.
CI passes `--scan` to each Gradle invocation, accepts the published Gradle terms
of use non-interactively, tags scans as `CI`, and waits for upload completion.
Local builds do not publish scans but retain the plugin instrumentation required
by IntelliJ's live build timeline.

## Implementation note

The first-push full-scope fallback logs its diagnostic to the workflow step
log and signals full scope through `full-run-fallback.txt`. Only
machine-readable `name=value` records are written to `$GITHUB_OUTPUT`; this
preserves the GitHub Actions output-file contract. Native `paths` filters are
not a replacement for the resolver because they do not provide reverse
Gradle-dependent selection or the repository's E2E stream classification.
