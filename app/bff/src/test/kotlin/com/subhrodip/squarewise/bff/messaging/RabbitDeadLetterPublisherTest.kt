package com.subhrodip.squarewise.bff.messaging

import com.subhrodip.squarewise.bff.messaging.config.BffMessagingProperties
import com.subhrodip.squarewise.bff.messaging.transport.RabbitDeadLetterPublisher
import com.subhrodip.squarewise.errors.async.DeadLetterRecord
import java.time.Instant
import java.util.UUID
import org.junit.jupiter.api.Assertions.assertThrows
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mockito.doAnswer
import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import org.springframework.amqp.AmqpException
import org.springframework.amqp.core.Message
import org.springframework.amqp.core.ReturnedMessage
import org.springframework.amqp.rabbit.connection.CorrelationData
import org.springframework.amqp.rabbit.core.RabbitOperations
import org.springframework.amqp.rabbit.core.RabbitTemplate
import tools.jackson.databind.ObjectMapper

/** Verifies terminal BFF failures are serialized and confirmed through RabbitMQ. */
class RabbitDeadLetterPublisherTest {
    @Test
    fun `publishes a structured dead letter with broker confirmation`() {
        val rabbitTemplate = mock(RabbitTemplate::class.java)
        `when`(
            rabbitTemplate.invoke<Any?>(any())
        ).thenAnswer { invocation ->
            invocation.getArgument<RabbitOperations.OperationsCallback<Any?>>(0)
                .doInRabbit(rabbitTemplate)
        }
        val properties = BffMessagingProperties(deadLetterExchange = "squarewise.dlx")
        val publisher = RabbitDeadLetterPublisher(rabbitTemplate, properties, ObjectMapper())

        assertDoesNotThrow { publisher.publish(record()) }
    }

    @Test
    fun `fails when the broker returns the dead letter`() {
        val rabbitTemplate = mock(RabbitTemplate::class.java)
        `when`(
            rabbitTemplate.invoke<Any?>(any())
        ).thenAnswer { invocation ->
            invocation.getArgument<RabbitOperations.OperationsCallback<Any?>>(0)
                .doInRabbit(rabbitTemplate)
        }
        doAnswer { invocation ->
            invocation.getArgument<CorrelationData>(3).setReturned(
                ReturnedMessage(Message(ByteArray(0)), 312, "NO_ROUTE", "squarewise.dlx", "bff.dead-letter")
            )
            null
        }.`when`(rabbitTemplate).convertAndSend(anyString(), anyString(), any<Any>(), any(CorrelationData::class.java))
        val publisher = RabbitDeadLetterPublisher(
            rabbitTemplate,
            BffMessagingProperties(deadLetterExchange = "squarewise.dlx"),
            ObjectMapper()
        )

        assertThrows(AmqpException::class.java) { publisher.publish(record()) }
    }

    private fun record() = DeadLetterRecord(
        originalQueue = "squarewise.bff.events",
        eventId = UUID.randomUUID(),
        eventType = "group.updated",
        schemaVersion = 1,
        failedAt = Instant.parse("2026-10-10T00:00:00Z"),
        attemptCount = 4,
        numericCode = "426701",
        errorName = "LIVE_UPDATE_EVENT_INVALID",
        diagnosticReason = "Invalid event",
        messagePayloadBase64 = "e30=",
    )
}
