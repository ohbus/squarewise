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
- Local implementation commit: `bdf0536ee8af9b0bcb0e8c5503d713b288ea8178`.

## Hosted PR-55 follow-up (2026-10-27)

The first hosted CodeQL run (`40510007886`) passed the Python and Actions lanes
but the Java/Kotlin lane failed while compiling `:app:accounts:compileKotlin`
with `java.lang.OutOfMemoryError: Java heap space` (job `122209107187`). The
workflow now gives the manual Gradle extraction build a 4 GiB heap through
`GRADLE_OPTS`; the replacement hosted run and fork-PR evidence remain pending.

### Initial alert triage increment

The CodeQL ReDoS finding in `tools/contracts/validate_public_surface.py` is
addressed by replacing ambiguous repeated whitespace with horizontal
whitespace plus mandatory newline boundaries. Focused regression coverage
protects multiline resolver discovery and non-matching long input.

Commit `71ed1a3a` implements this increment. The focused validator tests,
public-surface validation, `make python-typecheck`, and `git diff --check`
passed.

### BFF CSRF alert triage increment

Added an end-to-end WebFlux transport regression proving a cookie-authenticated
GraphQL mutation is rejected before any gateway or resolver interaction when
the CSRF proof is missing. The existing custom `BrowserCsrfWebFilter` remains
the intentional enforcement boundary while Spring's generic CSRF filter stays
disabled for bearer-native requests.

Commit `5dc30569` implements this increment. The focused BFF transport and
CSRF tests passed.
