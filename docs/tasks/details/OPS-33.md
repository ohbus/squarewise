# OPS-33: Advanced CodeQL analysis

## Status

In progress. Local workflow implementation and validation are owned by the
coordinator; hosted default-branch, internal-PR, and fork-PR evidence is still
required before closure.

## Objective

Add an isolated advanced CodeQL workflow that analyzes Java/Kotlin, repository
Python, and GitHub Actions on the default branch and pull requests, including
approved pull requests from forks. Keep untrusted fork code away from secrets
and privileged workflow triggers.

## Dependencies

- `OPS-04`
- `OPS-05`
- `OPS-15`
- `OPS-24`
- `PR-21`

All dependencies are complete.

## Owned paths and handoff

- `.github/workflows/codeql.yml`
- CodeQL sections in `docs/operations/ci.md`
- CodeQL sections in `docs/security/supply-chain-evidence.md`
- `docs/tasks/details/OPS-33.md`
- Coordinator-owned tracking updates in `docs/tasks/registry.yaml`,
  `docs/tasks/board.md`, and `docs/tasks/progress.md`

The coordinator grants this task a scoped handoff from active tasks `OPS-20`,
`DOC-28`, and `OPS-32` for the paths above. Existing reusable and caller CI
workflows remain outside this task.

## Acceptance criteria

1. CodeQL runs on pushes to `master`, pull requests targeting `master`, a
   weekly schedule, and manual dispatch.
2. The matrix analyzes `java-kotlin`, `python`, and `actions` with the default
   high-precision query suite.
3. The Java/Kotlin lane compiles production and test sources with Java 25 and
   the repository Gradle wrapper without running tests or restoring compiled
   task outputs.
4. Fork pull requests use `pull_request`, receive no repository secrets, and
   never execute through `pull_request_target`.
5. Actions use least-privilege permissions and immutable references required by
   repository policy.
6. Python analysis includes both operational tooling and tests. Low and medium
   findings remain reported; staged merge protection later blocks errors and
   high-or-higher security alerts for the CodeQL tool.
7. Local workflow, tracking, security-hygiene, and whitespace validations pass;
   hosted evidence and limitations are recorded accurately.

## Validation commands

```text
./gradlew testClasses --dry-run --no-daemon --console=plain
make workflow-validate
uv run --frozen --no-build python -m unittest tools.coverage.test_report_tools
uv run --frozen --no-build python tools/contracts/validate.py
uv run --frozen --no-build python tools/contracts/validate_public_surface.py
make security-hygiene
git diff --check
```

## Expected hosted evidence

- Successful `java-kotlin`, `python`, and `actions` analyses on `master`.
- Successful analyses and uploaded results for one internal pull request and
  one approved fork pull request.
- Tool Status coverage for all three languages and an initial-alert triage.
- A separate default-branch CodeQL ruleset evaluated before activation with
  `errors` and `high_or_higher` thresholds.

## Implementation principles

The workflow is independent from the existing reusable CI, adds no application
dependency or custom abstraction, reuses the repository's Java/Gradle setup,
and keeps one security-analysis definition. A separate JavaScript lane is
intentionally omitted because the tracked JavaScript is limited to non-shipping
k6 load probes.

## Local implementation evidence (2026-10-27)

- Added the isolated three-lane workflow with the documented triggers,
  least-privilege permissions, immutable CodeQL references, and manual JVM
  extraction build.
- `./gradlew testClasses --no-daemon --no-build-cache --console=plain` passed
  all 50 actionable tasks in 31 seconds.
- `make workflow-validate`, direct PyYAML parsing, both contract validators,
  `make security-hygiene`, the focused immutable-action policy test, and
  `git diff --check` passed.
- The full `tools.coverage.test_report_tools` module remains red on 13 failures
  and two errors that predate this task: stale QA-10 source/report counts,
  references to retired error infrastructure and a removed coverage-inventory
  workflow job, and unavailable generated JaCoCo ledger data. Its focused
  action-reference policy passes with the new workflow.
- Hosted CodeQL runs, initial-alert triage, fork execution, and the evaluated
  then active merge-protection ruleset remain pending until this commit is
  available on GitHub.
