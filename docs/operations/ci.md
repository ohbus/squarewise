# Continuous integration and delivery

Three thin workflows select policy: `ci-pr.yml` validates pull requests,
`ci-branch.yml` validates non-master pushes, and `ci-master.yml` validates master and
publishes four application images. Verification, checks, and E2E execution live
in `_reusable-ci.yml`, while container image delivery lives in `ci-master.yml`.
The PR, branch, and master callers configure read-only workflow-level permissions,
and grant `pull-requests: read`, `id-token: write`, and `attestations: write` explicitly
at the calling job level (`ci`) so nested reusable workflow jobs (`lint`, `verify`, and other
artifact producers) can attest build provenance without escalating privileges across the workflow.

PR and non-master branch runs now calculate a changed-scope plan before the Gradle
matrix. The plan selects changed modules plus their reverse project dependents and
selects only the E2E streams affected by the changed paths. Master and manually
dispatched master runs pass `full_run: true` and retain the complete matrix and all
E2E streams. Repository-wide preflight, lint, contract, security, and Sonar jobs
remain global checks where their tools need the complete repository; they are not
pretended to be module-local checks. The scope resolver is
`tools/ci/changed_scope.py`, with behavior tests in
`tests/tools/test_changed_scope.py`.

The scope job keeps human diagnostics on the step log and writes only the
resolver's machine-readable `name=value` records to `$GITHUB_OUTPUT`. A
first-push fallback logs its explanation normally and uses a workspace marker
to request full scope; prose must never be appended to the GitHub Actions
output file because the runner parses that file as structured data. Native
`paths` and `paths-ignore` filters can gate simple globs, but they cannot
calculate this repository's reverse Gradle-dependent closure or classify the
three E2E streams, so the small typed resolver and first-party matrix jobs
remain necessary.

The comprehensive E2E job applies the notification delivery-limit variables
again when it restarts Compose after the invalid-subject checks. Compose
interpolation is evaluated at startup, so a restart without those variables
would silently restore the default ten-per-minute policy and invalidate the
suppression probe.

The reusable workflow delegates Gradle dependency, wrapper, and build caching
natively to `gradle/actions/setup-gradle`, providing content-addressed caching
and automatic cache cleanup without conflicting user-home restoration steps.
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
   lookup-isolation checks (`make e2e-auth-no-accounts`), cross-process BFF
   admission (`make e2e-auth-bff-replicas`), negative OIDC JWT path probes, and
   live REST edge cases (`make e2e-rest-edge`).
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
`astral-sh/setup-uv@c18668ad3cf93ea998bef934396af7bb5c839dc7` (the `v10.2.0` tag)
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
QA-10 coverage is reported per module in the Gradle matrix. The repository-wide
inventory remains a local QA-10 audit command because no matrix shard can prove
aggregate coverage, and the former artifact-only follow-up job was removed from
hosted CI. The eventual blocking gate command is
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
for `master` and internal pull requests when `SONAR_TOKEN` is available. For pull
requests originating from forks (where repository secrets are withheld by GitHub Actions
security boundaries), the Sonar job is gracefully skipped to uphold the principle of least
privilege and prevent arbitrary code execution vulnerabilities (such as "pwn request" attacks),
while all unit, integration, acceptance, contract, and E2E verification suites run in full.
For application projects, `_reusable-ci.yml` uploads the built executable
`bootJar` artifact (`app-jar-<service>`). Master image publishing in `ci-master.yml`
downloads this pre-built artifact and packages the runtime image with
`infra/docker/Dockerfile.fast` (`eclipse-temurin:25-jre`), eliminating redundant
JVM compilation inside Docker.
In adherence to industry standards for secure software supply chains (such as SLSA / OpenSSF provenance standards), cryptographic attestations must be generated for all build artifacts and container images. Every uploaded workflow artifact (SBOMs, application `bootJar` packages, JUnit/HTML test and coverage reports, and E2E diagnostic bundles) is attested immediately after creation and upload using GitHub's signed build-provenance action (`actions/attest-build-provenance`). The published container images are attested against their pushed immutable container digests. All workflows and jobs that produce or invoke jobs producing build artifacts must declare explicit OIDC and attestation permissions (`id-token: write` and `attestations: write`), and artifact digests—never mutable tags or names—are strictly used as attestation subjects.
Master image jobs create an explicit `docker-container` Buildx builder before
using the GitHub Actions cache backend; each service matrix entry has its own
cache scope. They publish SHA and branch tags to
`ghcr.io/<owner>/squarewise-<service>` with the job-scoped `GITHUB_TOKEN`.

Run the complete hosted verification equivalent locally with:

```sh
make ci
```

The local CI target includes the repository `release-gate`, including the ERRC-28
rollout manifest/regression gate and observability/release prerequisites, before
Gradle verification begins.

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

The hosted lint gate also runs the ERRC-28 rollout manifest/ledger validator and
its regression suite, keeping canary configuration safety checks in parity with
the local `validate` target.

The repository `release-gate` includes the same rollout validation before
production-prerequisite checks are evaluated.

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

## CI secrets and environment variables

Squarewise workflows ([`.github/workflows/_reusable-ci.yml`](../../.github/workflows/_reusable-ci.yml))
support secure secret overrides through GitHub Actions repository secrets while falling back
to safe, deterministic local fixtures when secrets are omitted:

| Variable | GitHub Secret Name | Purpose |
|---|---|---|
| `SQUAREWISE_SECURITY_CREDENTIAL_DIGEST_SECRET` | `SQUAREWISE_SECURITY_CREDENTIAL_DIGEST_SECRET` | 32-byte Base64 key for passwordless HMAC token digest |
| `SQUAREWISE_SECURITY_AUTH_EMAIL_ENVELOPE_KEY` | `SQUAREWISE_SECURITY_AUTH_EMAIL_ENVELOPE_KEY` | 32-byte Base64 key for AES-GCM auth email encryption |
| `REDIS_PASSWORD` | `REDIS_PASSWORD` | Redis authentication password |
| `POSTGRES_PASSWORD` | `POSTGRES_PASSWORD` | PostgreSQL database user password |
| `RABBITMQ_DEFAULT_PASS` | `RABBITMQ_DEFAULT_PASS` | RabbitMQ broker password |
| `KEYCLOAK_ADMIN_PASSWORD` | `KEYCLOAK_ADMIN_PASSWORD` | Keycloak admin console bootstrap password |
| `SONAR_TOKEN` | `SONAR_TOKEN` | SonarCloud code quality scanner token |

To generate a complete, fresh set of cryptographically strong secrets for GitHub Actions:

```sh
make generate-secrets
# or: python3 tools/ops/generate_secrets.py
```

A complete configuration catalog and policy matrix is documented in
[`infra/local/env-secrets-matrix.example`](../../infra/local/env-secrets-matrix.example).

