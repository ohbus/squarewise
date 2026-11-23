# Spec Delta

## Purpose

Preserve Squarewise's existing RabbitMQ messaging behavior while the application dependency baseline moves to Spring Boot 4.2. The capability covers reliable AMQP 0.9 connectivity, listener delivery, acknowledgements, and publishing across all messaging services.

## ADDED Requirements

### Requirement: RabbitMQ AMQP 0.9 compatibility

The services that publish or consume Squarewise events SHALL use a dependency set that provides the RabbitMQ AMQP 0.9.1 protocol and the existing messaging integration APIs.

#### Scenario: Application compilation retains messaging APIs

- **WHEN** the Accounts, BFF, Expense Core, and Notifications applications are compiled
- **THEN** all existing RabbitMQ publisher, listener, acknowledgement, connection, and correlation-data code compiles successfully

#### Scenario: Existing RabbitMQ broker configuration remains valid

- **WHEN** a service starts with the existing `spring.rabbitmq.*` configuration
- **THEN** it connects using the existing RabbitMQ protocol and does not require AMQP 1.0-specific properties

### Requirement: Event delivery contract is preserved

The messaging integration SHALL preserve existing queue, exchange, routing, acknowledgement, retry, and event-payload behavior; this dependency migration MUST NOT change externally visible event contracts.

#### Scenario: Existing consumer acknowledges a valid event

- **WHEN** a valid event is delivered to an existing consumer
- **THEN** the consumer processes it through the existing handler and applies the existing acknowledgement behavior

#### Scenario: Existing publisher sends an event

- **WHEN** an existing publisher emits an event
- **THEN** the event is published using the existing routing and correlation behavior without an AMQP 1.0 conversion
