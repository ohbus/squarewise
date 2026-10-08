package com.subhrodip.squarewise.notifications.email

import java.time.Instant
import com.subhrodip.squarewise.notifications.email.delivery.AuthEmailDeliveryConsumer
import com.subhrodip.squarewise.notifications.email.delivery.AuthEmailDeliveryEvent
import com.subhrodip.squarewise.notifications.email.delivery.AuthEmailRabbitListener
import com.rabbitmq.client.Channel
import java.nio.charset.StandardCharsets
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.doThrow
import org.mockito.Mockito.doReturn
import org.mockito.Mockito.any
import org.springframework.amqp.core.Message
import org.springframework.amqp.core.MessageProperties
import tools.jackson.databind.ObjectMapper
import com.subhrodip.squarewise.notifications.email.delivery.EmailDeliveryOutcome
import com.subhrodip.squarewise.errors.async.AsyncExecutionTemplate
import org.mockito.Mockito.`when`
import org.mockito.Mockito.verify
import org.mockito.Mockito.times

class AuthEmailRabbitListenerTest {
    private val consumer = mock(AuthEmailDeliveryConsumer::class.java)
    private val listener = AuthEmailRabbitListener(ObjectMapper(), consumer)

    @Test
    fun `rejects malformed auth email without requeue`() {
        val channel = TestChannel()
        listener.onMessage(message("{not-json}", 11L), channel)

        assertEquals(11L, channel.rejectedTag)
        assertEquals(false, channel.rejectedRequeue)
        assertNull(channel.ackedTag)
    }

    @Test
    fun `rejects an empty auth email body without requeue`() {
        val channel = TestChannel()

        listener.onMessage(message("", 10L), channel)

        assertEquals(10L, channel.rejectedTag)
        assertEquals(false, channel.rejectedRequeue)
        assertNull(channel.ackedTag)
    }

    @Test
    fun `acknowledges valid auth email after consumer succeeds`() {
        doReturn(EmailDeliveryOutcome.DELIVERED).`when`(consumer).consume(
            any(AuthEmailDeliveryEvent::class.java) ?: AuthEmailDeliveryEvent("event", "user@example.com", "LOGIN_CODE", "cipher", Instant.MAX),
            any(Instant::class.java) ?: Instant.EPOCH
        )
        val channel = TestChannel()

        listener.onMessage(message(validEvent(), 13L), channel)

        assertEquals(13L, channel.ackedTag)
        assertNull(channel.rejectedTag)
        verify(consumer, times(1)).consume(
            any(AuthEmailDeliveryEvent::class.java) ?: AuthEmailDeliveryEvent("event", "user@example.com", "LOGIN_CODE", "cipher", Instant.MAX),
            any(Instant::class.java) ?: Instant.EPOCH
        )
    }

    @Test
    fun `requeues first transient auth email failure`() {
        doThrow(IllegalStateException("temporary failure"))
            .`when`(consumer)
            .consume(
                any(AuthEmailDeliveryEvent::class.java) ?: AuthEmailDeliveryEvent("evt", "user@example.com", "LOGIN_CODE", "cipher", Instant.MAX),
                any(Instant::class.java) ?: Instant.EPOCH
            )
        val channel = TestChannel()

        listener.onMessage(message(validEvent(), 14L, redelivered = false), channel)

        assertEquals(14L, channel.rejectedTag)
        assertEquals(true, channel.rejectedRequeue)
    }

    @Test
    fun `rejects unsupported event type without requeue`() {
        val unsupported = validEvent().replace("auth.email.requested.v1", "expense.created.v1")
        val channel = TestChannel()

        listener.onMessage(message(unsupported, 15L), channel)

        assertEquals(15L, channel.rejectedTag)
        assertEquals(false, channel.rejectedRequeue)
        assertNull(channel.ackedTag)
    }

