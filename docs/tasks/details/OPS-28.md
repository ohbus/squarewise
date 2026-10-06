# OPS-28: Create devcontainer scaffolding and multi-service compose integration

## Status
`completed`

## Objective
Implement an unprivileged, multi-service OCI Devcontainer workspace conforming to the Devcontainer specification, utilizing a native Docker Compose override (`dockerComposeFile`) attached to the `squarewise-local-net` bridge network, without Docker-outside-of-Docker (DooD) socket mounts, and configuring optimized persistent caching for Gradle and uv.

## Background and motivation
The repository currently supports local development via Docker Compose and host Gradle runs. However, onboarding a new developer requires manual installation of Java 25, Python 3.12+, uv, Node.js 22, and PostgreSQL client binaries. Providing a pre-configured Devcontainer removes local toolchain friction while preserving host isolation and clean dependency boundaries.

## Owned paths
```
.devcontainer/Dockerfile
.devcontainer/docker-compose.devcontainer.yml
.devcontainer/devcontainer.json
docs/tasks/details/OPS-28.md
```

## Dependencies
- `OPS-26` (done): Decoupled database seeder and persistent postgres volume.
- `OPS-27` (done): Registered Devcontainer toolchain baseline and version pins.

## Architecture and design decisions

### 1. Native Compose topology without Docker-outside-of-Docker
Instead of mounting `/var/run/docker.sock` (which grants root-equivalent host access and fails in many containerized CI/cloud environments), the workspace container runs as an unprivileged service (`devcontainer`) participating directly in `squarewise-local-net`:
```yaml
services:
  devcontainer:
    build:
      context: .
      dockerfile: Dockerfile
    volumes:
      - ..:/workspace:cached
      - gradle-cache:/home/vscode/.gradle
      - uv-cache:/home/vscode/.cache/uv
    network_mode: service:postgres # or default bridge network squarewise-local-net
    command: sleep infinity
```

### 2. Workspace container toolchain
The workspace image (`.devcontainer/Dockerfile`) is derived from `mcr.microsoft.com/devcontainers/base:ubuntu-24.04` and installs:
- Microsoft OpenJDK 25 (matching CI workflow and Gradle toolchain).
- Python 3.12+ and `uv` package manager.
- Node.js 22 LTS for executing `@usebruno/cli@4.1.0`.
- `postgresql-client` for direct TCP CLI operations (`psql`).
- `curl`, `jq`, and standard developer utilities.

### 3. I/O and build cache optimization
To mitigate Windows 9P / Hyper-V filesystem sluggishness:
- The workspace directory is mounted with `:cached`.
- Named Docker volumes (`gradle-cache` and `uv-cache`) ensure dependency caches reside on the native container filesystem.
- Gradle daemon settings configure VFS watching, parallel builds, and calibrated heap limits.

### 4. Deterministic port mapping and container environment
`.devcontainer/devcontainer.json` specifies:
- `forwardPorts`: `[28080, 28081, 28082, 28083, 28090, 28025, 28673, 25432]` with semantic labels.
- `containerEnv`: routes database and broker traffic to internal Docker hostnames (`postgres-db:5432`, `message-broker:5672`, `idp-keycloak:8080`, `rate-limit-redis:6379`).

## Acceptance criteria
- [x] `.devcontainer/devcontainer.json` uses `dockerComposeFile` referencing `infra/local/docker-compose.yml` and `docker-compose.devcontainer.yml` without host socket mounting.
- [x] `.devcontainer/Dockerfile` builds an unprivileged workspace environment containing Java 25, Python 3.12, uv, Node.js 22, and postgresql-client.
- [x] Named persistent volumes for Gradle (`~/.gradle`) and uv cache (`~/.cache/uv`) are declared with `:cached` workspace mount.
- [x] Gradle VFS watching, parallel execution, and calibrated daemon memory limits are configured.
- [x] Devcontainer `forwardPorts` maps all application and dependency ports with semantic labels.
- [x] Dual IDE customization blocks (`customizations.vscode` and `customizations.jetbrains`) ensure full parity.
- [x] SSH agent forwarding and Git user synchronization are enabled.

## Validation commands
```bash
make compose-config
git diff --check
```

## Evidence
- `.devcontainer/Dockerfile` created with Ubuntu 24.04, Java 25 (Adoptium / Microsoft), Node.js 22, Astral uv, and `postgresql-client`.
- `.devcontainer/docker-compose.devcontainer.yml` connects to `squarewise-local-net` with named volumes for `gradle-cache` and `uv-cache`.
- `.devcontainer/devcontainer.json` maps collision-free `28xxx` ports with semantic labels and wires `vscode` and `jetbrains` IDE customizations.
- `make compose-config` validates the configuration cleanly.
