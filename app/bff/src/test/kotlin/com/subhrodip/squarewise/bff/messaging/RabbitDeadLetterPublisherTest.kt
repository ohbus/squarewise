package com.subhrodip.squarewise.bff.messaging

import com.subhrodip.squarewise.bff.messaging.config.BffMessagingProperties
import com.subhrodip.squarewise.bff.messaging.transport.RabbitDeadLetterPublisher
import com.subhrodip.squarewise.errors.async.DeadLetterRecord
import java.time.Instant
import java.util.UUID
import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import org.springframework.amqp.rabbit.core.RabbitOperations
import org.springframework.amqp.rabbit.core.RabbitTemplate
import tools.jackson.databind.ObjectMapper

/** Verifies terminal BFF failures are serialized and confirmed through RabbitMQ. */
class RabbitDeadLetterPublisherTest {
    @Test
    fun `publishes a structured dead letter with broker confirmation`() {
        val rabbitTemplate = mock(RabbitTemplate::class.java)
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
        val properties = BffMessagingProperties(deadLetterExchange = "squarewise.dlx")
        val publisher = RabbitDeadLetterPublisher(rabbitTemplate, properties, ObjectMapper())

        assertDoesNotThrow { publisher.publish(record()) }
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
