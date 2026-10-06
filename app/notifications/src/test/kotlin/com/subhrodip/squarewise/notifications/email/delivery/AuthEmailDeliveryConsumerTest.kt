package com.subhrodip.squarewise.notifications.email.delivery
import org.mockito.ArgumentMatchers.anyString

import com.subhrodip.squarewise.notifications.email.security.AuthEmailEnvelopeProtector
import com.subhrodip.squarewise.notifications.delivery.rate.DeliveryRateLimiter
import java.time.Instant
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.doReturn
import org.mockito.Mockito.doThrow
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify

/**
 * Verifies authentication-email validation, context-bound reveal, and message mapping.
 *
 * The listener owns acknowledgement/requeue policy; this suite deliberately keeps that
 * transport concern separate and proves the consumer never dispatches an invalid or
 * expired credential.
 */
class AuthEmailDeliveryConsumerTest {
    private val protector = mock(AuthEmailEnvelopeProtector::class.java)
    private val dispatcher = mock(EmailDispatcher::class.java)
    private val deliveryRateLimiter = mock(DeliveryRateLimiter::class.java)
    private lateinit var consumer: AuthEmailDeliveryConsumer

    private val now = Instant.parse("2026-10-01T12:00:00Z")

    @BeforeEach
    fun setUp() {
        doReturn(true).`when`(deliveryRateLimiter).allow("alice@example.com")
        consumer = AuthEmailDeliveryConsumer(protector, dispatcher, deliveryRateLimiter)
    }

    @Test
    fun `LOGIN_LINK reveals with recipient and template and maps link message`() {
        doReturn("credential-value").`when`(protector).reveal("cipher", "alice@example.com", "LOGIN_LINK")
        doReturn(EmailDeliveryOutcome.DELIVERED).`when`(dispatcher).send(
            "alice@example.com",
            "Your Squarewise sign-in link",
            "Use this one-time sign-in credential: credential-value"
        )

        val outcome = consumer.consume(event(template = "LOGIN_LINK"), now)

        assertThat(outcome).isEqualTo(EmailDeliveryOutcome.DELIVERED)
        verify(protector).reveal("cipher", "alice@example.com", "LOGIN_LINK")
        verify(dispatcher).send(
            "alice@example.com",
            "Your Squarewise sign-in link",
            "Use this one-time sign-in credential: credential-value"
        )
    }

    @Test
    fun `LOGIN_CODE maps code message and propagates dispatcher outcome`() {
        doReturn("123456").`when`(protector).reveal("cipher", "alice@example.com", "LOGIN_CODE")
        doReturn(EmailDeliveryOutcome.RETRYABLE_FAILURE).`when`(dispatcher).send(
            "alice@example.com",
            "Your Squarewise sign-in code",
            "Your one-time Squarewise sign-in code is: 123456"
        )

        val outcome = consumer.consume(event(template = "LOGIN_CODE"), now)

        assertThat(outcome).isEqualTo(EmailDeliveryOutcome.RETRYABLE_FAILURE)
    }

    @Test
    fun `expired credential is rejected before reveal or dispatch`() {
        val expired = event(expiresAt = now)

        assertThrows(IllegalArgumentException::class.java) { consumer.consume(expired, now) }

        verify(protector, never()).reveal(anyString(), anyString(), anyString())
        verify(dispatcher, never()).send(
            anyString(),
            anyString(),
            anyString()
        )
    }

    @Test
    fun `unsupported template is rejected before reveal or dispatch`() {
        assertThrows(IllegalArgumentException::class.java) {
            consumer.consume(event(template = "PASSWORD_RESET"), now)
        }

        verify(protector, never()).reveal(anyString(), anyString(), anyString())
        verify(dispatcher, never()).send(
            anyString(),
            anyString(),
            anyString()
        )
    }

    @Test
    fun `reveal failure prevents plaintext delivery`() {
        doThrow(IllegalArgumentException("authentication failed")).`when`(protector)
            .reveal("cipher", "alice@example.com", "LOGIN_CODE")

        assertThrows(IllegalArgumentException::class.java) {
            consumer.consume(event(template = "LOGIN_CODE"), now)
        }

        verify(dispatcher, never()).send(
            anyString(),
            anyString(),
            anyString()
        )
    }

    @Test
    fun `delivery rate denial suppresses reveal and dispatch`() {
        doReturn(false).`when`(deliveryRateLimiter).allow("alice@example.com")

        val outcome = consumer.consume(event(template = "LOGIN_CODE"), now)

        assertThat(outcome).isEqualTo(EmailDeliveryOutcome.SKIPPED)
        verify(protector, never()).reveal(anyString(), anyString(), anyString())
        verify(dispatcher, never()).send(anyString(), anyString(), anyString())
    }

    private fun event(
        template: String = "LOGIN_CODE",
        expiresAt: Instant = now.plusSeconds(60)
    ): AuthEmailDeliveryEvent = AuthEmailDeliveryEvent(
        eventId = "event-1",
        recipient = "alice@example.com",
        template = template,
        encryptedCredential = "cipher",
        expiresAt = expiresAt
    )
}
