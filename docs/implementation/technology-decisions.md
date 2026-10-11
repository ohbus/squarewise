# Technology decisions

## Baseline

Use Kotlin on the JVM, Spring Boot, Gradle Kotlin DSL, Spring MVC for stateful
REST services, and WebFlux/Spring GraphQL for the database-free BFF. Version
selection is verified through dependency resolution and centralized in
`gradle/libs.versions.toml`. The current baseline is Java 25, Kotlin 2.4.20,
Spring Boot 4.1.1, and Gradle 9.7.1. The earlier Java 26 proposal was superseded;
future baseline changes require a separately verified technology decision.

## Persistence

Use Spring Data JPA with Hibernate for ordinary aggregate persistence. This
reduces mapping code while retaining transactions, optimistic `@Version`
checks, and fetch planning. Use native SQL only for group-revision locking,
outbox/job claiming and measured specialized queries. Flyway owns migrations;
Hibernate validates. Disable Open Session in View, keep entities private, and
return DTOs. The BFF has no database. Read/write separation is CQRS-oriented,
not event-sourced: commands and consistency-critical reads use the writer;
explicit query ports may use named read pools only under a documented
consistency policy. `libs/db` owns routing, pool budgets, route guards, health,
and database telemetry; it does not own business entities, repositories, or
migrations. See `docs/implementation/cqrs-data-access-plan.md`.

PostgreSQL is authoritative for money, audit, balances, synchronization and
outbox records. Each service has separate credentials and tables. Databases may
share a cluster initially, but cross-service SQL, foreign keys and entities are
forbidden. Store monetary values as integer minor units internally and strings
on JSON/GraphQL boundaries.

Same-service foreign keys remain mandatory for durable relationships such as
Expense Core groups, expenses, postings, and settlement records. Application
services perform authorization and domain validation in the writer transaction,
but those checks supplement rather than replace database referential integrity.
Before changing a constraint for performance, capture normalized query timing,
lock waits, child-index usage, and controlled `EXPLAIN (ANALYZE, BUFFERS, WAL)`
evidence. The existing Expense Core foreign-key columns are indexed for the
dependent-row checks. `libs/db` may provide routing and telemetry for this work,
but it must not contain business relationship checks or service entities.

## Messaging and API

REST over HTTPS is the synchronous service boundary. RabbitMQ via Spring AMQP
delivers transactional-outbox events to notifications and BFF invalidation
fan-out. Use publisher confirms, durable queues, manual acknowledgements,
deduplicating inbox records, bounded retry and parking queues. A socket is only
an invalidation hint; REST snapshot/change-feed recovery remains authoritative.

GraphQL over HTTPS is the current BFF client API. GraphQL subscriptions over
authenticated WebSockets carry group IDs and revisions, never authoritative
balances. Every BFF replica has its own temporary fan-out queue.

## Security and operations

Production deployments currently use Keycloak as the selected OIDC provider,
with a provider-neutral configuration and Spring Security resource-server
validation in every service. Keycloak is also the optional local OIDC provider
for realistic integration testing. It is an infrastructure choice rather than
a domain dependency: no application code may depend on Keycloak-specific APIs
or claims, so Auth0, Okta, Entra ID, or another OIDC provider can be selected in
future through configuration and an adapter boundary. The existing passthrough
bearer-token principal remains a narrowly scoped local-demo mechanism only; it
accepts no proof of identity and therefore does not constitute production or
OIDC evidence. See
`docs/security/authentication-hardening.md` and task `AUTH-01` for the staged
hardening plan.
Services enforce membership authorization themselves. Use Actuator,
Micrometer and OpenTelemetry-compatible tracing; structured logs exclude money
payloads, invitation secrets and tokens. Docker Compose supports local
PostgreSQL, RabbitMQ, Redis and SMTP capture. Redis is mandatory for ephemeral
distributed rate limiting in local, staging and production; it is not a source of
truth for identity, sessions, audit or financial state. Local single-database
reader mode is diagnostic only and is not replica evidence. Kubernetes, Kafka, JPA
second-level caching, multi-region writes and sharding are deferred until measured
requirements justify them.
Local Compose owns a disposable password-protected Redis instance; staging and
production Compose consume an externally managed Redis endpoint whose host,
credential, port, and TLS mode are required deployment inputs. No application
profile may select PostgreSQL, process-local memory, or an implicit Redis fallback
for rate-limit state.

## Python tooling and environment management

Repository Python scripts, contract verifiers, and operational tools use
`pyproject.toml` with Hatchling as the build backend, exposing `tests` and `tools`
as editable packages. Dependencies and tool versions (including mypy and yamllint)
are pinned in a committed `uv.lock`. `uv` is the authoritative environment and
package manager across local developer workstations and CI workflows (`uv sync --frozen --no-build`
and `uv run --frozen --no-build`). No ephemeral `uvx` executions or manual `PYTHONPATH` exports are
permitted.

## Devcontainer architecture and toolchain baseline

