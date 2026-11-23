# Design

## Context

PR #67 upgrades the dependency baseline to Spring Boot 4.2.0 and Gradle 9.9.2. The current source uses `org.springframework.amqp.rabbit.*` AMQP 0.9 APIs across all four applications, but the generic Boot AMQP starter now resolves AMQP 1.0 artifacts and no longer supplies the required `spring-rabbit` integration.

The dependency graph also includes Spring Framework 7.1, Spring Security 7.2, Spring GraphQL 2.1, Reactor 2026.0, Micrometer 1.18, Flyway 13.13, CycloneDX 3.6.1, and SonarScanner 7.6. The repository search found no current use of the Boot 4.2-deprecated RestTemplate, forwarded-header, Redis master/replica, Tomcat, Janino, AppOptics, RabbitProperties `determine...`, or deprecated Micrometer convention APIs.

## Goals / Non-Goals

**Goals:**

- Restore compilation and runtime dependency support for RabbitMQ AMQP 0.9.1.
- Keep Boot 4.2 and the other PR dependency upgrades.
- Make the protocol choice explicit in the version catalog and lockfiles.
- Verify dependency, deprecation, SBOM, test, packaging, and local acceptance behavior.

**Non-Goals:**

- Migrating to generic AMQP 1.0 or RabbitMQ AMQP 1.0.
- Changing message payloads, queue topology, routing keys, retry policy, or acknowledgement semantics.
- Opportunistically migrating unrelated deprecated APIs that are not used by this repository.
- Changing REST, GraphQL, database, or deployment contracts.

## Decisions

### Use the dedicated RabbitMQ AMQP 0.9 starter

The catalog will expose `boot-rabbitmq` for `spring-boot-starter-rabbitmq`, and each messaging application will use that alias. This is preferred over adding a direct `spring-rabbit` dependency because the starter is Boot's supported boundary and supplies the matching auto-configuration and dependency set.

The generic `spring-boot-starter-amqp` and RabbitMQ AMQP 1.0 starter were rejected because they would require a protocol/client migration and do not match the existing source or broker configuration.

### Keep the dependency upgrade baseline

Boot 4.2, Flyway 13.13, Gradle 9.9.2, CycloneDX 3.6.1, SonarScanner 7.6, and the existing Kotlin 2.4.21 baseline remain in place. Their release notes show no additional source change required by this repository, but their relevant validation tasks remain mandatory.

### Regenerate only affected locks

The four application lockfiles will be regenerated after the catalog/build-script change. Shared-library and benchmark lockfiles are not part of this dependency edge and will remain unchanged unless Gradle proves they require updates.

## Risks / Trade-offs

- [Boot 4.2 transitive API changes] → Compile all applications, run tests with `--warning-mode=all`, and exercise local acceptance flows.
- [RabbitMQ runtime incompatibility despite successful compilation] → Run RabbitMQ-backed tests and `make acceptance-live` with local dependencies.
- [Flyway environment deprecation] → Search CI, Compose, and operations configuration for `REDGATE_LICENSING_PERMIT` and record the result.
- [SBOM or lockfile drift] → Run `cyclonedxBom`, `make sbom-validate`, and `git diff --check`.

## Migration Plan

1. Update the catalog alias and four application dependency declarations.
2. Regenerate the four application lockfiles.
3. Run OpenSpec validation and all declared Gradle/repository checks.
4. Commit the implementation and OpenSpec artifacts on the new local branch.
5. Create the GitHub follow-up issue with `gh`, then close PR #67 with a link to that issue.
6. Do not push the branch or any commit.

Rollback is a local revert of the dependency alias, application declarations, lockfiles, and OpenSpec change. No schema or data migration is involved.
