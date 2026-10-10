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
import com.subhrodip.squarewise.notifications.delivery.rate.TestDeliveryRateLimiter
import com.subhrodip.squarewise.notifications.preferences.persistence.PreferenceStore

import com.subhrodip.squarewise.notifications.email.delivery.EmailDeliveryOutcome
import com.subhrodip.squarewise.notifications.email.delivery.EmailDispatcher

import com.subhrodip.squarewise.notifications.delivery.rate.DeliveryRateLimiter
import com.subhrodip.squarewise.notifications.preferences.model.NotificationPreferences
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mockito.doReturn
import org.mockito.Mockito.doThrow
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import java.time.Duration
import java.time.Instant
import java.util.UUID

class NotificationConsumerServiceTest {

    private lateinit var processor: TransactionalNotificationEventProcessor
    private lateinit var processedEvents: ProcessedNotificationEventRepository
    private lateinit var preferenceStore: PreferenceStore
    private lateinit var emailDispatcher: EmailDispatcher
    private lateinit var deliveryRateLimiter: DeliveryRateLimiter
    private lateinit var consumer: NotificationConsumerService

    @BeforeEach
    fun setUp() {
        processor = mock(TransactionalNotificationEventProcessor::class.java)
        processedEvents = mock(ProcessedNotificationEventRepository::class.java)
        preferenceStore = mock(PreferenceStore::class.java)
        emailDispatcher = mock(EmailDispatcher::class.java)
        deliveryRateLimiter = TestDeliveryRateLimiter(10, Duration.ofMinutes(1))

        consumer = NotificationConsumerService(
            processor = processor,
            processedEvents = processedEvents,
            preferenceStore = preferenceStore,
            emailDispatcher = emailDispatcher,
            deliveryRateLimiter = deliveryRateLimiter
        )
    }

    @Test
    fun `suppresses delivery when only an unverified subject is available`() {
        val event = sampleEvent(subject = "alice")
        doReturn(NotificationConsumptionOutcome.APPLIED).`when`(processor).process(event)
        doReturn(NotificationPreferences(emailEnabled = true, pushEnabled = true)).`when`(preferenceStore).get("alice")

        val outcome = consumer.consume(event)

        assertEquals(NotificationConsumptionOutcome.APPLIED, outcome)
        verify(emailDispatcher, never()).send(anyString(), anyString(), anyString())
    }

    @Test
    fun `dispatches email using explicit recipientEmail if provided`() {
        val event = sampleEvent(
            subject = "alice",
            recipientEmail = "custom.alice@example.com",
            title = "custom.title",
            body = "custom.body"
        )
        doReturn(NotificationConsumptionOutcome.APPLIED).`when`(processor).process(event)
        doReturn(NotificationPreferences(emailEnabled = true, pushEnabled = true)).`when`(preferenceStore).get("alice")
        doReturn(EmailDeliveryOutcome.DELIVERED).`when`(emailDispatcher)
            .send("custom.alice@example.com", "Notification: custom.title", "custom.body")

        val outcome = consumer.consume(event)

        assertEquals(NotificationConsumptionOutcome.APPLIED, outcome)
        verify(emailDispatcher, times(1))
            .send("custom.alice@example.com", "Notification: custom.title", "custom.body")
    }

    @Test
    fun `dispatches email using subject directly if subject contains email address`() {
        val event = sampleEvent(subject = "bob@example.com")
        doReturn(NotificationConsumptionOutcome.APPLIED).`when`(processor).process(event)
        doReturn(NotificationPreferences(emailEnabled = true)).`when`(preferenceStore).get("bob@example.com")
        doReturn(EmailDeliveryOutcome.DELIVERED).`when`(emailDispatcher)
            .send("bob@example.com", "Notification: expense.created", "Dinner was added")

        val outcome = consumer.consume(event)

        assertEquals(NotificationConsumptionOutcome.APPLIED, outcome)
        verify(emailDispatcher, times(1))
            .send("bob@example.com", "Notification: expense.created", "Dinner was added")
    }

    @Test
    fun `skips email dispatch when recipient preferences have email disabled`() {
        val event = sampleEvent(subject = "bob")
        doReturn(NotificationConsumptionOutcome.APPLIED).`when`(processor).process(event)
        doReturn(NotificationPreferences(emailEnabled = false, pushEnabled = true)).`when`(preferenceStore).get("bob")

        val outcome = consumer.consume(event)

        assertEquals(NotificationConsumptionOutcome.APPLIED, outcome)
        verify(emailDispatcher, never()).send(anyString(), anyString(), anyString())
    }

