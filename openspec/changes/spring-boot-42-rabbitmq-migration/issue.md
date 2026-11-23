# OpenSpec Change Issue

## Objective

Restore Squarewise's existing RabbitMQ AMQP 0.9.1 integration while retaining the Spring Boot 4.2.0 dependency baseline.

- Follow-up tracker: [issue #68](https://github.com/ohbus/squarewise/issues/68).
- Implementation: [replacement PR #69](https://github.com/ohbus/squarewise/pull/69).

## Source and Evidence

- Source of inspiration: [PR #67](https://github.com/ohbus/squarewise/pull/67).
- Hosted failures: [JVM coverage run](https://github.com/ohbus/squarewise/actions/runs/45138829190), [PR CI run](https://github.com/ohbus/squarewise/actions/runs/45138829221), and [CodeQL run](https://github.com/ohbus/squarewise/actions/runs/45138829249).
- All failed application jobs reported missing RabbitMQ AMQP 0.9 symbols such as `RabbitTemplate`, `RabbitListener`, `ConnectionFactory`, and listener-container APIs.
- Spring Boot 4.2 release notes state that `spring-boot-starter-amqp` now provides generic AMQP 1.0 and that AMQP 0.9 applications must use `spring-boot-starter-rabbitmq`: [Boot 4.2 migration notes](https://github.com/spring-projects/spring-boot/wiki/Spring-Boot-4.2-Release-Notes).

## Requirements

### Requirement: RabbitMQ AMQP 0.9 compatibility

The Accounts, BFF, Expense Core, and Notifications applications SHALL compile and run with the existing RabbitMQ AMQP 0.9.1 publisher, listener, acknowledgement, and correlation APIs.

#### Scenario: Application compilation

- **WHEN** all four applications are compiled with Spring Boot 4.2.0
- **THEN** the existing RabbitMQ messaging source compiles without unresolved symbols

#### Scenario: Existing broker configuration

- **WHEN** the local stack starts with the current `spring.rabbitmq.*` properties
- **THEN** services connect to the existing RabbitMQ broker without AMQP 1.0-specific configuration

### Requirement: Messaging contracts remain stable

The change MUST preserve queue topology, routing, event payloads, acknowledgement behavior, retry behavior, and correlation behavior.

#### Scenario: Existing event lifecycle

- **WHEN** the live acceptance suite publishes and consumes an event
- **THEN** the existing expense, settlement, notification, and replay flows remain successful

## Design

- Rename the version-catalog alias from `boot-amqp` to `boot-rabbitmq`.
- Use `org.springframework.boot:spring-boot-starter-rabbitmq` in all four applications.
- Regenerate only the four application lockfiles.
- Do not add direct `spring-rabbit`, generic AMQP 1.0, or RabbitMQ AMQP 1.0 dependencies.
- Do not change REST, GraphQL, database, event, or deployment contracts.

## Dependency audit

- Spring Boot 4.2.0: AMQP starter migration is the required fix; Boot 4.0-deprecated APIs were searched and no affected usage was found.
- Spring AMQP 4.2.0: retains `spring-rabbit` for AMQP 0.9 and adds separate AMQP 1.0 integrations; the existing application remains on AMQP 0.9.
- Flyway 13.13.0: deprecates `REDGATE_LICENSING_PERMIT`; repository configuration contains no use requiring migration. [Release notes](https://documentation.red-gate.com/flyway/release-notes-and-older-versions/release-notes-for-flyway-engine)
- Gradle 9.9.2: patch release with deprecation-reporting and SFTP host-key changes; `make doctor` and `--warning-mode=all` validation pass. [Release notes](https://docs.gradle.org/9.9.2/release-notes.html)
- CycloneDX 3.6.1: SBOM task-input fix; SBOM generation and validation pass. [Plugin](https://plugins.gradle.org/plugin/org.cyclonedx.bom/3.6.1)
- SonarScanner Gradle 7.6: maintenance, reliability, and Gradle compatibility updates; no source migration required. [Release notes](https://github.com/SonarSource/sonar-scanner-gradle/releases/tag/7.6.0.18749-rev2)
- Kotlin 2.4.21: existing bug-fix baseline, not a direct PR version change; no Kotlin source change required. [Release](https://github.com/JetBrains/kotlin/releases/tag/v2.4.21)

## Local verification evidence

- `./gradlew testClasses --warning-mode=all`: passed.
- `./gradlew test check jacocoTestReport bootJar`: passed; 78 actionable tasks.
- All four dependency graphs resolve `spring-boot-starter-rabbitmq:4.2.0` and `spring-rabbit:4.2.0`.
- `make contracts`: passed.
- `make python-typecheck`: passed for 76 files.
- `make workflow-validate`: passed.
- `make compose-config`: passed.
- `make sbom-validate`: passed.
- `make security-hygiene`: passed for 1500 tracked files.
- `make acceptance-live`: passed all QA-04 and QA-05 scenarios after local Keycloak persona provisioning.
- `openspec validate spring-boot-42-rabbitmq-migration --strict`: passed.

The repository's `tests/e2e/acquire_local_tokens.sh` helper cannot run under the host's Bash 3.2 because it uses `readarray`; the equivalent local persona provisioning path was used for live acceptance, and the application stack itself passed.

## Acceptance criteria

- [ ] All four applications compile and package successfully.
- [ ] Existing RabbitMQ publisher and consumer tests pass.
- [ ] Dependency locks contain the classic RabbitMQ AMQP 0.9 graph and no generic AMQP 1.0 starter.
- [ ] All listed repository gates pass.
- [ ] OpenSpec artifacts remain valid and complete.
- [ ] The reviewed implementation is merged from replacement PR #69; no unreviewed branch changes are required.

## Rollback

Revert the catalog alias, four application declarations, four lockfiles, and this OpenSpec change. No database or data migration is required.
