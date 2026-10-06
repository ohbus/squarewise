# MSG-02: Deliver committed group changes to every BFF replica

Publish committed group-change events from Expense Core and consume them through
per-replica BFF queues into live-update fanout with deduplication and resync-safe
semantics. Depends on CORE-22, MSG-01, and BFF-02. Validate two-replica delivery,
reconnect recovery, and broker failure behavior.

## Implementation Details

1. **Central Event Constants & Envelope Contract Alignment:**
   - Introduced [EventConstants.kt](file:///Users/smohanta/scm/splitwise/libs/ids/src/main/kotlin/com/subhrodip/squarewise/ids/EventConstants.kt) defining `squarewise.events` topic exchange, header keys, schema version (`1`), and wildcard routing patterns.
   - Verified compliance with `contracts/events/envelope.schema.json` (`eventId`, `eventType`, `schemaVersion`, `aggregateId`, `groupId`, `groupRevision`, `occurredAt`, `payload`).

2. **Expense Core Broker Publisher:**
   - Implemented [RabbitBrokerPublisher.kt](file:///Users/smohanta/scm/splitwise/app/expense-core/src/main/kotlin/com/subhrodip/squarewise/expensecore/messaging/RabbitBrokerPublisher.kt) implementing `BrokerPublisher` using `RabbitTemplate` and Jackson `ObjectMapper` with JSON content type, message IDs, and timestamp properties.
   - Updated [OutboxMessagingConfiguration.kt](file:///Users/smohanta/scm/splitwise/app/expense-core/src/main/kotlin/com/subhrodip/squarewise/expensecore/messaging/OutboxRelayDaemon.kt) to wire `RabbitBrokerPublisher` when `squarewise.outbox.rabbit-enabled=true`, with in-memory fallback.

3. **BFF Per-Replica Event Consumer & Fanout:**
   - Implemented [BffEventEnvelope.kt](file:///Users/smohanta/scm/splitwise/app/bff/src/main/kotlin/com/subhrodip/squarewise/bff/messaging/BffEventEnvelope.kt) representing deserialized envelope events.
   - Implemented [BffEventDeduplicator.kt](file:///Users/smohanta/scm/splitwise/app/bff/src/main/kotlin/com/subhrodip/squarewise/bff/messaging/BffEventDeduplicator.kt) providing thread-safe bounded LRU caching to deduplicate incoming messages by `eventId`.
   - Implemented [BffEventConsumer.kt](file:///Users/smohanta/scm/splitwise/app/bff/src/main/kotlin/com/subhrodip/squarewise/bff/messaging/BffEventConsumer.kt) which receives envelopes, checks deduplication, and fans out invalidations to `LiveUpdateFanout.emitInvalidation` and `LiveUpdateFanout.publish(LiveUpdate)`.
   - Implemented [RabbitBffEventListener.kt](file:///Users/smohanta/scm/splitwise/app/bff/src/main/kotlin/com/subhrodip/squarewise/bff/messaging/RabbitBffEventListener.kt) with manual channel acknowledgement (`basicAck`) and poison-pill rejection without requeue (`basicReject`).
   - Implemented [BffMessagingConfiguration.kt](file:///Users/smohanta/scm/splitwise/app/bff/src/main/kotlin/com/subhrodip/squarewise/bff/messaging/BffMessagingConfiguration.kt) configuring `TopicExchange("squarewise.events")`, per-replica `AnonymousQueue()` (exclusive, auto-delete), topic bindings, and listener container.

4. **Verification Evidence:**
   - `TwoReplicaBffFanoutTest`: Verified that two distinct BFF replica instances receiving identical broker events both trigger local live update subscriptions and reactive invalidation flux streams, with redundant deliveries cleanly deduplicated.
   - `BffEventConsumerTest`: Verified invalidation emission, subscriber queue polling, event ID deduplication, and LRU eviction.
   - `RabbitBffEventListenerTest`: Verified successful envelope processing and manual ACK, as well as malformed poison-pill rejection without requeue.
   - `RabbitBrokerPublisherTest`: Verified envelope serialization conforming to `envelope.schema.json` and AMQP exception rejection handling.
   - Build & validation commands executed cleanly:
     - `./gradlew :app:expense-core:test :app:bff:test --no-daemon` (exit status 0)
     - `python3 tools/contracts/validate.py` (exit status 0)
     - `git diff --check` (exit status 0)
