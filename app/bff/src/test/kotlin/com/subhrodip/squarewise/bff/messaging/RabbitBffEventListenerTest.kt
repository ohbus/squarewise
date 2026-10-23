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
import com.subhrodip.squarewise.bff.realtime.LiveUpdateFanout
import com.subhrodip.squarewise.errors.async.DeadLetterRecord

import com.rabbitmq.client.Channel
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.doThrow
import org.mockito.Mockito.doAnswer
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.ArgumentMatchers.any
import org.springframework.amqp.core.Message
import org.springframework.amqp.core.MessageProperties
import tools.jackson.databind.ObjectMapper
import java.time.Instant
import java.io.IOException
import java.util.UUID
import java.util.concurrent.CancellationException

class RabbitBffEventListenerTest {

    private val fanout = LiveUpdateFanout()
    private val deduplicator = BffEventDeduplicator()
    private val eventConsumer = BffEventConsumer(fanout, deduplicator)
    private val objectMapper = ObjectMapper()
    private val listener = RabbitBffEventListener(eventConsumer, objectMapper, deadLetterPublisher = DeadLetterPublisher { })
    private val channel: Channel = mock(Channel::class.java)

    @Test
    fun `acknowledges valid envelope message and triggers fanout`() {
        val eventId = UUID.randomUUID()
        val aggregateId = UUID.randomUUID()
        val groupId = UUID.randomUUID()

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
    fun `rejects without requeue when terminal record publication fails`() {
        val message = Message("{ not-valid-json }".toByteArray(Charsets.UTF_8), MessageProperties().apply { deliveryTag = 8L })

        val failingPublisher = DeadLetterPublisher { throw IOException("publish failed") }
        val failingListener = RabbitBffEventListener(eventConsumer, objectMapper, deadLetterPublisher = failingPublisher)

        failingListener.onMessage(message, channel)

        verify(channel).basicReject(8L, false)
    }

    @Test
    fun `uses bounded attempt metadata for dead-letter records`() {
        val publisher = RecordingDeadLetterPublisher()
        val boundedListener = RabbitBffEventListener(eventConsumer, objectMapper, deadLetterPublisher = publisher)
        val properties = MessageProperties().apply {
            deliveryTag = 16L
            setHeader("x-attempt", 99)
        }

        boundedListener.onMessage(Message("{ not-valid-json }".toByteArray(), properties), channel)

        assertEquals(4, publisher.records.single().attemptCount)
    }

    @Test
    fun `uses redelivery metadata and preserves empty payload handling`() {
        val publisher = RecordingDeadLetterPublisher()
        val redeliveredListener = RabbitBffEventListener(eventConsumer, objectMapper, deadLetterPublisher = publisher)
        val properties = MessageProperties().apply {
            deliveryTag = 19L
            isRedelivered = true
        }

        redeliveredListener.onMessage(Message(ByteArray(0), properties), channel)

        assertEquals(3, publisher.records.single().attemptCount)
        verify(channel).basicAck(19L, false)
    }

    @Test
    fun `requeues non-fatal consumer failures`() {
        val failingConsumer = mock(BffEventConsumer::class.java)
        doThrow(IllegalStateException("fanout unavailable")).`when`(failingConsumer)
            .consume(any(BffEventEnvelope::class.java) ?: sampleEnvelope())
        val retryListener = RabbitBffEventListener(failingConsumer, objectMapper, deadLetterPublisher = DeadLetterPublisher { })
        val groupId = UUID.randomUUID()
        val json = """
            {"eventId":"${UUID.randomUUID()}","eventType":"group.updated","schemaVersion":1,
             "aggregateId":"${UUID.randomUUID()}","groupId":"$groupId","groupRevision":1,
             "occurredAt":"${Instant.now()}","payload":{}}
        """.trimIndent()
        val message = Message(json.toByteArray(), MessageProperties().apply { deliveryTag = 17L })

        retryListener.onMessage(message, channel)

        verify(channel).basicReject(17L, true)
    }

    @Test
    fun `does not require a channel when requeueing a non-fatal failure`() {
        val failingConsumer = mock(BffEventConsumer::class.java)
        doThrow(IllegalStateException("fanout unavailable")).`when`(failingConsumer)
            .consume(any(BffEventEnvelope::class.java) ?: sampleEnvelope())
        val retryListener = RabbitBffEventListener(failingConsumer, objectMapper, deadLetterPublisher = DeadLetterPublisher { })
        val json = """
            {"eventId":"${UUID.randomUUID()}","eventType":"group.updated","schemaVersion":1,
             "aggregateId":"${UUID.randomUUID()}","groupId":"${UUID.randomUUID()}","groupRevision":1,
             "occurredAt":"${Instant.now()}","payload":{}}
        """.trimIndent()

        retryListener.onMessage(Message(json.toByteArray(), MessageProperties()), null)
    }

    @Test
    fun `propagates cancellation without broker disposition`() {
        val cancellingConsumer = mock(BffEventConsumer::class.java)
        doThrow(CancellationException("cancelled")).`when`(cancellingConsumer)
            .consume(any(BffEventEnvelope::class.java) ?: sampleEnvelope())
        val cancellingListener = RabbitBffEventListener(cancellingConsumer, objectMapper, deadLetterPublisher = DeadLetterPublisher { })
        val json = """
            {"eventId":"${UUID.randomUUID()}","eventType":"group.updated","schemaVersion":1,
             "aggregateId":"${UUID.randomUUID()}","groupId":"${UUID.randomUUID()}","groupRevision":1,
             "occurredAt":"${Instant.now()}","payload":{}}
        """.trimIndent()

        assertThrows(CancellationException::class.java) {
            cancellingListener.onMessage(Message(json.toByteArray(), MessageProperties()), channel)
        }
    }

    @Test
    fun `propagates fatal checked failures without broker disposition`() {
        val failingConsumer = mock(BffEventConsumer::class.java)
        doAnswer { throw InterruptedException("interrupted") }.`when`(failingConsumer)
            .consume(any(BffEventEnvelope::class.java) ?: sampleEnvelope())
        val failingListener = RabbitBffEventListener(failingConsumer, objectMapper, deadLetterPublisher = DeadLetterPublisher { })
        val json = """
            {"eventId":"${UUID.randomUUID()}","eventType":"group.updated","schemaVersion":1,
             "aggregateId":"${UUID.randomUUID()}","groupId":"${UUID.randomUUID()}","groupRevision":1,
             "occurredAt":"${Instant.now()}","payload":{}}
        """.trimIndent()

        assertThrows(InterruptedException::class.java) {
            failingListener.onMessage(Message(json.toByteArray(), MessageProperties()), channel)
        }
    }

    @Test
    fun `swallows broker rejection failure while handling dead-letter publication failure`() {
        val failingChannel = mock(Channel::class.java)
        doThrow(IOException("reject failed")).`when`(failingChannel).basicReject(18L, false)
        val failingPublisher = DeadLetterPublisher { throw IOException("publish failed") }
        val failingListener = RabbitBffEventListener(eventConsumer, objectMapper, deadLetterPublisher = failingPublisher)

        failingListener.onMessage(
            Message("{ not-valid-json }".toByteArray(), MessageProperties().apply { deliveryTag = 18L }),
            failingChannel
        )
    }

    private class RecordingDeadLetterPublisher : DeadLetterPublisher {
        val records = mutableListOf<DeadLetterRecord>()

        override fun publish(record: DeadLetterRecord) {
            records += record
        }
    }

    private fun sampleEnvelope() = BffEventEnvelope(
        eventId = UUID.randomUUID(),
        eventType = "group.updated",
        schemaVersion = 1,
        aggregateId = UUID.randomUUID(),
        groupId = UUID.randomUUID(),
        groupRevision = 1,
        occurredAt = Instant.now()
    )
}
