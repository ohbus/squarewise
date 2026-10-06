# Supply chain and deployment security evidence (SEC-011)

**Date:** 2026-09-29  
**Scope:** Dependency management, SBOM generation, image hardening, CI pipeline, and deployment configuration.

---

## Implemented controls

### Dependency centralization

All JVM library versions and Gradle plugin versions are declared in
[`gradle/libs.versions.toml`](../../gradle/libs.versions.toml) as the single authoritative
catalog. No version strings are duplicated in individual module build files.
The `check_architecture.py` gate enforces this on every CI run.

### SBOM generation

CycloneDX SBOMs are generated per application on every CI run:
```bash
./gradlew cyclonedxBom
```
The `validate_sbom_baseline.py` gate asserts that the generated SBOM files exist and
contain at least the expected number of declared dependencies.

**Local evidence (2026-09-29):**
```
./gradlew cyclonedxBom: BUILD SUCCESSFUL (24 tasks)
uv run --frozen --no-build python tools/ops/validate_sbom_baseline.py: SBOM dependency baseline validation passed
```

### Secret and hygiene scanning

The `check_security_hygiene.py` script scans all tracked files for obvious credential patterns
(passwords, tokens, private key literals). The scan ran clean against 1,035 tracked files:
```
security hygiene passed: scanned 1035 tracked files
```

The script is run in CI as part of the `security-hygiene` Make target and the
`_reusable-ci.yml` workflow.

### Image hardening baseline

All application container images use:
- Multi-stage builds (builder + minimal runtime layer).
- Non-root `squarewise` user (UID 1001).
- No shell utilities in the runtime image layer where possible.
- Read-only file system flag at the Compose level.
- Resource limits (CPU/memory) declared in production Compose.

Reference: [`infra/docker/`](../../infra/docker/) and [`infra/deploy/docker-compose.prod.yml`](../../infra/deploy/docker-compose.prod.yml).

### Build artifact and container image attestations

In adherence to industry software supply chain standards (such as SLSA Build Level 2/3 and OpenSSF guidelines), cryptographic build-provenance attestations are mandatory for all build artifacts and published container images:
- Every uploaded CI artifact (including CycloneDX SBOMs, Spring Boot `bootJar` application packages, JUnit/HTML test reports, and end-to-end diagnostic dumps) is cryptographically attested with GitHub's signed build-provenance mechanism (`actions/attest-build-provenance`).
- Published container images are attested against their immutable SHA256 digest subjects.
- All jobs generating or orchestrating artifact generation explicitly declare `id-token: write` and `attestations: write` permissions.

The `workflow-validate` Make target lints workflow YAML structure:
```bash
make workflow-validate  # yamllint
```

**Remaining open controls (require production-environment completion):**

| Control | Status | Gate |
|---|---|---|
| Container vulnerability scanning (Trivy/Snyk) | Not yet enforced in CI | QA-08 / production release gate |
| Image signing and provenance attestations (Sigstore/cosign) | Not yet implemented | OPS-20 / production release gate |
| Dependency renovation / auto-update policy | Manual review only | Production release gate |
| License policy enforcement | Manual review only | Production release gate |
| Reproducible build verification | Not yet verified | Production release gate |
| Admission control in target environment | Not yet provisioned | Production release gate |

These open controls are explicitly listed in [`docs/quality/production-validation.md`](../quality/production-validation.md)
as QA-08 / OPS-20 release-gated items. No production approval is claimed until each control
has a linked implementation commit, environment evidence, and reviewer sign-off.

---

## CI evidence summary

The following commands are executed on every CI run and must all pass before a build is
marked green:

| Check | Command | Current status |
|---|---|---|
| Contract validation | `uv run --frozen --no-build python tools/contracts/validate.py` | ✅ 207 tasks, all links valid |
| Public surface validation | `uv run --frozen --no-build python tools/contracts/validate_public_surface.py` | ✅ 45 REST, 9 GraphQL roots |
| Security hygiene scan | `uv run --frozen --no-build python tools/ops/check_security_hygiene.py` | ✅ 1035 files clean |
| Architecture boundary check | `uv run --frozen --no-build python tools/ops/check_architecture.py` | ✅ Passed |
| SBOM baseline validation | `uv run --frozen --no-build python tools/ops/validate_sbom_baseline.py` | ✅ Passed |
| Python type checking | `uv run --frozen --no-build mypy tests tools` | ✅ Passed |
| Workflow lint | `make workflow-validate` | ✅ Passed |
| JVM full test suite | `./gradlew test check jacocoTestReport bootJar --parallel` | ✅ Passed (73 tasks) |
| Git diff check | `git diff --check` | ✅ Clean |