    @Test
    fun `rejects consumer validation failure without requeue`() {
        doThrow(IllegalArgumentException("auth email credential has expired"))
            .`when`(consumer)
            .consume(
                any(AuthEmailDeliveryEvent::class.java) ?: AuthEmailDeliveryEvent("evt", "user@example.com", "LOGIN_CODE", "cipher", Instant.MAX),
                any(Instant::class.java) ?: Instant.EPOCH
            )
        val channel = TestChannel()

        listener.onMessage(message(validEvent(), 16L), channel)

        assertEquals(16L, channel.rejectedTag)
        assertEquals(false, channel.rejectedRequeue)
    }

    @Test
    fun `rejects redelivered transient auth email failure without requeue`() {
        val event = """{"eventType":"auth.email.requested.v1","eventId":"evt-1","payload":{"recipient":"user@example.com","template":"LOGIN_CODE","encryptedCredential":"cipher","expiresAt":"2026-09-21T01:00:00Z"}}"""
        doThrow(IllegalStateException("temporary failure"))
            .`when`(consumer)
            .consume(
                any(AuthEmailDeliveryEvent::class.java) ?: AuthEmailDeliveryEvent("evt", "user@example.com", "LOGIN_CODE", "cipher", Instant.MAX),
                any(Instant::class.java) ?: Instant.EPOCH
            )
        val channel = TestChannel()

        listener.onMessage(message(event, 12L, redelivered = true), channel)

        assertEquals(12L, channel.rejectedTag)
        assertEquals(false, channel.rejectedRequeue)
    }

    /** Verifies missing payload fields are permanent envelope failures. */
    @Test
    fun `rejects auth email envelopes with missing required fields`() {
        listOf(
            "eventId",
            "recipient",
            "template",
            "encryptedCredential",
            "expiresAt"
        ).forEachIndexed { index, field ->
            val malformed = when (field) {
                "eventId" -> validEvent().replace("\"eventId\":\"evt-1\",", "")
                else -> validEvent()
                    .replace("\"$field\":\"${fieldValue(field)}\",", "")
                    .replace("\"$field\":\"${fieldValue(field)}\"", "")
            }
            val channel = TestChannel()

            listener.onMessage(message(malformed, 20L + index), channel)

            assertEquals(20L + index, channel.rejectedTag)
            assertEquals(false, channel.rejectedRequeue)
        }
    }

    @Test
    fun `rejects auth email envelopes with explicit null required fields`() {
        val channel = TestChannel()
        val malformed = validEvent().replace("\"eventId\":\"evt-1\"", "\"eventId\":null")

        listener.onMessage(message(malformed, 28L), channel)

        assertEquals(28L, channel.rejectedTag)
        assertEquals(false, channel.rejectedRequeue)
        assertNull(channel.ackedTag)
    }

    @Test
    fun `rejects auth email envelopes with null event type or payload`() {
        listOf(
            validEvent().replace("\"eventType\":\"auth.email.requested.v1\"", "\"eventType\":null"),
            validEvent().replace("\"payload\":{", "\"payload\":null")
        ).forEachIndexed { index, malformed ->
            val channel = TestChannel()

            listener.onMessage(message(malformed, 29L + index), channel)

            assertEquals(29L + index, channel.rejectedTag)
            assertEquals(false, channel.rejectedRequeue)
            assertNull(channel.ackedTag)
        }
    }

    /** Verifies a nullable parser result is treated as a permanent envelope failure. */
    @Test
    fun `rejects auth email when the object mapper returns no root`() {
        val objectMapper = mock(ObjectMapper::class.java)
        doReturn(null).`when`(objectMapper).readTree(any<ByteArray>())
        val boundaryListener = AuthEmailRabbitListener(objectMapper, consumer)
        val channel = TestChannel()

        boundaryListener.onMessage(message("ignored", 32L), channel)

        assertEquals(32L, channel.rejectedTag)
        assertEquals(false, channel.rejectedRequeue)
        assertNull(channel.ackedTag)
    }

    /** Verifies an absent payload object is rejected separately from an explicit JSON null. */
    @Test
    fun `rejects auth email envelopes without a payload object`() {
        val missingPayload = """{"eventType":"auth.email.requested.v1","eventId":"evt-1"}"""
        val channel = TestChannel()

        listener.onMessage(message(missingPayload, 33L), channel)

        assertEquals(33L, channel.rejectedTag)
        assertEquals(false, channel.rejectedRequeue)
        assertNull(channel.ackedTag)
    }

