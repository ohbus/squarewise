package com.subhrodip.squarewise.notifications.email

import com.subhrodip.squarewise.notifications.email.config.EmailProperties
import com.subhrodip.squarewise.notifications.email.delivery.EmailDeliveryOutcome
import com.subhrodip.squarewise.notifications.email.delivery.EmailDispatcher
import com.subhrodip.squarewise.notifications.email.smtp.JavaMailSender
import com.subhrodip.squarewise.notifications.email.smtp.MailSendException
import com.subhrodip.squarewise.notifications.email.smtp.SimpleMailMessage
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.doAnswer
import org.mockito.Mockito.doThrow
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import java.net.ConnectException
import java.net.SocketException
import java.io.IOException

class EmailDispatcherTest {

    private lateinit var mailSender: JavaMailSender
    private lateinit var properties: EmailProperties
    private lateinit var dispatcher: EmailDispatcher

    @BeforeEach
    fun setUp() {
        mailSender = mock(JavaMailSender::class.java)
        properties = EmailProperties(
            host = "localhost",
            port = 1025,
            fromAddress = "notifications@squarewise.local",
            enabled = true,
            maxAttempts = 3,
            retryDelayMs = 0L
        )
        dispatcher = EmailDispatcher(properties, mailSender)
    }

    @Test
    fun `successful dispatch sends SimpleMailMessage with correct recipient from subject and text`() {
        val recipient = "alice@example.com"
        val subject = "Expense Split Update"
        val body = "You owe $25.00 for Dinner."

        val outcome = dispatcher.dispatch(recipient, subject, body)

        assertThat(outcome).isEqualTo(EmailDeliveryOutcome.DELIVERED)

        val messageCaptor = ArgumentCaptor.forClass(SimpleMailMessage::class.java)
        verify(mailSender, times(1)).send(captureMessage(messageCaptor))

        val sentMessage = messageCaptor.value
        assertThat(sentMessage.from).isEqualTo("notifications@squarewise.local")
        assertThat(sentMessage.to).containsExactly("alice@example.com")
        assertThat(sentMessage.subject).isEqualTo("Expense Split Update")
        assertThat(sentMessage.text).isEqualTo("You owe $25.00 for Dinner.")
    }

    /** Verifies the public send alias preserves the same delivery and message contract as dispatch. */
    @Test
    fun `send delivers through the public compatibility entry point`() {
        val outcome = dispatcher.send("alice@example.com", "Subject", "Body")

        assertThat(outcome).isEqualTo(EmailDeliveryOutcome.DELIVERED)
        verify(mailSender, times(1)).send(anyMessage())
    }

    @Test
    fun `transient mail send exception returns RETRYABLE_FAILURE after exhausting retries`() {
        val recipient = "bob@example.com"
        val subject = "Payment Reminder"
        val body = "Please settle the outstanding balance."

        doThrow(MailSendException("Connection refused", ConnectException("Connection refused to 1025")))
            .`when`(mailSender)
            .send(anyMessage())

        val outcome = dispatcher.dispatch(recipient, subject, body)

        assertThat(outcome).isEqualTo(EmailDeliveryOutcome.RETRYABLE_FAILURE)
        verify(mailSender, times(3)).send(anyMessage())
    }

    @Test
    fun `transient socket exception retries and succeeds on subsequent attempt`() {
        val recipient = "charlie@example.com"
        val subject = "Weekly Summary"
        val body = "Here is your weekly split summary."

        var attempts = 0
        doAnswer {
            attempts++
            if (attempts == 1) {
                throw MailSendException("Transient network glitch", SocketException("Connection reset"))
            }
            null
        }.`when`(mailSender).send(anyMessage())

        val outcome = dispatcher.dispatch(recipient, subject, body)

        assertThat(outcome).isEqualTo(EmailDeliveryOutcome.DELIVERED)
        assertThat(attempts).isEqualTo(2)
        verify(mailSender, times(2)).send(anyMessage())
    }

    /** Verifies an interrupted retry delay stops retrying and preserves interruption. */
    @Test
    fun `interrupted retry delay returns retryable failure and restores interrupt`() {
        properties.retryDelayMs = 1L
        doThrow(MailSendException("temporary SMTP failure"))
            .`when`(mailSender)
            .send(anyMessage())

        Thread.currentThread().interrupt()
        try {
            val outcome = dispatcher.dispatch("delay@example.com", "Subject", "Body")

            assertThat(outcome).isEqualTo(EmailDeliveryOutcome.RETRYABLE_FAILURE)
            assertThat(Thread.currentThread().isInterrupted).isTrue()
            verify(mailSender, times(1)).send(anyMessage())
        } finally {
            Thread.interrupted()
        }
    }

