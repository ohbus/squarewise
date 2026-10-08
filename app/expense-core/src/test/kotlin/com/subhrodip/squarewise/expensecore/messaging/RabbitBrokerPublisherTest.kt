package com.subhrodip.squarewise.expensecore.messaging

import com.subhrodip.squarewise.expensecore.messaging.broker.BrokerMessage
import com.subhrodip.squarewise.expensecore.messaging.broker.PublishResult
import com.subhrodip.squarewise.expensecore.messaging.broker.RabbitBrokerPublisher
import com.subhrodip.squarewise.expensecore.messaging.config.OutboxMessagingConfiguration
import com.subhrodip.squarewise.ids.events.EventConstants
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.doThrow
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.springframework.amqp.rabbit.core.RabbitOperations
import org.springframework.amqp.AmqpException
import org.springframework.amqp.core.Message
import org.springframework.amqp.rabbit.core.RabbitTemplate
import tools.jackson.databind.ObjectMapper
import java.time.Instant
import java.util.UUID

class RabbitBrokerPublisherTest {

    private val rabbitTemplate: RabbitTemplate = mock(RabbitTemplate::class.java)
    private val objectMapper = ObjectMapper()
    private val publisher = RabbitBrokerPublisher(rabbitTemplate, objectMapper, EventConstants.EVENTS_EXCHANGE)

    init {
        `when`(
            rabbitTemplate.invoke<Any?>(
                @Suppress("UNCHECKED_CAST")
                (any(RabbitOperations.OperationsCallback::class.java)
                    ?: RabbitOperations.OperationsCallback<Any?> { null }) as RabbitOperations.OperationsCallback<Any?>
            )
        ).thenAnswer { invocation ->
            @Suppress("UNCHECKED_CAST")
            (invocation.getArgument<Any>(0) as RabbitOperations.OperationsCallback<Any?>)
                .doInRabbit(rabbitTemplate)
        }
    }

    /** Verifies the production bean factory exposes the Rabbit publisher adapter unchanged. */
    @Test
    fun `configuration creates rabbit broker publisher`() {
        val publisher = OutboxMessagingConfiguration().rabbitBrokerPublisher(rabbitTemplate, objectMapper)

        assertTrue(publisher is RabbitBrokerPublisher)
    }

    /** Verifies broker confirmation cannot be configured with a non-positive timeout. */
    @Test
    fun `rejects a non-positive confirmation timeout`() {
        assertThrows(IllegalArgumentException::class.java) {
            RabbitBrokerPublisher(rabbitTemplate, objectMapper, confirmTimeoutMs = 0)
        }
    }

    @Test
    fun `publishes message conforming to envelope schema to central topic exchange`() {
        val eventId = UUID.randomUUID()
        val aggregateId = UUID.randomUUID()
        val groupId = UUID.randomUUID()
        val occurredAt = Instant.parse("2026-09-19T10:00:00Z")
        val payloadJson = """{"name":"Euro Trip","currency":"EUR"}""".toByteArray(Charsets.UTF_8)

        val brokerMessage = BrokerMessage(
            eventId = eventId,
            eventType = "group.created",
            payload = payloadJson,
            occurredAt = occurredAt,
            headers = mapOf(
                "aggregate-id" to aggregateId.toString(),
                "group-id" to groupId.toString(),
                "group-revision" to "3"
            )
        )

        val result = publisher.publish(brokerMessage)
        assertEquals(PublishResult.Confirmed, result)

        val exchangeCaptor = ArgumentCaptor.forClass(String::class.java)
        val routingKeyCaptor = ArgumentCaptor.forClass(String::class.java)
        val messageCaptor = ArgumentCaptor.forClass(Message::class.java)

        verify(rabbitTemplate).send(exchangeCaptor.capture(), routingKeyCaptor.capture(), messageCaptor.capture())

        assertEquals(EventConstants.EVENTS_EXCHANGE, exchangeCaptor.value)
        assertEquals("group.created", routingKeyCaptor.value)

        val sentMessage = messageCaptor.value
        val bodyJson = String(sentMessage.body, Charsets.UTF_8)
        val envelope = objectMapper.readTree(bodyJson)

        assertEquals(eventId.toString(), envelope.get("eventId").asString())
        assertEquals("group.created", envelope.get("eventType").asString())
        assertEquals(1, envelope.get("schemaVersion").asInt())
        assertEquals(aggregateId.toString(), envelope.get("aggregateId").asString())
        assertEquals(groupId.toString(), envelope.get("groupId").asString())
        assertEquals(3L, envelope.get("groupRevision").asLong())
        assertEquals(occurredAt.toString(), envelope.get("occurredAt").asString())
        assertEquals("Euro Trip", envelope.get("payload").get("name").asString())
        assertEquals("EUR", envelope.get("payload").get("currency").asString())
    }

    @Test
    fun `returns Rejected when RabbitTemplate throws AmqpException`() {
        val brokerMessage = BrokerMessage(
            eventId = UUID.randomUUID(),
            eventType = "group.renamed",
            payload = "{}".toByteArray(),
            occurredAt = Instant.now()
        )

        val failingRabbitTemplate: RabbitTemplate = mock(RabbitTemplate::class.java)
        doThrow(AmqpException("Connection refused"))
            .`when`(failingRabbitTemplate)
            .invoke<Any?>(any())

        val failingPublisher = RabbitBrokerPublisher(failingRabbitTemplate, objectMapper)
        val result = failingPublisher.publish(brokerMessage)

        assertTrue(result is PublishResult.Rejected)
        val rejection = result as PublishResult.Rejected
        assertTrue(rejection.reason.contains("Connection refused"))
    }

    /** Verifies malformed event payloads are rejected without invoking the broker. */
    @Test
    fun `returns Rejected when event payload cannot be deserialized`() {
        val brokerMessage = BrokerMessage(
            eventId = UUID.randomUUID(),
            eventType = "group.invalid",
            payload = "not-json".toByteArray(),
            occurredAt = Instant.now()
        )

        val result = publisher.publish(brokerMessage)

        assertTrue(result is PublishResult.Rejected)
        val rejection = result as PublishResult.Rejected
        assertTrue(rejection.reason.startsWith("Publish error:"))
    }
}
