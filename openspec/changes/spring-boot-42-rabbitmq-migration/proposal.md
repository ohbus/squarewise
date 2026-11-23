# Proposal

## Why

Spring Boot 4.2 changes `spring-boot-starter-amqp` to generic AMQP 1.0 support, while Squarewise uses RabbitMQ's AMQP 0.9 APIs (`RabbitTemplate`, listener containers, acknowledgements, and correlation data). PR #67 therefore fails application compilation across Accounts, BFF, Expense Core, and Notifications.

The fix is needed now because the dependency upgrade is otherwise blocked in hosted CI and cannot be safely merged without restoring the existing messaging contract.

## What Changes

- Select Spring Boot's RabbitMQ AMQP 0.9 starter explicitly for every messaging application.
- Rename the version-catalog alias from `boot-amqp` to `boot-rabbitmq` so the protocol choice is explicit.
- Regenerate the four affected application dependency lockfiles.
- Preserve the existing RabbitMQ Java APIs, `spring.rabbitmq.*` configuration, queues, exchanges, acknowledgements, and event contracts.
- Validate Spring Boot 4.2 deprecations and transitive upgrades without adding unrelated migrations.

## Capabilities

### New Capabilities

- `rabbitmq-amqp-09`: Compile-time and runtime compatibility for the existing RabbitMQ AMQP 0.9 messaging integration.

### Modified Capabilities

- None. The change restores an existing externally visible messaging capability; it does not alter event shapes or delivery semantics.

## Impact

- Affected build files: `gradle/libs.versions.toml` and the four application build scripts.
- Affected dependency locks: Accounts, BFF, Expense Core, and Notifications.
- The dependency graph changes from generic AMQP 1.0 artifacts to `spring-rabbit` and the classic RabbitMQ client.
- No REST, GraphQL, database, event-schema, or deployment configuration contract changes are expected.
- Source: [PR #67](https://github.com/ohbus/squarewise/pull/67).
