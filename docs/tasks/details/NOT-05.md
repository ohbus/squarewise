# NOT-05: RabbitMQ transport adapter

Map validated broker envelopes into the transactional notification consumer and
apply explicit acknowledgement, retry, and reject behavior without moving
business logic into the listener.

## Decisions and implementation notes

- Added `NotificationConsumer` functional interface and wired `NotificationEventConsumer` to implement it, enabling clear decoupling between Spring AMQP transport and domain transaction processing.
- Added `BrokerEnvelopeParser` implementing contract-level validation against `contracts/events/envelope.schema.json` (validating `eventId`, `eventType`, `schemaVersion >= 1`, `aggregateId`, `groupId`, `groupRevision >= 1`, `occurredAt` ISO-8601, and object `payload`).
- Implemented `RabbitNotificationListener` extending `ChannelAwareMessageListener` with `@RabbitListener(queues = ["\${squarewise.notifications.queue:squarewise.notifications}"], ackMode = "MANUAL")`:
  - **Success / Duplicate**: `channel.basicAck(deliveryTag, false)` after consumer succeeds.
  - **Poison Pill / Schema Violation**: `channel.basicReject(deliveryTag, false)` (no requeue) to avoid poison pill head-of-line blocking.
  - **Transient / Transaction Failure**: `channel.basicReject(deliveryTag, true)` (requeue for retry) so the broker can redeliver.
- Added `application.yml` for Notifications configuring manual acknowledge mode, Hibernate validation, and disabling container auto-startup in tests (`spring.rabbitmq.listener.simple.auto-startup: false`).
- Added comprehensive unit and component tests in `RabbitNotificationListenerTest` covering valid delivery, duplicates, schema violations, poison pills, transient errors, and graceful null channel handling.
