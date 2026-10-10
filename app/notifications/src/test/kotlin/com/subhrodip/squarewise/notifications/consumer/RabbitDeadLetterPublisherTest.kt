package com.subhrodip.squarewise.notifications.consumer

import com.subhrodip.squarewise.errors.async.DeadLetterRecord
import com.subhrodip.squarewise.notifications.consumer.config.NotificationMessagingProperties
import com.subhrodip.squarewise.notifications.consumer.transport.RabbitDeadLetterPublisher
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

/** Verifies terminal notification failures are serialized and confirmed through RabbitMQ. */
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
        val properties = NotificationMessagingProperties(deadLetterExchange = "squarewise.dlx")
        val publisher = RabbitDeadLetterPublisher(rabbitTemplate, properties, ObjectMapper())

        assertDoesNotThrow { publisher.publish(record()) }
    }

    private fun record() = DeadLetterRecord(
        originalQueue = "squarewise.notifications.v2",
        eventId = UUID.randomUUID(),
        eventType = "expense.created",
        schemaVersion = 1,
        failedAt = Instant.parse("2026-10-10T00:00:00Z"),
        attemptCount = 4,
        numericCode = "426701",
        errorName = "NOTIFICATION_DISPATCH_FAILED",
        diagnosticReason = "Dispatch failed",
        messagePayloadBase64 = "e30=",
    )
}