    @Test
    fun `suppresses email when recipient preference is missing`() {
        val event = sampleEvent(subject = "charlie")
        doReturn(NotificationConsumptionOutcome.APPLIED).`when`(processor).process(event)
        doReturn(null).`when`(preferenceStore).get("charlie")
        val outcome = consumer.consume(event)

        assertEquals(NotificationConsumptionOutcome.APPLIED, outcome)
        verify(emailDispatcher, never()).send(anyString(), anyString(), anyString())
    }

    @Test
    fun `propagates preference store failures for broker retry`() {
        val event = sampleEvent(subject = "dave")
        doReturn(NotificationConsumptionOutcome.APPLIED).`when`(processor).process(event)
        doThrow(RuntimeException("Preferences database connection failure")).`when`(preferenceStore).get("dave")
        assertThrows(RuntimeException::class.java) { consumer.consume(event) }
        verify(emailDispatcher, never()).send(anyString(), anyString(), anyString())
    }

    @Test
    fun `suppresses delivery after the per-recipient rate limit`() {
        deliveryRateLimiter = TestDeliveryRateLimiter(1, Duration.ofMinutes(1))
        consumer = NotificationConsumerService(processor, processedEvents, preferenceStore, emailDispatcher, deliveryRateLimiter)
        val first = sampleEvent(subject = "rate@example.com")
        val second = sampleEvent(subject = "rate@example.com")
        doReturn(NotificationConsumptionOutcome.APPLIED).`when`(processor).process(first)
        doReturn(NotificationConsumptionOutcome.APPLIED).`when`(processor).process(second)
        doReturn(NotificationPreferences(emailEnabled = true)).`when`(preferenceStore).get("rate@example.com")
        doReturn(EmailDeliveryOutcome.DELIVERED).`when`(emailDispatcher)
            .send("rate@example.com", "Notification: expense.created", "Dinner was added")

        consumer.consume(first)
        consumer.consume(second)

        verify(emailDispatcher, times(1))
            .send("rate@example.com", "Notification: expense.created", "Dinner was added")
    }

    @Test
    fun `propagates email dispatch failure for broker retry`() {
        val event = sampleEvent(subject = "eve@example.com")
        doReturn(NotificationConsumptionOutcome.APPLIED).`when`(processor).process(event)
        doReturn(NotificationPreferences(emailEnabled = true)).`when`(preferenceStore).get("eve@example.com")
        doThrow(RuntimeException("SMTP server unreachable")).`when`(emailDispatcher)
            .send(anyString(), anyString(), anyString())

        assertThrows(RuntimeException::class.java) { consumer.consume(event) }
        verify(processor, times(1)).process(event)
    }

    @Test
    fun `propagates permanent delivery failure for broker visibility`() {
        val event = sampleEvent(subject = "frank")
        doReturn(NotificationConsumptionOutcome.APPLIED).`when`(processor).process(event)
        doReturn(NotificationPreferences(emailEnabled = true)).`when`(preferenceStore).get("frank")
        doReturn(EmailDeliveryOutcome.PERMANENT_FAILURE).`when`(emailDispatcher)
            .send(anyString(), anyString(), anyString())

        assertEquals(NotificationConsumptionOutcome.APPLIED, consumer.consume(event))
        verify(processor, times(1)).process(event)
    }

    @Test
    fun `duplicate event does not trigger email dispatch`() {
        val event = sampleEvent(subject = "grace")
        doReturn(NotificationConsumptionOutcome.DUPLICATE).`when`(processor).process(event)

        val outcome = consumer.consume(event)

        assertEquals(NotificationConsumptionOutcome.DUPLICATE, outcome)
        verify(preferenceStore, never()).get(anyString())
        verify(emailDispatcher, never()).send(anyString(), anyString(), anyString())
    }

    private fun sampleEvent(
        subject: String,
        recipientEmail: String? = null,
        title: String = "expense.created",
        body: String = "Dinner was added"
    ) = NotificationEvent(
        eventId = UUID.randomUUID(),
        notificationId = UUID.randomUUID(),
        subject = subject,
        eventType = title,
        message = body,
        occurredAt = Instant.parse("2026-09-18T10:00:00Z"),
        recipientEmail = recipientEmail,
        title = title,
        body = body
    )
}
