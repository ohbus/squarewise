# FND-04: Repository workflow Makefile

- Phase: foundation
- Owner: coordinator
- Dependencies: FND-01, FND-03
- Owned paths: `Makefile`, `README.md`, `docs/operations/quickstart.md`

## Outcome

Provide one discoverable command surface for tool checks, contract validation,
tests, coverage, packaging, Compose lifecycle, image builds, production config,
smoke E2E checks, cleanup, and Git status.

## Acceptance

Targets call existing scripts and Gradle tasks, fail on missing prerequisites,
do not embed credentials, and are documented with expected product-scaffold
limitations. `make check`, `make e2e`, and Compose validation must pass.

## Implementation notes

The contributor entry point documents the repository's Python 3.12 baseline,
all five dependency services, the foreground behavior of `make full-up`, and
the environment required for a native Expense Core Gradle run. It also removes
the two dead top-level table-of-contents entries. The `doctor`
target rejects Java runtimes older than 25 so its result matches the Gradle
toolchain used by every application and library.

## Validation evidence

- `make doctor` passed with Docker Compose 5.5.1, Java 25, and Gradle 9.8.0.
- Contract validation passed for all JSON, GraphQL, and task-registry inputs.
- Compose service discovery confirmed the five dependency services and four
  applications described by the README.
- `make -n full-up deps-up acceptance-live` matched the documented command
  entry points, and `git diff --check` passed.
