# CORE-12: Outbox background polling relay daemon

Schedule periodic execution of `OutboxPublisher.publishAvailable()` to pump leased
transactional outbox events to RabbitMQ in the background.

## Acceptance criteria

- A scheduled worker component invokes `OutboxPublisher.publishAvailable()` at a configurable fixed delay.
- The poller respects lease timeouts and batch sizes without blocking application startup.
- The worker can be enabled/disabled via configuration property for deterministic test isolation.

## Decisions and implementation notes

- Added `OutboxRelayProperties` binding to `squarewise.outbox.*` (`enabled`, `batchSize`, `pollDelayMs`, `leaseSeconds`, `maxAttempts`, `retryAfterSeconds`).
- Implemented `OutboxMessagingConfiguration` providing `BrokerPublisher` (defaulting to `InMemoryBroker` when no external broker is configured) and configuring `OutboxPublisher` beans with `@EnableScheduling`.
- Implemented `OutboxRelayDaemon` annotated with `@Scheduled(fixedDelayString = "\${squarewise.outbox.poll-delay-ms:1000}")` and conditionally enabled via `@ConditionalOnProperty(prefix = "squarewise.outbox", name = ["enabled"], havingValue = "true")`.
- By default `squarewise.outbox.enabled` is false in `application.yml` ensuring deterministic isolation in test runs and preventing uncoordinated worker loops.
- Added comprehensive unit and context tests in `OutboxRelayDaemonTest` verifying conditional creation and batch execution.
