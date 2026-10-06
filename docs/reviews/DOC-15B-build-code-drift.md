# DOC-15B: Code, build, CI, and test drift review

Reviewed 2026-09-17 against `AGENTS.md`, the task registry, implementation plan,
architecture and quality documents. This review is limited to code, Gradle,
tests, CI, Docker/Compose, imports, and repository hygiene. Findings are
classified so intentional scaffold limits are not mistaken for defects.

## Findings

### High: the reusable workflow declares unsupported workflow-call permissions

`.github/workflows/_reusable-ci.yml` places a `permissions` mapping beneath
`on.workflow_call`. GitHub reusable-workflow syntax supports inputs, secrets and
outputs under `workflow_call`; permissions belong at workflow or job level.
The caller workflows already grant permissions, and the jobs grant their own
permissions, so this declaration is redundant and may cause workflow schema
validation or dispatch failure. Remove it from `workflow_call` and retain the
explicit job permissions.

Evidence: `ruby -e 'require "yaml"; ...'` parses YAML syntax, but does not
validate GitHub's workflow schema. A real GitHub Actions run has not been
available locally.

### Medium: the configured lint command is not a formatting gate

`make lint` runs contract validation and Gradle `check`; it does not run the
Spotless/ktfmt check researched by DOC-14. DOC-14 correctly records 65 existing
formatting violations and the decision not to enable a failing gate. The
documentation should therefore describe `make lint` as repository and Gradle
quality checks, or add a separately named non-gating formatting report command.
Calling this a complete lint gate would be drift from the documented blocker.

### Medium: test fixtures still generate random UUIDs

Production-generated IDs use `libs:ids` and no production `UUID.randomUUID()`
calls were found. Several tests use `UUID.randomUUID()` directly (notifications,
Expense Core messaging, settlements and sync). This is acceptable as test-only
fixture data, but it means the blanket wording “all UUID generation” is broader
than the implementation. If UUIDv7 ordering is part of a test contract, fixtures
need to use the shared generator; otherwise documentation should explicitly
scope the UUIDv7 rule to application-generated identifiers.

### Low: Docker runtime image is not Microsoft OpenJDK

CI actions use `distribution: microsoft` as requested. `infra/docker/Dockerfile.jvm`
still uses `eclipse-temurin:25-jre` for the runtime stage, while the build stage
uses `gradle:9.7.1-jdk25`. This is not an Actions defect, but it is a toolchain
consistency drift if the Microsoft JDK requirement applies to production images.
Decide and document whether the requirement is CI-only; if it applies to images,
select and verify a Microsoft OpenJDK runtime tag before changing it.

### Low: Makefile repeats the complete test suite

`test` invokes `test-unit` and `test-integration`, while both currently execute
the full Gradle `test` task. `ci` also invokes `test` and `check`, with `check`
running validation and tests again. This is functional but increases local CI
time and obscures the intended fast-test/integration-test split. Split task
filters when integration suites exist, then make CI invoke each once.

## Confirmed alignment and hygiene

- Gradle includes four applications and six technical libraries, including the
  later `ids` and `errors` additions; versions are centralized in the version
  catalog.
- CI has separate PR, non-main push, and main workflows, a reusable workflow,
  independent module matrix jobs, and main-only image publication.
- Kotlin source uses explicit imports; no inline fully-qualified production
  calls were found.
- `git ls-files` contains no tracked `build/`, `target/`, `.class`, or generated
  application JAR outputs. The large `build/` trees observed during review are
  ignored local outputs, as intended by `.gitignore`.
- Compose files and Dockerfiles are present for local, development, production,
  and documentation rendering workflows.

## Intentional limitations

Libraries without source currently report `NO-SOURCE` for tests. This is an
intentional scaffold state and should not be represented as meaningful library
coverage. Product journey E2E remains deferred by QA-01, and the ktlint/format
gate remains blocked by DOC-12/DOC-14 evidence.

## Verification evidence

Executed from repository root:

```text
ruby -e 'require "yaml"; Dir[".github/workflows/*.yml"].each { |f| YAML.load_file(f); puts f }'  # exit 0
python3 tools/contracts/validate.py                                                   # exit 0
./gradlew test --no-daemon                                                           # exit 0
git ls-files | rg '(^|/)(build|out|target)/|\.class$|\.jar$'                         # no output
rg -n 'UUID\.randomUUID' --glob '!**/build/**' .                                     # test-only matches
git diff --check                                                                     # pending coordinator changes excluded; review file clean
```

The Gradle test run completed successfully (`BUILD SUCCESSFUL`, 28 actionable
tasks). The review itself does not alter shared trackers; the coordinator must
record this document and commit in `docs/tasks/registry.yaml`, `board.md`, and
`progress.md`.
