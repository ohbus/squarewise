# Continuous integration and delivery

Three thin workflows select policy: `ci-pr.yml` validates pull requests,
`ci-branch.yml` validates non-master pushes, and `ci-master.yml` validates master and
publishes four application images. Verification, checks, and E2E execution live
in `_reusable-ci.yml`, while container image delivery lives in `ci-master.yml` so
feature branch and pull request workflows can operate with read-only permissions
without encountering GitHub Actions reusable workflow permission validation errors.
The PR, branch, and master callers grant `pull-requests: read` because the reusable
dependency-review job declares that least-privilege permission; the job remains
skipped for non-PR events.

PR and non-master branch runs now calculate a changed-scope plan before the Gradle
matrix. The plan selects changed modules plus their reverse project dependents and
selects only the E2E streams affected by the changed paths. Master and manually
dispatched master runs pass `full_run: true` and retain the complete matrix and all
E2E streams. Repository-wide preflight, lint, contract, security, and Sonar jobs
remain global checks where their tools need the complete repository; they are not
pretended to be module-local checks. The scope resolver is
`tools/ci/changed_scope.py`, with behavior tests in
`tests/tools/test_changed_scope.py`.

The reusable workflow applies Gradle dependency and build caching with
content-addressed keys and restore fallbacks. E2E uses the same policy, while
Docker BuildKit layers use the GitHub Actions cache backend. Cache misses only
reduce speed and never change verification behavior.
Workflows declare `FORCE_JAVASCRIPT_ACTIONS_TO_NODE24: 'true'` in their top-level
`env` blocks, ensuring all runner steps and composite JavaScript actions run on
Node 24 ahead of runner deprecation deadlines.

Each verification-matrix job receives isolated PostgreSQL 17 and RabbitMQ 4.3
service containers. The CI-only RabbitMQ service uses the protocol image rather
than the management variant because verification and Sonar do not use the
management UI; the local Compose topology retains management for operator
inspection. Docker health checks (`pg_isready` and
`rabbitmq-diagnostics ping`) must pass before job steps begin. RabbitMQ receives
a 60-second health-check start period, 10-second health-check timeout, and 24
five-second retries to accommodate slow hosted-runner startup without weakening
the readiness command. No service
state is shared between matrix jobs. The parallelized E2E jobs instead let the complete
local Compose topology exclusively own PostgreSQL, RabbitMQ, Keycloak, Redis, and
Mailpit; declaring duplicate job services on the host would contend for host ports `5432`
and `5672`.

To achieve fast feedback and conserve runner CPU, the E2E stages eliminate redundant
Gradle test runs and compilation. Instead, the parallel E2E jobs depend directly on
`verify` and consume the pre-built application `bootJar` artifacts (`app-jar-*`),
allowing `Dockerfile.fast` to package lightweight runtime containers in seconds.
The Docker context explicitly re-includes only these downloaded application JARs
from the otherwise ignored Gradle `build/` directories. Each E2E job verifies all
four artifact paths before Compose starts, so a missing artifact fails at the
handoff instead of later as an opaque Docker `COPY` checksum error.
The monolithic E2E stage is split into three parallel streams:
1. `e2e-edge-and-security`: Contract smoke, Redis authentication-cache
   eviction/outage/restart checks (`make e2e-auth-cache`), public GraphQL HTTP
   and WebSocket rate-limit surface checks (`make e2e-auth-surfaces`), Accounts
   lookup-isolation checks (`make e2e-auth-no-accounts`), negative OIDC JWT
   path probes, and live REST edge cases (`make e2e-rest-edge`).
2. `e2e-product-and-offline`: Passwordless auth-email delivery (`make e2e-auth-email`), public acceptance suite (`make acceptance-live`), ordered Bruno collection (`make bruno-run`), live multi-service product lifecycle (`make e2e-live`), and offline client synchronization / replay resilience (`make e2e-offline`).
3. `e2e-concurrency-and-chaos`: Real-time WebSocket GraphQL subscription invalidation, concurrent member edit race resolution (`make e2e-concurrency`), message broker outage chaos, and transactional outbox drain recovery (`make e2e-chaos`).

An aggregate gate job (`e2e-gate`) monitors the selected parallel streams and provides
a single status check for branch protection rules. It fails only when a selected
stream fails, or when the shared preflight/artifact preparation fails. Intentionally
unselected streams are reported as not evaluated and do not fail the gate. If no E2E
stream is selected, the gate is skipped rather than falsely reporting a full E2E pass.
Master always selects all three streams.