    /** Verifies an unparsable expiry is rejected as a permanent envelope failure. */
    @Test
    fun `rejects auth email envelopes with invalid expiry`() {
        val channel = TestChannel()
        val malformed = validEvent().replace("2099-09-21T01:00:00Z", "not-an-instant")

        listener.onMessage(message(malformed, 27L), channel)

        assertEquals(27L, channel.rejectedTag)
        assertEquals(false, channel.rejectedRequeue)
        assertNull(channel.ackedTag)
    }

    /** Verifies channel-optional delivery remains safe when no broker channel is supplied. */
    @Test
    fun `does not require a channel to process a valid delivery`() {
        listener.onMessage(message(validEvent(), 26L), null)
    }

    /** Verifies malformed deliveries remain safe when the broker channel is unavailable. */
    @Test
    fun `does not require a channel to reject a malformed delivery`() {
        listener.onMessage(message("{not-json}", 27L), null)
    }

    /** Verifies validation failures remain safe when no broker channel is available. */
    @Test
    fun `does not require a channel to reject a consumer validation failure`() {
        doThrow(IllegalArgumentException("auth email credential has expired"))
            .`when`(consumer)
            .consume(
                any(AuthEmailDeliveryEvent::class.java) ?: AuthEmailDeliveryEvent("evt", "user@example.com", "LOGIN_CODE", "cipher", Instant.MAX),
                any(Instant::class.java) ?: Instant.EPOCH
            )

        listener.onMessage(message(validEvent(), 30L), null)
    }

    /** Verifies transient retry handling remains safe when no broker channel is available. */
    @Test
    fun `does not require a channel to requeue a transient failure`() {
        doThrow(IllegalStateException("temporary failure"))
            .`when`(consumer)
            .consume(
                any(AuthEmailDeliveryEvent::class.java) ?: AuthEmailDeliveryEvent("evt", "user@example.com", "LOGIN_CODE", "cipher", Instant.MAX),
                any(Instant::class.java) ?: Instant.EPOCH
            )

        listener.onMessage(message(validEvent(), 31L, redelivered = false), null)
    }

    @Test
    fun `respects x-attempt header and redelivered fallback`() {
        val channel = TestChannel()
        val msgWithHeader = Message(
            validEvent().toByteArray(StandardCharsets.UTF_8),
            MessageProperties().apply {
                deliveryTag = 40L
                setHeader("x-attempt", 2)
            }
        )
        listener.onMessage(msgWithHeader, channel)
        assertEquals(40L, channel.ackedTag)

        val channel2 = TestChannel()
        val redeliveredMsg = message(validEvent(), 41L, redelivered = true)
        listener.onMessage(redeliveredMsg, channel2)
        assertEquals(41L, channel2.ackedTag)
    }

    private fun fieldValue(field: String): String = when (field) {
        "recipient" -> "user@example.com"
        "template" -> "LOGIN_CODE"
        "encryptedCredential" -> "cipher"
        "expiresAt" -> "2099-09-21T01:00:00Z"
        else -> error("Unsupported test field: $field")
    }

    private fun message(body: String, tag: Long, redelivered: Boolean = false): Message = Message(
        body.toByteArray(StandardCharsets.UTF_8),
        MessageProperties().apply {
            deliveryTag = tag
            isRedelivered = redelivered
        }
    )

    private fun validEvent(): String =
        """{"eventType":"auth.email.requested.v1","eventId":"evt-1","payload":{"recipient":"user@example.com","template":"LOGIN_CODE","encryptedCredential":"cipher","expiresAt":"2099-09-21T01:00:00Z"}}"""

    private class TestChannel : Channel by mock(Channel::class.java) {
        var ackedTag: Long? = null
        var rejectedTag: Long? = null
        var rejectedRequeue: Boolean? = null

        override fun basicAck(deliveryTag: Long, multiple: Boolean) { ackedTag = deliveryTag }
        override fun basicReject(deliveryTag: Long, requeue: Boolean) {
            rejectedTag = deliveryTag
            rejectedRequeue = requeue
        }
    }
}
