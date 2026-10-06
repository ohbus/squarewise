# OPS-15: Publish CI test results, pass down built application artifacts, and optimize caching

## Objective

Enhance GitHub Actions CI workflows to:
1. Publish test results in GitHub Actions accepted format (formatted step summary and retained XML/HTML test reports).
2. Pass down pre-built application JAR artifacts from the reusable `verify` matrix jobs to downstream workflows/jobs (such as `ci-master.yml` `images` job).
3. Avoid rebuilding application JARs inside Docker during container image creation by switching `ci-master.yml` to use `infra/docker/Dockerfile.fast` with downloaded pre-built JARs.
4. Ensure Gradle dependency caches, build caches, and Docker BuildKit GHA caches are fully leveraged across CI runs.

## Root Cause & Architecture Decisions

- Currently, the `verify` matrix in `_reusable-ci.yml` runs `./gradlew ${{ matrix.path }}:bootJar`, producing the bootable jar, but discards it when the matrix runner shuts down.
- `ci-master.yml` previously ran `Dockerfile.jvm`, which cloned/copied the entire repo and invoked Gradle to rebuild the project from scratch inside Docker for all 4 application images.
- By uploading the built jar (`squarewise-${{ matrix.name }}-0.1.0-SNAPSHOT.jar`) as an artifact (`app-jar-${{ matrix.name }}`) during `verify`, `ci-master.yml` can download it and build images in seconds using `Dockerfile.fast` (`eclipse-temurin:25-jre` base, simple `COPY` of the pre-built jar).
- For test results, Gradle produces standard JUnit XML files at `**/build/test-results/test/TEST-*.xml` and HTML reports at `**/build/reports/tests/test/`. Uploading test reports with `actions/upload-artifact@v4` (with `if: always()`) preserves them for inspection.
- Using `test-summary/action@v2` (with `if: always()`) renders test counts, passes, failures, and execution times directly into the GitHub Actions step summary (`$GITHUB_STEP_SUMMARY`) without requiring elevated permissions like `checks: write`.

## Dependencies

- `OPS-13`
- `OPS-14`

## Owned paths

- `.github/workflows/_reusable-ci.yml`
- `.github/workflows/ci-master.yml`
- `docs/operations/ci.md`
- `docs/tasks/details/OPS-15.md`

## Acceptance criteria

- `_reusable-ci.yml` `verify` job uploads test reports (`actions/upload-artifact@v4`) and renders test summaries (`test-summary/action@v2`) with `if: always()`.
- `_reusable-ci.yml` `verify` job uploads built application JAR artifacts for app matrix entries.
- `_reusable-ci.yml` `e2e` job uploads test reports and renders test summary with `if: always()`.
- `ci-master.yml` downloads pre-built JAR artifacts and builds container images using `infra/docker/Dockerfile.fast`, eliminating redundant Gradle builds inside Docker.
- Workflows maintain least privilege: `_reusable-ci.yml` requires only `contents: read`; `ci-master.yml` has `packages: write` for image publishing.
- `docs/operations/ci.md` documents test summary rendering and artifact pass-down.
- `make workflow-validate`, `python3 tools/contracts/validate.py`, and `git diff --check` pass.

## Validation commands

- `make workflow-validate`
- `python3 tools/contracts/validate.py`
- `git diff --check`

## Evidence

- Workflows validate with Ruby YAML parser and contract validator.
- Artifact upload and test summary steps configured with `if: always()`.
- Image build workflow configured with `Dockerfile.fast` and artifact download.