The matrix tests every application and library in parallel after a single
preflight, validates contracts, REST path structure, GraphQL schema/resolver
parity, and Compose files, runs Gradle `test`, `check`, and JaCoCo, and builds
application jars. The lightweight checks also run the acceptance unit suite,
workflow YAML parsing, strict Python typing via `uv run --frozen --no-build mypy`, and
`git diff --check`. The nine matrix entries are capped at four concurrent jobs
because each entry owns PostgreSQL and RabbitMQ service containers; this avoids
hosted-runner broker startup contention without removing or changing any shard.
Jobs use Microsoft Build of OpenJDK. Python dependencies
and tooling are deterministically managed via `pyproject.toml` and `uv.lock`.
CI workflows install dependencies via the immutable commit
`astral-sh/setup-uv@d0cc045d04ccac9d8b7881df0226f9e82c39688e` (the `v6` tag)
with `uv sync --frozen --no-build`, running tools and scripts via
`uv run --frozen --no-build`. `--frozen` prevents lockfile resolution changes;
`--no-build` prevents dependency/project build hooks from executing during the
tool-environment setup and invocation. Local Python tooling
must use `uv` rather than installing packages into the system interpreter.
All non-GitHub-owned actions are also pinned to full commit SHAs; the repository
test suite rejects floating third-party action tags. GitHub-owned actions remain
on their supported major tags because the repository policy scopes this pinning
requirement to third-party actions.
The lightweight lint job also installs the same Microsoft JDK 25 and Gradle
setup before generating the CycloneDX SBOM; every job that invokes Gradle owns
its toolchain setup explicitly.
QA-10 coverage is reported per module in the Gradle matrix and aggregated by
the follow-up `qa10-coverage-inventory` job, which publishes one JSON inventory
artifact. The aggregation runs only after every verification shard succeeds;
when verification fails, it is skipped so it cannot mask the original shard
failure with an incomplete-artifact error. A single matrix shard cannot prove repository-wide coverage. The
eventual blocking gate command is
`uv run --frozen --no-build python tools/coverage/report_branch_gaps.py --format json --fail-on-gaps`.
The current local discovery baseline is 35 methods containing 61 missed
branches. This is a backlog signal, not a target to reduce by deleting
implementation; hosted closure requires tests or reviewed structural
classification for every record.
Every test run publishes a readable test summary directly to GitHub Actions job
summaries (`test-summary/action@v2`) and uploads JUnit XML and HTML reports as
job artifacts with `if: always()` retention.
A dedicated `sonar` job runs SonarQube / SonarCloud static analysis with cached
Sonar packages (`~/.sonar/cache`) and Gradle cache, sending coverage and test analysis
for `master` and pull requests.
For application projects, `_reusable-ci.yml` uploads the built executable
`bootJar` artifact (`app-jar-<service>`). Master image publishing in `ci-master.yml`
downloads this pre-built artifact and packages the runtime image with
`infra/docker/Dockerfile.fast` (`eclipse-temurin:25-jre`), eliminating redundant
JVM compilation inside Docker.
Master image jobs create an explicit `docker-container` Buildx builder before
using the GitHub Actions cache backend; each service matrix entry has its own
cache scope. They publish SHA and branch tags to
`ghcr.io/<owner>/squarewise-<service>` with the job-scoped `GITHUB_TOKEN`.

Run the complete hosted verification equivalent locally with:

```sh
make ci
```

This runs the same contract and Compose preflight, then asks Gradle to execute
tests, checks, JaCoCo reporting, and application packaging with `--parallel`.
Gradle's project task graph avoids rebuilding work that is already up to date.
Run `make ci-e2e` when the E2E smoke stage should be included. The smaller
targets remain useful when iterating: `make contracts`, `make compose-config`,
`make test`, `make coverage`, and `make package`.

Hosted verification intentionally fans out the nine module checks across
separate runners; local execution shares one checkout and JVM, so Gradle
parallelism is the corresponding local optimization. Hosted master image builds
fan out only after every verification matrix job succeeds. Local image builds
are available independently through `make docker-build-all` or
`make docker-build-<service>` and never push to a registry.

Workflow files are syntax-checked with a pinned `yamllint` invocation installed
ephemerally through `uvx`; CI does not assume Ruby is present on slim runners.
The reusable workflow is the single source for action versions, Microsoft
OpenJDK setup, and verification matrix membership; `ci-master.yml` owns GHCR
delivery policy once verification succeeds. Trigger workflows only select event
policy and inputs. Kotlin ktlint remains tracked as DOC-12
because its verified toolchain is not compatible with the current Kotlin
baseline.

Use `make workflow-validate` to parse all workflow files and `make acceptance`
to produce `build/reports/acceptance/qa-01.json`.

The hosted E2E jobs retain raw acceptance JSON, Bruno JSON, and suite logs as
artifacts with `if: always()`, including when a suite or service startup fails.
The product job writes Bruno output to `build/reports/e2e/bruno.json`; the
other retained files identify the suite and execution order. These raw files
are evidence inputs, not automatic pass claims. To close QA-10 operation rows,
the product job also attempts to write
`build/reports/e2e/qa10-operation-execution.json` using
`tools/coverage/normalize_bruno_execution.py`. The normalizer credits only
unique collection-path-to-contract mappings with assertion-backed checks; it
preserves failed/blocked results and skips ambiguous or surface-mismatched
fixtures. The artifact is therefore partial evidence, not blanket closure of
the 54-operation matrix. The same hosted step feeds it to the operation-gap
reporter and retains `qa10-operation-inventory.json`; review the resulting
`EXECUTION-ARTIFACT-PASSED`, `FAILED`, and `BLOCKED` statuses before crediting
any operation.
