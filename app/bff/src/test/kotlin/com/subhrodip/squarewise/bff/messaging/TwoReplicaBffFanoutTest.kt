package com.subhrodip.squarewise.bff.messaging

import com.subhrodip.squarewise.bff.transport.ExpenseCoreGateway
import com.subhrodip.squarewise.bff.transport.AccountsGateway
import com.subhrodip.squarewise.bff.messaging.model.BffEventEnvelope
import com.subhrodip.squarewise.bff.messaging.model.ConsumptionResult
import com.subhrodip.squarewise.bff.messaging.model.DuplicateConsumptionResult
import com.subhrodip.squarewise.bff.messaging.model.ProcessedConsumptionResult
import com.subhrodip.squarewise.bff.messaging.service.BffEventConsumer
import com.subhrodip.squarewise.bff.messaging.persistence.BffEventDeduplicator
import com.subhrodip.squarewise.bff.realtime.GroupInvalidation
import com.subhrodip.squarewise.bff.realtime.LiveUpdateFanout

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import tools.jackson.databind.ObjectMapper
import java.time.Instant
import java.util.UUID

/**
 * Validates the core requirement of MSG-02 / BFF-02:
 * "Two replicas notify their own clients; broker reconnect triggers resync; slow consumers are bounded; money never depends on socket delivery."
 *
 * Simulates two separate BFF replicas (Replica A and Replica B), each possessing its own
 * [LiveUpdateFanout], [BffEventDeduplicator], and [BffEventConsumer].
 *
 * Verifies that when a single committed group change event is delivered to each replica's
 * independent fanout queue, both replicas trigger their own local subscribers and reactive streams.
 */
class TwoReplicaBffFanoutTest {

    private val objectMapper = ObjectMapper()

    @Test
    fun `two BFF replicas receive same broker event and notify their own local subscribers`() {
        val groupId = UUID.fromString("00000000-0000-0000-0000-000000000100")
        val groupIdStr = groupId.toString()

        // Replica A setup
        val fanoutA = LiveUpdateFanout()
        val consumerA = BffEventConsumer(fanoutA, BffEventDeduplicator())
        val subscriptionA = fanoutA.subscribe("user-alice", groupIdStr)
        val perSubscriptionA = mutableListOf<GroupInvalidation>()
        val perSubscriptionDisposableA = fanoutA.invalidations(subscriptionA.id).subscribe { perSubscriptionA.add(it) }
        val invalidationsA = mutableListOf<GroupInvalidation>()
        val disposableA = fanoutA.invalidations().subscribe { invalidationsA.add(it) }

        // Replica B setup
        val fanoutB = LiveUpdateFanout()
        val consumerB = BffEventConsumer(fanoutB, BffEventDeduplicator())
        val subscriptionB = fanoutB.subscribe("user-bob", groupIdStr)
        val perSubscriptionB = mutableListOf<GroupInvalidation>()
        val perSubscriptionDisposableB = fanoutB.invalidations(subscriptionB.id).subscribe { perSubscriptionB.add(it) }
        val invalidationsB = mutableListOf<GroupInvalidation>()
        val disposableB = fanoutB.invalidations().subscribe { invalidationsB.add(it) }

        try {
            val eventId = UUID.randomUUID()
            val aggregateId = UUID.randomUUID()

            // Simulated wire payload delivered by RabbitMQ fanout to Replica A queue
            val envelopeA = BffEventEnvelope(
                eventId = eventId,
                eventType = "expense.created",
                schemaVersion = 1,
                aggregateId = aggregateId,
                groupId = groupId,
                groupRevision = 7L,
                occurredAt = Instant.now(),
                payload = mapOf("description" to "Dinner", "amount" to 5000)
            )

            // Simulated wire payload delivered by RabbitMQ fanout to Replica B queue
            val envelopeB = BffEventEnvelope(
                eventId = eventId,
                eventType = "expense.created",
                schemaVersion = 1,
                aggregateId = aggregateId,
                groupId = groupId,
                groupRevision = 7L,
                occurredAt = Instant.now(),
                payload = mapOf("description" to "Dinner", "amount" to 5000)
            )

            // Both replicas consume the event independently
            val resultA = consumerA.consume(envelopeA)
            val resultB = consumerB.consume(envelopeB)

            // Assert Replica A local delivery
            assertEquals(1, invalidationsA.size)
            assertEquals(7L, invalidationsA[0].revision)

            // Assert Replica B local delivery
            assertEquals(1, invalidationsB.size)
            assertEquals(7L, invalidationsB[0].revision)
            assertEquals(1, perSubscriptionA.size)
            assertEquals(7L, perSubscriptionA[0].revision)
            assertEquals(1, perSubscriptionB.size)
            assertEquals(7L, perSubscriptionB[0].revision)

            // Assert duplicate delivery to Replica A is ignored without redundant fanout
            val duplicateResultA = consumerA.consume(envelopeA)
            assertEquals(eventId, (duplicateResultA as DuplicateConsumptionResult).eventId)
            assertEquals(1, invalidationsA.size) // No new invalidation emitted
            assertEquals(1, perSubscriptionA.size)
        } finally {
            disposableA.dispose()
            disposableB.dispose()
            perSubscriptionDisposableA.dispose()
            perSubscriptionDisposableB.dispose()
        }
    }
}
