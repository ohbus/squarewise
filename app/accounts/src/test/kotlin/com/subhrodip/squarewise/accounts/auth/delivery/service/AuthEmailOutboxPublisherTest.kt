package com.subhrodip.squarewise.accounts.auth.delivery.service

import com.subhrodip.squarewise.accounts.auth.delivery.model.AuthEmailPublishOutcome
import com.subhrodip.squarewise.accounts.auth.delivery.outbox.AuthEmailOutboxEntity
import java.time.Duration
import java.time.Instant
import java.util.UUID
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.springframework.amqp.AmqpException
import org.springframework.amqp.core.Message
import org.springframework.amqp.core.MessageProperties
import org.springframework.amqp.rabbit.core.RabbitOperations
import org.springframework.amqp.rabbit.core.RabbitTemplate
import tools.jackson.databind.ObjectMapper

/** Verifies protected auth-email outbox publication and failure state transitions. */
class AuthEmailOutboxPublisherTest {

    private val now = Instant.parse("2026-09-29T20:00:00Z")
    private val lease = Duration.ofSeconds(30)
    private val retryAfter = Duration.ofSeconds(10)
    private val eventId = UUID.fromString("00000000-0000-0000-0000-000000000123")

    /** An empty claim does not touch RabbitMQ or advance any outbox state. */
    @Test
    fun `empty claim returns empty without publishing`() {
        val outbox = mock(AuthEmailOutboxService::class.java)
        val rabbit = RecordingRabbitTemplate()
        val mapper = ObjectMapper()
        `when`(outbox.claim(now, lease)).thenReturn(null)

        val outcome = publisher(outbox, rabbit, mapper).publishOne()

        assertEquals(AuthEmailPublishOutcome.EMPTY, outcome)
        assertEquals(null, rabbit.sentMessage)
        verify(outbox, never()).acknowledge(eventId, now)
        verify(outbox, never()).reject(eventId, now, retryAfter, 3)
    }

    /** A successful publish sends the complete envelope and acknowledges once. */
    @Test
    fun `successful publication sends protected envelope and acknowledges`() {
        val outbox = mock(AuthEmailOutboxService::class.java)
        val rabbit = RecordingRabbitTemplate()
        val mapper = ObjectMapper()
        val record = record()
        `when`(outbox.claim(now, lease)).thenReturn(record)

        val outcome = publisher(outbox, rabbit, mapper).publishOne()

        assertEquals(AuthEmailPublishOutcome.PUBLISHED, outcome)
        val message = requireNotNull(rabbit.sentMessage)
        assertEquals("auth.email.requested.v1", message.bodyJsonField("eventType"))
        assertTrue(String(message.body).contains("\"schemaVersion\":1"))
        assertEquals("v1-protected", message.bodyJsonField("encryptedCredential"))
        assertFalse(String(message.body).contains("raw-credential"))
        assertEquals("auth.exchange", rabbit.sentExchange)
        assertEquals("auth.email", rabbit.sentRoutingKey)
        assertEquals(MessageProperties.CONTENT_TYPE_JSON, message.messageProperties.contentType)
        assertEquals(eventId.toString(), message.messageProperties.messageId)
        verify(outbox, times(1)).acknowledge(eventId, now)
        verify(outbox, never()).reject(eventId, now, retryAfter, 3)
    }

    /** Broker failure schedules bounded retry and never acknowledges the claimed event. */
    @Test
    fun `broker failure schedules retry`() {
        val outbox = mock(AuthEmailOutboxService::class.java)
        val rabbit = RecordingRabbitTemplate(failWithAmqpException = true)
        val mapper = ObjectMapper()
        `when`(outbox.claim(now, lease)).thenReturn(record())

        val outcome = publisher(outbox, rabbit, mapper).publishOne()

        assertEquals(AuthEmailPublishOutcome.RETRY_SCHEDULED, outcome)
        verify(outbox, times(1)).reject(eventId, now, retryAfter, 3)
        verify(outbox, never()).acknowledge(eventId, now)
    }

    /** A negative publisher confirmation schedules retry instead of acknowledging delivery. */
    @Test
    fun `publisher confirmation failure schedules retry`() {
        val outbox = mock(AuthEmailOutboxService::class.java)
        val rabbit = RecordingRabbitTemplate(failWithConfirmException = true)
        `when`(outbox.claim(now, lease)).thenReturn(record())

        val outcome = publisher(outbox, rabbit, ObjectMapper()).publishOne()

        assertEquals(AuthEmailPublishOutcome.RETRY_SCHEDULED, outcome)
        verify(outbox).reject(eventId, now, retryAfter, 3)
        verify(outbox, never()).acknowledge(eventId, now)
    }

    /** Serialization failure follows the same retry path and does not send a partial message. */
    @Test
    fun `serialization failure schedules retry without sending`() {
        val outbox = mock(AuthEmailOutboxService::class.java)
        val rabbit = RecordingRabbitTemplate()
        val mapper = FailingObjectMapper()
        `when`(outbox.claim(now, lease)).thenReturn(record())

        val outcome = publisher(outbox, rabbit, mapper).publishOne()

        assertEquals(AuthEmailPublishOutcome.RETRY_SCHEDULED, outcome)
        assertEquals(null, rabbit.sentMessage)
        verify(outbox, times(1)).reject(eventId, now, retryAfter, 3)
        verify(outbox, never()).acknowledge(eventId, now)
    }

    private fun publisher(
        outbox: AuthEmailOutboxService,
        rabbit: RabbitTemplate,
        mapper: ObjectMapper
    ): AuthEmailOutboxPublisher = AuthEmailOutboxPublisher(
        outbox = outbox,
        rabbitTemplate = rabbit,
        objectMapper = mapper,
        exchange = "auth.exchange",
        routingKey = "auth.email",
        lease = lease,
        retryAfter = retryAfter,
        maximumAttempts = 3,
        confirmTimeoutMs = 5_000,
        clock = { now }
    )

    private fun record(): AuthEmailOutboxEntity = AuthEmailOutboxEntity(
        outboxId = UUID.fromString("00000000-0000-0000-0000-000000000124"),
        eventId = eventId,
        recipient = "user@example.com",
        template = "LOGIN_CODE",
        encryptedCredential = "v1-protected",
        expiresAt = now.plusSeconds(600),
        createdAt = now,
        availableAt = now,
        attempts = 1,
        status = "CLAIMED"
    )

    private class RecordingRabbitTemplate(
        private val failWithAmqpException: Boolean = false,
        private val failWithConfirmException: Boolean = false
    ) : RabbitTemplate() {
        var sentMessage: Message? = null
        var sentExchange: String? = null
        var sentRoutingKey: String? = null

        override fun send(exchange: String, routingKey: String, message: Message) {
            if (failWithAmqpException) {
                throw AmqpException("broker unavailable")
            }
            sentMessage = message
            sentExchange = exchange
            sentRoutingKey = routingKey
        }

        override fun <T> invoke(action: RabbitOperations.OperationsCallback<T>): T =
            action.doInRabbit(this)

        override fun waitForConfirmsOrDie(timeout: Long) {
            if (failWithConfirmException) {
                throw AmqpException("publisher confirmation rejected")
            }
        }
    }

    private class FailingObjectMapper : ObjectMapper() {
        override fun writeValueAsBytes(value: Any): ByteArray =
            throw IllegalStateException("cannot serialize")
    }

    private fun Message.bodyJsonField(name: String): String {
        val body = String(body)
        val marker = "\"$name\":\""
        val start = body.indexOf(marker) + marker.length
        return body.substring(start, body.indexOf('"', start))
    }
}