The repository provides a containerized development environment adopting the
official OCI Devcontainer specification and a native Devcontainer Compose
topology (`dockerComposeFile`), avoiding root-equivalent Docker-outside-of-Docker
(DooD) host socket mounting (`/var/run/docker.sock`). The workspace container
operates as an unprivileged service on the shared internal Docker network
`squarewise-local-net`, communicating with PostgreSQL, RabbitMQ, Redis, Mailpit,
and Keycloak via TCP and HTTP.

The Devcontainer workspace image is pinned to Ubuntu 24.04
(`mcr.microsoft.com/devcontainers/base:ubuntu-24.04`), Microsoft OpenJDK 25
(matching CI reusable workflows and the Gradle toolchain baseline), Python 3.12+
managed exclusively by `uv`, and Node.js 24 LTS for executing `@usebruno/cli@4.1.0`
via `npx` during local verification. Direct database interaction uses
`postgresql-client` over standard TCP wire protocol. All central version
definitions are maintained in `infra/versions.env.example` without ad-hoc duplication.

## Deterministic local port allocation and networking model

Local development avoids standard default ports (`5432`, `8080`, `6379`, `5672`, `1025`, `8025`)
and unpredictable ephemeral port bindings in favor of a deterministic, collision-free `28xxx` port
family (`28080`–`28091`, `25432`, `28672`–`28673`, `28379`, `21025`, `28025`). This gives all developers
and automated test harnesses a shared, invariant vocabulary across workstations without risk of
collisions against personal PostgreSQL, Redis, or Keycloak instances running on host machines.

Inside the Docker bridge network (`squarewise-local-net`), containers bind to standard native ports
(`5432`, `5672`, `6379`, `1025`, `8080`) and communicate using descriptive domain hostnames
(`postgres-db`, `message-broker`, `rate-limit-redis`, `mailpit-email`, `idp-keycloak`, `accounts-api`,
`expense-core-api`, `notifications-api`, `squarewise-bff`).

Direct host networking (`network_mode: host`) was evaluated and rejected because it fails on macOS
and Windows (WSL2), where containers bind to the VM's network namespace rather than the host loopback,
breaking IDE and browser access. A user-defined bridge network combined with deterministic port
forwarding and `extra_hosts: ["host.docker.internal:host-gateway"]` provides 100% cross-platform parity
across Linux, macOS, and Windows.

## Unified version centralization and Docker anti-drift design patterns

To eliminate dependency drift and duplicate configuration between Devcontainers, local Docker Compose,
CI workflows, and developer workstations, the repository applies strict enterprise software design patterns:

1. **Single Authoritative Source of Truth**: All non-secret container image tags (`postgres:17`, `rabbitmq:4.3-management`,
   `redis:8.2-alpine`, `mailpit:v1.27`, `keycloak:26.7.4`), base images (`gradle:9.7.1-jdk25`, `eclipse-temurin:25-jre`,
   `node:24.18.0-bookworm-slim`, `python:3.12-slim`), and toolchain runtimes (`PYTHON_VERSION=3.12`, `UV_VERSION=0.6.5`,
   `DEVCONTAINER_JDK_VERSION=25`, `DEVCONTAINER_NODE_VERSION=24`, `MERMAID_CLI_VERSION=11.12.0`) are declared once in
   `infra/versions.env.example`. Local `.env.example` mirrors these exact values, while JVM library dependencies
   remain exclusively in `gradle/libs.versions.toml`.
2. **Parameterized Multi-Stage Dockerfile Pattern**: No Dockerfile in the repository contains un-parameterized,
   hardcoded base image tags. Every build file (`infra/docker/Dockerfile.jvm`, `Dockerfile.fast`, `Dockerfile.dev`,
   `infra/docs/Dockerfile`, `tests/fixtures/invalid_subject_oidc/Dockerfile`, and `.devcontainer/Dockerfile`) defines
   `ARG <IMAGE_VAR>=<default>` before `FROM ${<IMAGE_VAR>}`. This decouples image definitions from concrete versions
   and allows build environments, Compose, and CI pipelines to override or pin versions without modifying Dockerfiles.
3. **Compose Service Reuse Pattern**: Rather than copying service blocks across compose files, Devcontainer topologies
   use Docker Compose v2 `include: - path: ../infra/local/docker-compose.yml`, while local development overlays use
   `extends: {file: docker-compose.yml, service: ...}`. This guarantees that infrastructure services share identical
   healthchecks, environment configurations, and network topologies across all execution modes.
4. **Automated Zero-Drift SBOM Enforcement**: `tools/ops/validate_sbom_baseline.py` (executed via `make sbom-validate`)
   statically verifies:
   - All required version keys exist in `infra/versions.env.example`.
   - `infra/local/.env.example` container image tags match `infra/versions.env.example` exactly with zero drift.
   - `pyproject.toml` Python requirements align with `PYTHON_VERSION`.
   - All Dockerfiles adhere to the parameterized `ARG` base image pattern.
   - All JVM dependencies in `build.gradle.kts` reference `gradle/libs.versions.toml` without hardcoded version strings.

