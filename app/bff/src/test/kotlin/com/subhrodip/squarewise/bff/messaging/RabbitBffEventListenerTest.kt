package com.subhrodip.squarewise.bff.messaging

import com.subhrodip.squarewise.bff.transport.ExpenseCoreGateway
import com.subhrodip.squarewise.bff.transport.AccountsGateway
import com.subhrodip.squarewise.bff.messaging.model.BffEventEnvelope
import com.subhrodip.squarewise.bff.messaging.model.ConsumptionResult
import com.subhrodip.squarewise.bff.messaging.model.DuplicateConsumptionResult
import com.subhrodip.squarewise.bff.messaging.model.ProcessedConsumptionResult
import com.subhrodip.squarewise.bff.messaging.service.BffEventConsumer
import com.subhrodip.squarewise.bff.messaging.transport.RabbitBffEventListener
import com.subhrodip.squarewise.bff.messaging.transport.DeadLetterPublisher
import com.subhrodip.squarewise.bff.messaging.persistence.BffEventDeduplicator
import com.subhrodip.squarewise.bff.realtime.GroupInvalidation
import com.subhrodip.squarewise.bff.realtime.LiveUpdate
import com.subhrodip.squarewise.bff.realtime.LiveUpdateFanout

import com.rabbitmq.client.Channel
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.doThrow
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.springframework.amqp.core.Message
import org.springframework.amqp.core.MessageProperties
import tools.jackson.databind.ObjectMapper
import java.time.Instant
import java.io.IOException
import java.util.UUID

class RabbitBffEventListenerTest {

    private val fanout = LiveUpdateFanout()
    private val deduplicator = BffEventDeduplicator()
    private val eventConsumer = BffEventConsumer(fanout, deduplicator)
    private val objectMapper = ObjectMapper()
    private val listener = RabbitBffEventListener(eventConsumer, objectMapper)
    private val channel: Channel = mock(Channel::class.java)

    @Test
    fun `acknowledges valid envelope message and triggers fanout`() {
        val eventId = UUID.randomUUID()
        val aggregateId = UUID.randomUUID()
        val groupId = UUID.randomUUID()
        val subscriber = fanout.subscribe("user-1", groupId.toString())

        val json = """
            {
              "eventId": "$eventId",
              "eventType": "group.updated",
              "schemaVersion": 1,
              "aggregateId": "$aggregateId",
              "groupId": "$groupId",
              "groupRevision": 10,
              "occurredAt": "${Instant.now()}",
              "payload": {"name": "Summer Vacation"}
            }
        """.trimIndent()

        val properties = MessageProperties().apply { deliveryTag = 42L }
        val message = Message(json.toByteArray(Charsets.UTF_8), properties)

        listener.onMessage(message, channel)

        assertEquals(1, fanout.pendingCount(subscriber.id))
        val update = fanout.poll(subscriber.id)
        assertEquals(groupId.toString(), update?.groupId)
        assertEquals(10L, update?.revision)

        verify(channel).basicAck(42L, false)
        verify(channel, never()).basicReject(42L, false)
    }

    @Test
    fun `acknowledges malformed poison-pill after terminal record publication`() {
        val malformedJson = "{ not-valid-json }"
        val properties = MessageProperties().apply { deliveryTag = 99L }
        val message = Message(malformedJson.toByteArray(Charsets.UTF_8), properties)

        listener.onMessage(message, channel)

        verify(channel).basicAck(99L, false)
        verify(channel, never()).basicReject(99L, false)
    }

    @Test
    fun `consumes valid envelope when broker channel is unavailable`() {
        val eventId = UUID.randomUUID()
        val aggregateId = UUID.randomUUID()
        val groupId = UUID.randomUUID()
        val subscriber = fanout.subscribe("user-2", groupId.toString())
        val json = """
            {
              "eventId": "$eventId",
              "eventType": "group.updated",
              "schemaVersion": 1,
              "aggregateId": "$aggregateId",
              "groupId": "$groupId",
              "groupRevision": 11,
              "occurredAt": "${Instant.now()}",
              "payload": {"name": "Winter Vacation"}
            }
        """.trimIndent()
        val message = Message(json.toByteArray(Charsets.UTF_8), MessageProperties())

        listener.onMessage(message, null)

        assertEquals(1, fanout.pendingCount(subscriber.id))
        assertEquals(11L, fanout.poll(subscriber.id)?.revision)
    }

    @Test
    fun `does not throw when rejecting malformed message without broker channel`() {
        val message = Message("{ not-valid-json }".toByteArray(Charsets.UTF_8), MessageProperties())

        listener.onMessage(message, null)
    }

    @Test
    fun `rejects a valid message when acknowledging fails`() {
        val groupId = UUID.randomUUID()
        val json = """
            {
              "eventId": "${UUID.randomUUID()}",
              "eventType": "group.updated",
              "schemaVersion": 1,
              "aggregateId": "${UUID.randomUUID()}",
              "groupId": "$groupId",
              "groupRevision": 12,
              "occurredAt": "${Instant.now()}",
              "payload": {"name": "Ack failure"}
            }
        """.trimIndent()
        val message = Message(json.toByteArray(Charsets.UTF_8), MessageProperties().apply { deliveryTag = 7L })
        doThrow(IOException("ack failed"))
            .`when`(channel)
            .basicAck(7L, false)

        listener.onMessage(message, channel)

        verify(channel).basicReject(7L, true)
    }

    @Test
    fun `requeues when terminal record publication fails`() {
        val message = Message("{ not-valid-json }".toByteArray(Charsets.UTF_8), MessageProperties().apply { deliveryTag = 8L })
        doThrow(IOException("reject failed"))
            .`when`(channel)
            .basicReject(8L, true)

        val failingPublisher = DeadLetterPublisher { throw IOException("publish failed") }
        val failingListener = RabbitBffEventListener(eventConsumer, objectMapper, deadLetterPublisher = failingPublisher)

        failingListener.onMessage(message, channel)

        verify(channel).basicReject(8L, true)
    }
}
