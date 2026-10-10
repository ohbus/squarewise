package com.subhrodip.squarewise.notifications.consumer

import com.subhrodip.squarewise.notifications.consumer.model.NotificationConsumptionOutcome
import com.subhrodip.squarewise.notifications.consumer.model.NotificationEvent
import com.subhrodip.squarewise.notifications.consumer.persistence.ProcessedNotificationEventRepository
import com.subhrodip.squarewise.notifications.consumer.service.NotificationConsumer
import com.subhrodip.squarewise.notifications.consumer.service.NotificationConsumerService
import com.subhrodip.squarewise.notifications.consumer.service.NotificationEventConsumer
import com.subhrodip.squarewise.notifications.consumer.service.TransactionalNotificationEventProcessor
import com.subhrodip.squarewise.notifications.consumer.transport.BrokerEnvelopeParser
import com.subhrodip.squarewise.notifications.consumer.transport.RabbitNotificationListener
import com.subhrodip.squarewise.notifications.preferences.persistence.PreferenceStore

import com.rabbitmq.client.Channel
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.springframework.amqp.core.Message
import org.springframework.amqp.core.MessageProperties
import tools.jackson.databind.ObjectMapper
import java.nio.charset.StandardCharsets
import java.util.UUID

class RabbitNotificationListenerTest {

    private val objectMapper = ObjectMapper()
    private val envelopeParser = BrokerEnvelopeParser(objectMapper)

    @Test
    fun `acknowledges valid delivery when consumer returns applied`() {
        val eventId = UUID.fromString("00000000-0000-7000-8000-000000000201")
        val aggregateId = UUID.fromString("00000000-0000-7000-8000-000000000202")
        val groupId = UUID.fromString("00000000-0000-7000-8000-000000000203")

        var receivedEvent: NotificationEvent? = null
        val consumer = NotificationConsumer { event ->
            receivedEvent = event
            NotificationConsumptionOutcome.APPLIED
        }

        val listener = RabbitNotificationListener(consumer, envelopeParser)
        val channel = TestChannel()
        val deliveryTag = 42L

        val envelopeJson = """
            {
              "eventId": "$eventId",
              "eventType": "expense.created",
              "schemaVersion": 1,
              "aggregateId": "$aggregateId",
              "groupId": "$groupId",
              "groupRevision": 3,
              "occurredAt": "2026-09-17T20:00:00Z",
              "payload": {
                "notificationId": "$aggregateId",
                "subject": "user-42",
                "message": "Expense created for dinner"
              }
            }
        """.trimIndent()

        val message = createMessage(envelopeJson, deliveryTag)
        listener.onMessage(message, channel)

        assertEquals(deliveryTag, channel.ackedTag)
        assertNull(channel.rejectedTag)

        assertNotNull(receivedEvent)
        assertEquals(eventId, receivedEvent?.eventId)
        assertEquals("user-42", receivedEvent?.subject)
        assertEquals("expense.created", receivedEvent?.eventType)
        assertEquals("Expense created for dinner", receivedEvent?.message)
    }

    @Test
    fun `acknowledges duplicate delivery when consumer returns duplicate`() {
        val eventId = UUID.fromString("00000000-0000-7000-8000-000000000204")
        val aggregateId = UUID.fromString("00000000-0000-7000-8000-000000000205")
        val groupId = UUID.fromString("00000000-0000-7000-8000-000000000206")

        val consumer = NotificationConsumer {
            NotificationConsumptionOutcome.DUPLICATE
        }

        val listener = RabbitNotificationListener(consumer, envelopeParser)
        val channel = TestChannel()
        val deliveryTag = 99L

        val envelopeJson = """
            {
              "eventId": "$eventId",
              "eventType": "settlement.recorded",
              "schemaVersion": 1,
              "aggregateId": "$aggregateId",
              "groupId": "$groupId",
              "groupRevision": 1,
              "occurredAt": "2026-09-17T20:05:00Z",
              "payload": {"subject":"alice@example.com"}
            }
        """.trimIndent()

        val message = createMessage(envelopeJson, deliveryTag)
        listener.onMessage(message, channel)

        assertEquals(deliveryTag, channel.ackedTag)
        assertNull(channel.rejectedTag)
    }

    @Test
    fun `rejects poison pill message without requeue on malformed json`() {
        val consumer = NotificationConsumer { NotificationConsumptionOutcome.APPLIED }
        val listener = RabbitNotificationListener(consumer, envelopeParser)
        val channel = TestChannel()
        val deliveryTag = 12L

        val message = createMessage("{ this is definitely not valid json }", deliveryTag)
        listener.onMessage(message, channel)

        assertNull(channel.ackedTag)
        assertEquals(deliveryTag, channel.rejectedTag)
        assertEquals(false, channel.rejectedRequeue)
    }