    @Test
    fun `positive retry delay allows a transient failure to recover`() {
        properties.retryDelayMs = 1L
        var attempts = 0
        doAnswer {
            attempts++
            if (attempts == 1) {
                throw MailSendException("temporary SMTP failure", SocketException("reset"))
            }
            Unit
        }.`when`(mailSender).send(anyMessage())

        val outcome = dispatcher.dispatch("delay-recovery@example.com", "Subject", "Body")

        assertThat(outcome).isEqualTo(EmailDeliveryOutcome.DELIVERED)
        assertThat(attempts).isEqualTo(2)
    }

    /** Verifies permanent causes remain non-retryable when wrapped by a mail failure. */
    @Test
    fun `nested illegal argument failure is permanent without retrying`() {
        doThrow(MailSendException("invalid message", IllegalArgumentException("invalid header")))
            .`when`(mailSender)
            .send(anyMessage())

        val outcome = dispatcher.dispatch("nested@example.com", "Subject", "Body")

        assertThat(outcome).isEqualTo(EmailDeliveryOutcome.PERMANENT_FAILURE)
        verify(mailSender, times(1)).send(anyMessage())
    }

    /** Verifies direct I/O failures use the transient retry policy. */
    @Test
    fun `direct io failure exhausts transient retries`() {
        properties.maxAttempts = 2
        doAnswer { throw IOException("connection reset") }
            .`when`(mailSender)
            .send(anyMessage())

        val outcome = dispatcher.dispatch("io@example.com", "Subject", "Body")

        assertThat(outcome).isEqualTo(EmailDeliveryOutcome.RETRYABLE_FAILURE)
        verify(mailSender, times(2)).send(anyMessage())
    }

    @Test
    fun `disabled dispatcher skips sending and returns SKIPPED`() {
        properties.enabled = false
        val outcome = dispatcher.dispatch("alice@example.com", "Test", "Disabled body")

        assertThat(outcome).isEqualTo(EmailDeliveryOutcome.SKIPPED)
        verify(mailSender, never()).send(anyMessage())
    }

    @Test
    fun `invalid email address returns PERMANENT_FAILURE without sending`() {
        val invalidEmails = listOf(
            "",
            "   ",
            "not-an-email",
            "@missinguser.com",
            "missingdomain@.com",
            "${"a".repeat(245)}@example.com"
        )

        for (invalid in invalidEmails) {
            val outcome = dispatcher.dispatch(invalid, "Subject", "Body")
            assertThat(outcome).isEqualTo(EmailDeliveryOutcome.PERMANENT_FAILURE)
        }

        verify(mailSender, never()).send(anyMessage())
    }

    @Test
    fun `blank subject returns PERMANENT_FAILURE`() {
        val outcome = dispatcher.dispatch("alice@example.com", "   ", "Valid body")

        assertThat(outcome).isEqualTo(EmailDeliveryOutcome.PERMANENT_FAILURE)
        verify(mailSender, never()).send(anyMessage())
    }

    @Test
    fun `illegal argument exception during sending returns PERMANENT_FAILURE without retrying`() {
        doThrow(IllegalArgumentException("Illegal message header"))
            .`when`(mailSender)
            .send(anyMessage())

        val outcome = dispatcher.dispatch("alice@example.com", "Valid Subject", "Body")

        assertThat(outcome).isEqualTo(EmailDeliveryOutcome.PERMANENT_FAILURE)
        verify(mailSender, times(1)).send(anyMessage())
    }

    @Test
    fun `unknown mail failure is permanent and is not retried`() {
        doThrow(IllegalStateException("unexpected sender failure"))
            .`when`(mailSender)
            .send(anyMessage())

        val outcome = dispatcher.dispatch("alice@example.com", "Valid Subject", "Body")

        assertThat(outcome).isEqualTo(EmailDeliveryOutcome.PERMANENT_FAILURE)
        verify(mailSender, times(1)).send(anyMessage())
    }

    @Test
    fun `normalizes zero max attempts to one send and trims recipient`() {
        properties.maxAttempts = 0
        val outcome = dispatcher.dispatch("  alice@example.com  ", "Valid Subject", "Body")

        assertThat(outcome).isEqualTo(EmailDeliveryOutcome.DELIVERED)
        val messageCaptor = ArgumentCaptor.forClass(SimpleMailMessage::class.java)
        verify(mailSender).send(captureMessage(messageCaptor))
        assertThat(messageCaptor.value.to).containsExactly("alice@example.com")
    }

    @Suppress("UNCHECKED_CAST")
    private fun anyMessage(): SimpleMailMessage = any(SimpleMailMessage::class.java) ?: SimpleMailMessage()

    private fun captureMessage(captor: ArgumentCaptor<SimpleMailMessage>): SimpleMailMessage {
        captor.capture()
        return SimpleMailMessage()
    }
}
