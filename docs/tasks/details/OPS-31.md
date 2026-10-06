# OPS-31: Centralize runtime and toolchain versions and unify Docker anti-drift build patterns

## Status
`completed`

## Objective
Establish a single authoritative centralized version registry for all container images, base images, language runtimes (Python, Java, Node), and toolchain package managers (`uv`, `mermaid-cli`, `bruno-cli`), parameterize all Dockerfiles with `ARG` defaults to eliminate version drift, and implement automated static SBOM validation to enforce zero drift across Devcontainers, Docker Compose, CI, and local development.

## Background and motivation
As the repository expanded to include Devcontainers, local Compose, documentation rendering containers, test fixtures, and CI workflows, version definitions risked fragmenting across multiple files. To satisfy enterprise software engineering principles (DRY, single source of truth, separation of concerns), all non-secret container and toolchain versions must reside in one authoritative file (`infra/versions.env.example`), Dockerfiles must be parameterized via multi-stage `ARG` directives, and static verification must guarantee zero configuration drift.

## Owned paths
```
infra/versions.env.example
infra/local/.env.example
infra/docker/Dockerfile.jvm
infra/docker/Dockerfile.fast
infra/docker/Dockerfile.dev
infra/docs/Dockerfile
tests/fixtures/invalid_subject_oidc/Dockerfile
.devcontainer/Dockerfile
.devcontainer/docker-compose.devcontainer.yml
tools/ops/validate_sbom_baseline.py
docs/tasks/details/OPS-31.md
docs/implementation/technology-decisions.md
```

## Dependencies
- `OPS-27` (done): Devcontainer version baseline.
- `OPS-28` (done): Devcontainer scaffolding.
- `OPS-29` (done): Devcontainer automation and port forwarding.
- `OPS-30` (done): Devcontainer documentation.

## Architecture and design decisions

### 1. Centralized Version Registry (`infra/versions.env.example`)
`infra/versions.env.example` is designated as the sole authoritative declaration for:
- Infrastructure containers (`POSTGRES_IMAGE`, `RABBITMQ_IMAGE`, `REDIS_IMAGE`, `MAILPIT_IMAGE`, `KEYCLOAK_IMAGE`).
- Build and runtime base images (`JVM_BUILD_IMAGE`, `JVM_RUNTIME_IMAGE`, `NODE_IMAGE`, `PYTHON_IMAGE`, `DEVCONTAINER_BASE_IMAGE`).
- Language runtimes and tools (`PYTHON_VERSION`, `UV_VERSION`, `DEVCONTAINER_JDK_VERSION`, `DEVCONTAINER_NODE_VERSION`, `POSTGRES_CLIENT_VERSION`, `MERMAID_CLI_VERSION`, `BRUNO_CLI_VERSION`).
- Collision-free deterministic host ports (`28xxx` family) and in-network hostnames.

### 2. Parameterized Multi-Stage Dockerfile Pattern
All Dockerfiles (`infra/docker/Dockerfile.jvm`, `Dockerfile.fast`, `Dockerfile.dev`, `infra/docs/Dockerfile`, `tests/fixtures/invalid_subject_oidc/Dockerfile`, `.devcontainer/Dockerfile`) declare `ARG <VAR>=<default>` before the `FROM ${<VAR>}` directive.
- Prevents hardcoded image tags from drifting.
- Allows Compose files and CI builds to inject overridden tags seamlessly via build arguments.

### 3. Compose v2 Service and Include Reuse
- `.devcontainer/docker-compose.devcontainer.yml` uses `include: - path: ../infra/local/docker-compose.yml` to reuse identical infrastructure service definitions rather than duplicating them.
- Build arguments (`DEVCONTAINER_BASE_IMAGE`, `DEVCONTAINER_JDK_VERSION`, `DEVCONTAINER_NODE_VERSION`, `PYTHON_VERSION`, `UV_VERSION`, `POSTGRES_CLIENT_VERSION`) are passed dynamically from the environment.

### 4. Automated Zero-Drift SBOM Static Verification
`tools/ops/validate_sbom_baseline.py` (executed via `make sbom-validate`) enforces:
- All required version keys are present and non-empty in `infra/versions.env.example`.
- `infra/local/.env.example` image definitions match `infra/versions.env.example` exactly.
- `pyproject.toml` Python requirement aligns with `PYTHON_VERSION`.
- Dockerfiles contain no un-parameterized hardcoded `FROM <image>:<tag>` declarations.
- `gradle/libs.versions.toml` centralizes all JVM library dependencies.

## Acceptance criteria
- [x] All runtime, container, and toolchain versions declared in `infra/versions.env.example`.
- [x] All repository Dockerfiles parameterized with `ARG` base images.
- [x] `.devcontainer/docker-compose.devcontainer.yml` passes version build args.
- [x] `tools/ops/validate_sbom_baseline.py` verifies zero drift across configuration files and Dockerfiles.
- [x] `make sbom-validate`, `make python-typecheck`, `make compose-config`, and `make contracts` pass cleanly.

## Verification evidence
- `make sbom-validate` passed: validated Gradle catalog, container baseline, environment alignment, and Dockerfile ARG parameterization.
- `make python-typecheck` passed: 41 source files clean.
- `make compose-config` passed: all compose files validated.
- `make contracts` passed: task registry and contracts valid.
