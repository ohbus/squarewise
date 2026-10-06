# OPS-27: Register devcontainer version baseline and security hygiene rules

## Status
`completed`

## Objective
Establish central non-secret version definitions for the Devcontainer workspace toolchain, record the architectural rationale and toolchain resolution evidence in repository documentation, and enforce security hygiene rules by excluding devcontainer manifests from application container builds.

## Background and motivation

### Current pain points
1. **No centralized Devcontainer versioning**: `infra/versions.env.example` registers container versions for PostgreSQL, RabbitMQ, Mailpit, Keycloak, and JRE runtimes, but lacks registered variables for the Devcontainer workspace base image, OpenJDK distribution, or Node runtime.
2. **Missing technology decision record**: Repository policy (`GEMINI.md`) mandates that any container or runtime selection must be recorded in `docs/implementation/technology-decisions.md` with resolution evidence and rationale.
3. **Application build context isolation**: Without an explicit entry in `.dockerignore`, `.devcontainer/` files would be included in Docker build contexts during `make docker-build-all` or `infra/docker/Dockerfile.jvm` builds, violating minimal build context hygiene.

## Owned paths
```
infra/versions.env.example
docs/implementation/technology-decisions.md
.dockerignore
docs/tasks/details/OPS-27.md
docs/tasks/registry.yaml
docs/tasks/board.md
docs/tasks/progress.md
```

## Dependencies
- `OPS-25` (done): Standardized Python package management and tooling.
- `OPS-26` (done): Non-Docker local seeder execution.

## Design decisions

### 1. Central version declarations in `infra/versions.env.example`
In accordance with `GEMINI.md` ("Never duplicate versions in build files; record evidence in `infra/versions.env.example` and `technology-decisions.md`"), add:
```env
# Devcontainer workspace baseline (OPS-27)
DEVCONTAINER_BASE_IMAGE=mcr.microsoft.com/devcontainers/base:ubuntu-24.04
DEVCONTAINER_JDK_VERSION=25
DEVCONTAINER_NODE_VERSION=22
```

### 2. Technology decisions documentation
Update `docs/implementation/technology-decisions.md` to document:
- Selection of `mcr.microsoft.com/devcontainers/base:ubuntu-24.04` as the workspace base.
- Microsoft OpenJDK 25 / Temurin 25 as the official Devcontainer JVM runtime (matching CI reusable workflow).
- Node.js 22 LTS for executing `@usebruno/cli@4.1.0` via `npx` during `make bruno-run`.
- The decision to adopt the native Devcontainer Compose topology over Docker-outside-of-Docker (DooD), avoiding root-equivalent host socket exposure.

### 3. Docker build context hygiene
Update `.dockerignore` to add `.devcontainer` under Git, IDE, and developer metadata.

## Acceptance criteria
- [x] `infra/versions.env.example` registers `DEVCONTAINER_BASE_IMAGE`, `DEVCONTAINER_JDK_VERSION`, and `DEVCONTAINER_NODE_VERSION`.
- [x] `docs/implementation/technology-decisions.md` records the toolchain resolution evidence for Java 25, uv, and Node 22.
- [x] `.dockerignore` excludes `.devcontainer` from application container build contexts.
- [x] `tools/ops/validate_sbom_baseline.py` and `check_security_hygiene.py` pass without warnings.

## Validation commands
```bash
make sbom-validate
make security-hygiene
git diff --check
```

## Implementation order
1. Add devcontainer version variables to `infra/versions.env.example`.
2. Add Devcontainer Architecture and Toolchain section to `docs/implementation/technology-decisions.md`.
3. Add `.devcontainer` to `.dockerignore`.
4. Run `make sbom-validate` and `make security-hygiene`.
5. Update task tracking files.
