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
import com.subhrodip.squarewise.bff.realtime.LiveUpdate
import com.subhrodip.squarewise.bff.realtime.LiveUpdateFanout

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID
import org.mockito.Mockito.anyLong
import org.mockito.Mockito.anyString
import org.mockito.Mockito.doThrow
import org.mockito.Mockito.mock

class BffEventConsumerTest {

    private lateinit var fanout: LiveUpdateFanout
    private lateinit var deduplicator: BffEventDeduplicator
    private lateinit var consumer: BffEventConsumer

    @BeforeEach
    fun setUp() {
        fanout = LiveUpdateFanout()
        deduplicator = BffEventDeduplicator(100)
        consumer = BffEventConsumer(fanout, deduplicator)
    }

    @Test
    fun `consumes event and emits invalidation and updates subscribed queues`() {
        val groupId = UUID.randomUUID()
        val eventId = UUID.randomUUID()
        val sub1 = fanout.subscribe("user-1", groupId.toString())

        val envelope = BffEventEnvelope(
            eventId = eventId,
            eventType = "expense.created",
            schemaVersion = 1,
            aggregateId = UUID.randomUUID(),
            groupId = groupId,
            groupRevision = 5L,
            occurredAt = Instant.now()
        )

        val result = consumer.consume(envelope)
        assertTrue(result is ProcessedConsumptionResult)

        val processed = result as ProcessedConsumptionResult
        assertEquals(groupId.toString(), processed.invalidation.groupId)
        assertEquals(5L, processed.invalidation.revision)
        assertEquals(eventId.toString(), processed.invalidation.changeId)
        assertEquals(1, processed.deliveredQueues)

        val polled = fanout.poll(sub1.id)
        assertEquals(groupId.toString(), polled?.groupId)
        assertEquals(5L, polled?.revision)
    }

    @Test
    fun `deduplicates re-delivered events by eventId without redundant fanout`() {
        val groupId = UUID.randomUUID()
        val eventId = UUID.randomUUID()
        val sub = fanout.subscribe("user-1", groupId.toString())

        val envelope = BffEventEnvelope(
            eventId = eventId,
            eventType = "expense.created",
            schemaVersion = 1,
            aggregateId = UUID.randomUUID(),
            groupId = groupId,
            groupRevision = 2L,
            occurredAt = Instant.now()
        )

        val firstResult = consumer.consume(envelope)
        assertTrue(firstResult is ProcessedConsumptionResult)
        assertEquals(1, fanout.pendingCount(sub.id))

        // Second delivery with identical eventId
        val secondResult = consumer.consume(envelope)
        assertTrue(secondResult is DuplicateConsumptionResult)
        val duplicate = secondResult as DuplicateConsumptionResult
        assertEquals(eventId, duplicate.eventId)

        // Queue must still have only 1 pending update, not 2
        assertEquals(1, fanout.pendingCount(sub.id))
    }

    @Test
    fun `uses the default deduplicator when no adapter is supplied`() {
        val defaultConsumer = BffEventConsumer(fanout)
        val envelope = BffEventEnvelope(
            eventId = UUID.randomUUID(),
            eventType = "expense.created",
            schemaVersion = 1,
            aggregateId = UUID.randomUUID(),
            groupId = UUID.randomUUID(),
            groupRevision = 1L,
            occurredAt = Instant.now()
        )

        assertTrue(defaultConsumer.consume(envelope) is ProcessedConsumptionResult)
    }

    @Test
    fun `releases a claimed event when fanout processing fails`() {
        val failingFanout = mock(LiveUpdateFanout::class.java)
        doThrow(IllegalStateException("fanout unavailable")).`when`(failingFanout)
            .emitInvalidation(anyString(), anyLong(), anyString())
        val failingConsumer = BffEventConsumer(failingFanout, BffEventDeduplicator())
        val envelope = BffEventEnvelope(
            eventId = UUID.randomUUID(),
            eventType = "expense.created",
            schemaVersion = 1,
            aggregateId = UUID.randomUUID(),
            groupId = UUID.randomUUID(),
            groupRevision = 1L,
            occurredAt = Instant.now()
        )

        assertThrows(IllegalStateException::class.java) { failingConsumer.consume(envelope) }
    }

    @Test
    fun `deduplicator evicts oldest entry when capacity is reached`() {
        val smallDeduplicator = BffEventDeduplicator(2)
        val id1 = UUID.randomUUID()
        val id2 = UUID.randomUUID()
        val id3 = UUID.randomUUID()

        assertEquals(false, smallDeduplicator.isDuplicateAndMark(id1))
        assertEquals(false, smallDeduplicator.isDuplicateAndMark(id2))
        assertEquals(true, smallDeduplicator.isDuplicateAndMark(id1)) // access id1, making id2 eldest
        assertEquals(false, smallDeduplicator.isDuplicateAndMark(id3)) // should evict id2

        assertEquals(false, smallDeduplicator.isDuplicateAndMark(id2)) // id2 was evicted, so recognized as new
    }

    @Test
    fun `member removal revokes only removed subject subscription before update`() {
        val groupId = UUID.randomUUID()
        val removed = fanout.subscribe("removed-user", groupId.toString())
        val retained = fanout.subscribe("retained-user", groupId.toString())
        val envelope = BffEventEnvelope(
            eventId = UUID.randomUUID(),
            eventType = "member.removed.v1",
            schemaVersion = 1,
            aggregateId = UUID.randomUUID(),
            groupId = groupId,
            groupRevision = 4L,
            occurredAt = Instant.now(),
            payload = mapOf("targetSubject" to "removed-user")
        )

        val result = consumer.consume(envelope)

        assertTrue(result is ProcessedConsumptionResult)
        assertEquals(1, (result as ProcessedConsumptionResult).deliveredQueues)
        assertEquals(null, fanout.poll(removed.id))
        assertEquals(groupId.toString(), fanout.poll(retained.id)?.groupId)
    }

    /** Verifies malformed member-removal subjects do not revoke a subscription. */
    @Test
    fun `member removal without a usable target subject only publishes the update`() {
        listOf(emptyMap<String, Any?>(), mapOf("targetSubject" to ""), mapOf("targetSubject" to "   "))
            .forEach { payload ->
                val groupId = UUID.randomUUID()
                val subscriber = fanout.subscribe("retained-user", groupId.toString())
                val envelope = BffEventEnvelope(
                    eventId = UUID.randomUUID(),
                    eventType = "member.removed.v1",
                    schemaVersion = 1,
                    aggregateId = UUID.randomUUID(),
                    groupId = groupId,
                    groupRevision = 4L,
                    occurredAt = Instant.now(),
                    payload = payload
                )

                val result = consumer.consume(envelope)

                assertTrue(result is ProcessedConsumptionResult)
                assertEquals(groupId.toString(), fanout.poll(subscriber.id)?.groupId)
            }
    }
}