    @Test
    fun `rejects poison pill message without requeue on schema violation`() {
        val consumer = NotificationConsumer { NotificationConsumptionOutcome.APPLIED }
        val listener = RabbitNotificationListener(consumer, envelopeParser)
        val channel = TestChannel()
        val deliveryTag = 13L

        // Missing required aggregateId and invalid schemaVersion
        val invalidEnvelope = """
            {
              "eventId": "00000000-0000-7000-8000-000000000207",
              "eventType": "expense.created",
              "schemaVersion": 0,
              "groupId": "00000000-0000-7000-8000-000000000208",
              "groupRevision": 1,
              "occurredAt": "2026-09-17T20:00:00Z",
              "payload": {"subject":"alice@example.com"}
            }
        """.trimIndent()

        val message = createMessage(invalidEnvelope, deliveryTag)
        listener.onMessage(message, channel)

        assertNull(channel.ackedTag)
        assertEquals(deliveryTag, channel.rejectedTag)
        assertEquals(false, channel.rejectedRequeue)
    }

    @Test
    fun `rejects with requeue when consumer throws transient exception`() {
        val eventId = UUID.fromString("00000000-0000-7000-8000-000000000209")
        val aggregateId = UUID.fromString("00000000-0000-7000-8000-000000000210")
        val groupId = UUID.fromString("00000000-0000-7000-8000-000000000211")

        val consumer = NotificationConsumer {
            throw IllegalStateException("Transient database connectivity failure")
        }

        val listener = RabbitNotificationListener(consumer, envelopeParser)
        val channel = TestChannel()
        val deliveryTag = 77L

        val envelopeJson = """
            {
              "eventId": "$eventId",
              "eventType": "expense.created",
              "schemaVersion": 1,
              "aggregateId": "$aggregateId",
              "groupId": "$groupId",
              "groupRevision": 5,
              "occurredAt": "2026-09-17T20:10:00Z",
              "payload": {"subject":"alice@example.com"}
            }
        """.trimIndent()

        val message = createMessage(envelopeJson, deliveryTag)
        listener.onMessage(message, channel)

        assertNull(channel.ackedTag)
        assertEquals(deliveryTag, channel.rejectedTag)
        assertEquals(true, channel.rejectedRequeue)
    }

    @Test
    fun `rejects redelivered transient failure without requeue`() {
        val consumer = NotificationConsumer { throw IllegalStateException("database unavailable") }
        val listener = RabbitNotificationListener(consumer, envelopeParser)
        val channel = TestChannel()
        val message = createMessage(
            """{"eventId":"00000000-0000-7000-8000-000000000212","eventType":"expense.created","schemaVersion":1,"aggregateId":"00000000-0000-7000-8000-000000000213","groupId":"00000000-0000-7000-8000-000000000214","groupRevision":1,"occurredAt":"2026-09-17T20:00:00Z","payload":{"subject":"alice@example.com"}}""",
            78L,
            redelivered = true
        )

        listener.onMessage(message, channel)

        assertEquals(78L, channel.rejectedTag)
        assertEquals(false, channel.rejectedRequeue)
    }

    @Test
    fun `handles null channel gracefully without throwing`() {
        val consumer = NotificationConsumer { NotificationConsumptionOutcome.APPLIED }
        val listener = RabbitNotificationListener(consumer, envelopeParser)
        val message = createMessage("{}", 1L)

        // Should not throw NPE when channel is null
        listener.onMessage(message, null)
    }

    @Test
    fun `accepts a valid delivery when the broker channel is unavailable`() {
        val consumer = NotificationConsumer { NotificationConsumptionOutcome.APPLIED }
        val listener = RabbitNotificationListener(consumer, envelopeParser)
        val message = createMessage(
            """{"eventId":"00000000-0000-7000-8000-000000000215","eventType":"expense.created","schemaVersion":1,"aggregateId":"00000000-0000-7000-8000-000000000216","groupId":"00000000-0000-7000-8000-000000000217","groupRevision":1,"occurredAt":"2026-09-17T20:00:00Z","payload":{}}""",
            80L
        )

        listener.onMessage(message, null)
    }

    @Test
    fun `swallows transient failure when the broker channel is unavailable`() {
        val consumer = NotificationConsumer { throw IllegalStateException("database unavailable") }
        val listener = RabbitNotificationListener(consumer, envelopeParser)
        val message = createMessage(
            """{"eventId":"00000000-0000-0000-0000-000000000215","eventType":"expense.created","schemaVersion":1,"aggregateId":"00000000-0000-0000-0000-000000000216","groupId":"00000000-0000-0000-0000-000000000217","groupRevision":1,"occurredAt":"2026-09-17T20:00:00Z","payload":{}}""",
            81L
        )

        listener.onMessage(message, null)
    }

    private fun createMessage(content: String, deliveryTag: Long, redelivered: Boolean = false): Message {
        val props = MessageProperties().apply {
            this.deliveryTag = deliveryTag
            this.isRedelivered = redelivered
        }
        return Message(content.toByteArray(StandardCharsets.UTF_8), props)
    }

    private class TestChannel : Channel by mock(Channel::class.java) {
        var ackedTag: Long? = null
        var rejectedTag: Long? = null
        var rejectedRequeue: Boolean? = null

        override fun basicAck(deliveryTag: Long, multiple: Boolean) {
            ackedTag = deliveryTag
        }

        override fun basicReject(deliveryTag: Long, requeue: Boolean) {
            rejectedTag = deliveryTag
            rejectedRequeue = requeue
        }
    }
}
