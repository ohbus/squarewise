package com.subhrodip.squarewise.notifications

import com.subhrodip.squarewise.notifications.consumer.config.NotificationMessagingProperties
import com.subhrodip.squarewise.notifications.email.config.EmailProperties
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/** Verifies notification broker and SMTP properties retain values supplied by binding. */
class NotificationConfigurationPropertiesTest {
    @Test
    fun `notification messaging properties expose safe queue defaults`() {
        val properties = NotificationMessagingProperties()

        assertEquals("squarewise.notifications.v2", properties.queue)
        assertEquals("squarewise.auth-email.v2", properties.authEmailQueue)
        assertEquals("squarewise.events.dlx", properties.deadLetterExchange)
        assertEquals("squarewise.notifications.v2.dlq", properties.deadLetterQueue)
        assertEquals("squarewise.auth-email.v2.dlq", properties.authEmailDeadLetterQueue)
        assertEquals(10, properties.deliveryMaxPermits)
        assertEquals(60, properties.deliveryWindowSeconds)
    }

    @Test
    fun `notification delivery policy bounds fail closed`() {
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException::class.java) {
            NotificationMessagingProperties(deliveryMaxPermits = 0)
        }
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException::class.java) {
            NotificationMessagingProperties(deliveryWindowSeconds = 0)
        }
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException::class.java) {
            NotificationMessagingProperties(deliveryMaxPermits = 1_000_001)
        }
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException::class.java) {
            NotificationMessagingProperties(deliveryWindowSeconds = 86_401)
        }
        NotificationMessagingProperties(deliveryMaxPermits = 1_000_000, deliveryWindowSeconds = 86_400)
    }

    @Test
    fun `notification properties accept bound SMTP and queue values`() {
        val email = EmailProperties(
            host = "smtp.example.test",
            port = 2525,
            fromAddress = "notifications@example.test",
            enabled = false,
            maxAttempts = 4,
            retryDelayMs = 250
        )
        email.host = "mail.example.test"
        email.port = 1025
        email.fromAddress = "noreply@example.test"
        email.enabled = true
        email.maxAttempts = 2
        email.retryDelayMs = 100

        val messaging = NotificationMessagingProperties()
        messaging.queue = "notifications.test"
        messaging.authEmailQueue = "auth-email.test"
        messaging.deadLetterExchange = "events.test.dlx"
        messaging.deadLetterQueue = "notifications.test.dlq"
        messaging.authEmailDeadLetterQueue = "auth-email.test.dlq"

        assertEquals("mail.example.test", email.host)
        assertEquals(1025, email.port)
        assertEquals("noreply@example.test", email.fromAddress)
        assertEquals(true, email.enabled)
        assertEquals(2, email.maxAttempts)
        assertEquals(100, email.retryDelayMs)
        assertEquals("notifications.test", messaging.queue)
        assertEquals("auth-email.test", messaging.authEmailQueue)
        assertEquals("events.test.dlx", messaging.deadLetterExchange)
        assertEquals("notifications.test.dlq", messaging.deadLetterQueue)
        assertEquals("auth-email.test.dlq", messaging.authEmailDeadLetterQueue)
    }
}
