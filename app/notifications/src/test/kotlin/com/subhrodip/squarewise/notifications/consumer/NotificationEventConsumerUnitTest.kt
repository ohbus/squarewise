package com.subhrodip.squarewise.notifications.consumer

import com.subhrodip.squarewise.notifications.consumer.model.NotificationConsumptionOutcome
import com.subhrodip.squarewise.notifications.consumer.model.NotificationEvent
import com.subhrodip.squarewise.notifications.consumer.persistence.ProcessedNotificationEventRepository
import com.subhrodip.squarewise.notifications.consumer.service.NotificationEventConsumer
import com.subhrodip.squarewise.notifications.consumer.service.TransactionalNotificationEventProcessor
import com.subhrodip.squarewise.notifications.delivery.rate.DeliveryRateLimiter
import com.subhrodip.squarewise.notifications.email.delivery.EmailDeliveryOutcome
import com.subhrodip.squarewise.notifications.email.delivery.EmailDispatcher
import com.subhrodip.squarewise.notifications.email.persistence.NotificationEmailDeliveryEntity
import com.subhrodip.squarewise.notifications.email.persistence.NotificationEmailDeliveryRepository
import com.subhrodip.squarewise.notifications.preferences.model.NotificationPreferences
import com.subhrodip.squarewise.notifications.preferences.persistence.PreferenceStore
import java.time.Instant
import java.util.UUID
import java.util.Optional
import java.sql.SQLException
import org.hibernate.exception.ConstraintViolationException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.anyString
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.eq
import org.mockito.Mockito.doReturn
import org.mockito.Mockito.doThrow
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import com.subhrodip.squarewise.errors.catalog.NotificationErrors
import com.subhrodip.squarewise.notifications.errors.NotificationDomainException
import org.springframework.dao.DataIntegrityViolationException

/** Verifies the notification consumer's dispatch isolation and duplicate boundary. */
class NotificationEventConsumerUnitTest {

    private val processor = mock(TransactionalNotificationEventProcessor::class.java)
    private val processedEvents = mock(ProcessedNotificationEventRepository::class.java)
    private val preferenceStore = mock(PreferenceStore::class.java)
    private val emailDispatcher = mock(EmailDispatcher::class.java)
    private val deliveryRateLimiter = mock(DeliveryRateLimiter::class.java)
    private val consumer = NotificationEventConsumer(
        processor,
        processedEvents,
        preferenceStore,
        emailDispatcher,
        deliveryRateLimiter
    )

    @Test
    fun `trims explicit recipient and dispatches only after preference and rate admission`() {
        val event = sampleEvent(subject = "alice", recipientEmail = "  alice@example.com  ")
        doReturn(NotificationConsumptionOutcome.APPLIED).`when`(processor).process(event)
        doReturn(NotificationPreferences(emailEnabled = true)).`when`(preferenceStore).get("alice")
        doReturn(true).`when`(deliveryRateLimiter).allow("alice")
        doReturn(EmailDeliveryOutcome.DELIVERED).`when`(emailDispatcher)
            .send("alice@example.com", "Notification: expense.created", "Dinner was added")

        assertEquals(NotificationConsumptionOutcome.APPLIED, consumer.consume(event))
        verify(emailDispatcher).send("alice@example.com", "Notification: expense.created", "Dinner was added")
    }

    @Test
    fun `falls back to a trimmed verified subject when explicit recipient is blank`() {
        val event = sampleEvent(subject = "  alice@example.com  ", recipientEmail = "   ")
        doReturn(NotificationConsumptionOutcome.APPLIED).`when`(processor).process(event)
        doReturn(NotificationPreferences(emailEnabled = true)).`when`(preferenceStore).get("  alice@example.com  ")
        doReturn(true).`when`(deliveryRateLimiter).allow("  alice@example.com  ")
        doReturn(EmailDeliveryOutcome.DELIVERED).`when`(emailDispatcher)
            .send("alice@example.com", "Notification: expense.created", "Dinner was added")

        assertEquals(NotificationConsumptionOutcome.APPLIED, consumer.consume(event))
        verify(emailDispatcher).send("alice@example.com", "Notification: expense.created", "Dinner was added")
    }

    @Test
    fun `suppresses dispatch for disabled preferences rate denial and invalid fallback recipient`() {
        val disabled = sampleEvent(subject = "disabled")
        doReturn(NotificationConsumptionOutcome.APPLIED).`when`(processor).process(disabled)
        doReturn(NotificationPreferences(emailEnabled = false)).`when`(preferenceStore).get("disabled")
        assertEquals(NotificationConsumptionOutcome.APPLIED, consumer.consume(disabled))

        val rateDenied = sampleEvent(subject = "rate@example.com")
        doReturn(NotificationConsumptionOutcome.APPLIED).`when`(processor).process(rateDenied)
        doReturn(NotificationPreferences(emailEnabled = true)).`when`(preferenceStore).get("rate@example.com")
        doReturn(false).`when`(deliveryRateLimiter).allow("rate@example.com")
        assertEquals(NotificationConsumptionOutcome.APPLIED, consumer.consume(rateDenied))

        val invalidRecipient = sampleEvent(subject = "opaque-subject")
        doReturn(NotificationConsumptionOutcome.APPLIED).`when`(processor).process(invalidRecipient)
        doReturn(NotificationPreferences(emailEnabled = true)).`when`(preferenceStore).get("opaque-subject")
        assertEquals(NotificationConsumptionOutcome.APPLIED, consumer.consume(invalidRecipient))

        verify(emailDispatcher, never()).send(anyString(), anyString(), anyString())
    }

    @Test
    fun `persists skipped delivery when recipient cannot be resolved or rate is denied`() {
        val deliveries = mock(NotificationEmailDeliveryRepository::class.java)
        doReturn(1).`when`(deliveries).claimForDispatch(
            any(UUID::class.java) ?: UUID(0, 0),
            any(Instant::class.java) ?: Instant.EPOCH,
            any(Instant::class.java) ?: Instant.EPOCH
        )
        val invalidRecipient = sampleEvent(subject = "opaque-subject")
        val invalidDelivery = NotificationEmailDeliveryEntity(invalidRecipient.notificationId)
        doReturn(NotificationConsumptionOutcome.APPLIED).`when`(processor).process(invalidRecipient)
        doReturn(NotificationPreferences(emailEnabled = true)).`when`(preferenceStore).get("opaque-subject")
        doReturn(Optional.of(invalidDelivery)).`when`(deliveries).findById(invalidRecipient.notificationId)

        NotificationEventConsumer(processor, processedEvents, preferenceStore, emailDispatcher, deliveryRateLimiter, deliveries)
            .consume(invalidRecipient)

        val rateDenied = sampleEvent(subject = "rate@example.com")
        val rateDelivery = NotificationEmailDeliveryEntity(rateDenied.notificationId)
        doReturn(NotificationConsumptionOutcome.APPLIED).`when`(processor).process(rateDenied)
        doReturn(NotificationPreferences(emailEnabled = true)).`when`(preferenceStore).get("rate@example.com")
        doReturn(false).`when`(deliveryRateLimiter).allow("rate@example.com")
        doReturn(Optional.of(rateDelivery)).`when`(deliveries).findById(rateDenied.notificationId)

        NotificationEventConsumer(processor, processedEvents, preferenceStore, emailDispatcher, deliveryRateLimiter, deliveries)
            .consume(rateDenied)

        verify(deliveries).updateStatusIfClaimed(
            eq(invalidRecipient.notificationId) ?: UUID(0, 0),
            eq(NotificationEmailDeliveryEntity.SKIPPED) ?: "",
            any(Instant::class.java) ?: Instant.EPOCH,
            any(Instant::class.java) ?: Instant.EPOCH
        )
        verify(deliveries).updateStatusIfClaimed(
            eq(rateDenied.notificationId) ?: UUID(0, 0),
            eq(NotificationEmailDeliveryEntity.SKIPPED) ?: "",
            any(Instant::class.java) ?: Instant.EPOCH,
            any(Instant::class.java) ?: Instant.EPOCH
        )
    }

    @Test
    fun `acknowledges a constraint duplicate only when the event is already durable`() {
        val event = sampleEvent(subject = "alice")
        doThrow(DataIntegrityViolationException("duplicate")).`when`(processor).process(event)
        doReturn(true).`when`(processedEvents).existsById(event.eventId)

        assertEquals(NotificationConsumptionOutcome.DUPLICATE, consumer.consume(event))
        verify(emailDispatcher, never()).send(anyString(), anyString(), anyString())
    }

    @Test
    fun `propagates a constraint violation when the event is not already durable`() {
        val event = sampleEvent(subject = "alice")
        doThrow(DataIntegrityViolationException("duplicate race")).`when`(processor).process(event)
        doReturn(false).`when`(processedEvents).existsById(event.eventId)

        assertThrows(DataIntegrityViolationException::class.java) { consumer.consume(event) }
    }

    @Test
    fun `acknowledges a Hibernate constraint duplicate only when already durable`() {
        val event = sampleEvent(subject = "hibernate-duplicate")
        doThrow(ConstraintViolationException("duplicate", SQLException("duplicate"), "processed_events_pkey"))
            .`when`(processor).process(event)
        doReturn(true).`when`(processedEvents).existsById(event.eventId)

        assertEquals(NotificationConsumptionOutcome.DUPLICATE, consumer.consume(event))
    }

    @Test
    fun `propagates preference and dispatcher failures for broker retry`() {
        val preferenceFailure = sampleEvent(subject = "preference-failure")
        doReturn(NotificationConsumptionOutcome.APPLIED).`when`(processor).process(preferenceFailure)
        doThrow(IllegalStateException("preference store unavailable")).`when`(preferenceStore)
            .get("preference-failure")
        assertThrows(IllegalStateException::class.java) { consumer.consume(preferenceFailure) }

        val dispatchFailure = sampleEvent(subject = "dispatch@example.com")
        doReturn(NotificationConsumptionOutcome.APPLIED).`when`(processor).process(dispatchFailure)
        doReturn(NotificationPreferences(emailEnabled = true)).`when`(preferenceStore)
            .get("dispatch@example.com")
        doReturn(true).`when`(deliveryRateLimiter).allow("dispatch@example.com")
        doThrow(IllegalStateException("smtp unavailable")).`when`(emailDispatcher)
            .send("dispatch@example.com", "Notification: expense.created", "Dinner was added")

        assertThrows(IllegalStateException::class.java) { consumer.consume(dispatchFailure) }
    }

    @Test
    fun `propagates limiter store failure for broker retry`() {
        val event = sampleEvent(subject = "rate-store@example.com")
        doReturn(NotificationConsumptionOutcome.APPLIED).`when`(processor).process(event)
        doReturn(NotificationPreferences(emailEnabled = true)).`when`(preferenceStore)
            .get("rate-store@example.com")
        doThrow(IllegalStateException("redis unavailable"))
            .`when`(deliveryRateLimiter).allow("rate-store@example.com")

        assertThrows(IllegalStateException::class.java) { consumer.consume(event) }
        verify(emailDispatcher, never()).send(anyString(), anyString(), anyString())
    }

    @Test
    fun `propagates non-duplicate processor failures for broker retry`() {
        val event = sampleEvent(subject = "alice")
        val failure = IllegalStateException("database unavailable")
        doThrow(failure).`when`(processor).process(event)

        assertThrows(IllegalStateException::class.java) { consumer.consume(event) }
        verify(processedEvents, never()).existsById(event.eventId)
    }

    @Test
    fun `persists a newly created delivery and records skipped dispatch as terminal`() {
        val deliveries = mock(NotificationEmailDeliveryRepository::class.java)
        doReturn(1).`when`(deliveries).claimForDispatch(
            any(UUID::class.java) ?: UUID(0, 0),
            any(Instant::class.java) ?: Instant.EPOCH,
            any(Instant::class.java) ?: Instant.EPOCH
        )
        val event = sampleEvent(subject = "skip@example.com")
        val delivery = NotificationEmailDeliveryEntity(event.notificationId)
        doReturn(NotificationConsumptionOutcome.APPLIED).`when`(processor).process(event)
        doReturn(Optional.empty<NotificationEmailDeliveryEntity>(), Optional.of(delivery)).`when`(deliveries)
            .findById(event.notificationId)
        doReturn(delivery).`when`(deliveries).save(delivery)
        doReturn(NotificationPreferences(emailEnabled = true)).`when`(preferenceStore).get("skip@example.com")
        doReturn(true).`when`(deliveryRateLimiter).allow("skip@example.com")
        doReturn(EmailDeliveryOutcome.SKIPPED).`when`(emailDispatcher)
            .send("skip@example.com", "Notification: expense.created", "Dinner was added")

        NotificationEventConsumer(processor, processedEvents, preferenceStore, emailDispatcher, deliveryRateLimiter, deliveries)
            .consume(event)

        verify(deliveries).updateStatusIfClaimed(
            eq(event.notificationId) ?: UUID(0, 0),
            eq(NotificationEmailDeliveryEntity.SKIPPED) ?: "",
            any(Instant::class.java) ?: Instant.EPOCH,
            any(Instant::class.java) ?: Instant.EPOCH
        )
    }

    @Test
    fun `redelivers only pending state and leaves terminal states untouched`() {
        val deliveries = mock(NotificationEmailDeliveryRepository::class.java)
        doReturn(1).`when`(deliveries).claimForDispatch(
            any(UUID::class.java) ?: UUID(0, 0),
            any(Instant::class.java) ?: Instant.EPOCH,
            any(Instant::class.java) ?: Instant.EPOCH
        )
        val pending = sampleEvent(subject = "pending@example.com")
        val pendingDelivery = NotificationEmailDeliveryEntity(pending.notificationId)
        doReturn(NotificationConsumptionOutcome.DUPLICATE).`when`(processor).process(pending)
        doReturn(Optional.of(pendingDelivery)).`when`(deliveries).findById(pending.notificationId)
        doReturn(NotificationPreferences(emailEnabled = true)).`when`(preferenceStore).get("pending@example.com")
        doReturn(true).`when`(deliveryRateLimiter).allow("pending@example.com")
        doReturn(EmailDeliveryOutcome.DELIVERED).`when`(emailDispatcher)
            .send("pending@example.com", "Notification: expense.created", "Dinner was added")

        NotificationEventConsumer(processor, processedEvents, preferenceStore, emailDispatcher, deliveryRateLimiter, deliveries)
            .consume(pending)
        verify(emailDispatcher).send("pending@example.com", "Notification: expense.created", "Dinner was added")
    }

    @Test
    fun `returns outcome early when claimForDispatch loses claim race`() {
        val deliveries = mock(NotificationEmailDeliveryRepository::class.java)
        doReturn(0).`when`(deliveries).claimForDispatch(
            any(UUID::class.java) ?: UUID(0, 0),
            any(Instant::class.java) ?: Instant.EPOCH,
            any(Instant::class.java) ?: Instant.EPOCH
        )
        val event = sampleEvent(subject = "user@example.com")
        val pendingDelivery = NotificationEmailDeliveryEntity(event.notificationId, status = NotificationEmailDeliveryEntity.PENDING)
        doReturn(NotificationConsumptionOutcome.DUPLICATE).`when`(processor).process(event)
        doReturn(Optional.of(pendingDelivery)).`when`(deliveries).findById(event.notificationId)

        val outcome = NotificationEventConsumer(processor, processedEvents, preferenceStore, emailDispatcher, deliveryRateLimiter, deliveries)
            .consume(event)

        assertEquals(NotificationConsumptionOutcome.DUPLICATE, outcome)
        verify(emailDispatcher, never()).send(anyString(), anyString(), anyString())
    }

    @Test
    fun `redelivers when delivery status is DISPATCHING and outcome is DUPLICATE`() {
        val deliveries = mock(NotificationEmailDeliveryRepository::class.java)
        doReturn(1).`when`(deliveries).claimForDispatch(
            any(UUID::class.java) ?: UUID(0, 0),
            any(Instant::class.java) ?: Instant.EPOCH,
            any(Instant::class.java) ?: Instant.EPOCH
        )
        val event = sampleEvent(subject = "dispatching@example.com")
        val dispatchingDelivery = NotificationEmailDeliveryEntity(event.notificationId, status = NotificationEmailDeliveryEntity.DISPATCHING)
        doReturn(NotificationConsumptionOutcome.DUPLICATE).`when`(processor).process(event)
        doReturn(Optional.of(dispatchingDelivery)).`when`(deliveries).findById(event.notificationId)
        doReturn(NotificationPreferences(emailEnabled = true)).`when`(preferenceStore).get("dispatching@example.com")
        doReturn(true).`when`(deliveryRateLimiter).allow("dispatching@example.com")
        doReturn(EmailDeliveryOutcome.DELIVERED).`when`(emailDispatcher)
            .send("dispatching@example.com", "Notification: expense.created", "Dinner was added")

        NotificationEventConsumer(processor, processedEvents, preferenceStore, emailDispatcher, deliveryRateLimiter, deliveries)
            .consume(event)
        verify(emailDispatcher).send("dispatching@example.com", "Notification: expense.created", "Dinner was added")
    }

    @Test
    fun `skips dispatch when delivery status is SKIPPED`() {
        val deliveries = mock(NotificationEmailDeliveryRepository::class.java)
        val event = sampleEvent(subject = "skipped@example.com")
        val skippedDelivery = NotificationEmailDeliveryEntity(event.notificationId, status = NotificationEmailDeliveryEntity.SKIPPED)
        doReturn(NotificationConsumptionOutcome.APPLIED).`when`(processor).process(event)
        doReturn(Optional.of(skippedDelivery)).`when`(deliveries).findById(event.notificationId)

        val outcome = NotificationEventConsumer(processor, processedEvents, preferenceStore, emailDispatcher, deliveryRateLimiter, deliveries)
            .consume(event)

        assertEquals(NotificationConsumptionOutcome.APPLIED, outcome)
        verify(emailDispatcher, never()).send(anyString(), anyString(), anyString())
    }

    @Test
    fun `marks delivery as SKIPPED when domain exception retry policy is NEVER`() {
        val deliveries = mock(NotificationEmailDeliveryRepository::class.java)
        doReturn(1).`when`(deliveries).claimForDispatch(
            any(UUID::class.java) ?: UUID(0, 0),
            any(Instant::class.java) ?: Instant.EPOCH,
            any(Instant::class.java) ?: Instant.EPOCH
        )
        val event = sampleEvent(subject = "bad-domain@example.com")
        val delivery = NotificationEmailDeliveryEntity(event.notificationId, status = NotificationEmailDeliveryEntity.PENDING)
        doReturn(NotificationConsumptionOutcome.APPLIED).`when`(processor).process(event)
        doReturn(Optional.of(delivery)).`when`(deliveries).findById(event.notificationId)
        doReturn(NotificationPreferences(emailEnabled = true)).`when`(preferenceStore).get("bad-domain@example.com")
        doReturn(true).`when`(deliveryRateLimiter).allow("bad-domain@example.com")
        doThrow(NotificationDomainException(NotificationErrors.NOTIFICATION_PAYLOAD_CORRUPT))
            .`when`(emailDispatcher).send("bad-domain@example.com", "Notification: expense.created", "Dinner was added")

        val consumerWithDeliveries = NotificationEventConsumer(
            processor, processedEvents, preferenceStore, emailDispatcher, deliveryRateLimiter, deliveries
        )

        assertThrows(NotificationDomainException::class.java) {
            consumerWithDeliveries.consume(event)
        }

        verify(deliveries).updateStatusIfClaimed(
            eq(event.notificationId) ?: UUID(0, 0),
            eq(NotificationEmailDeliveryEntity.SKIPPED) ?: "",
            any(Instant::class.java) ?: Instant.EPOCH,
            any(Instant::class.java) ?: Instant.EPOCH
        )
    }

    @Test
    fun `markDelivery updates only the current dispatch lease`() {
        val deliveries = mock(NotificationEmailDeliveryRepository::class.java)
        doReturn(1).`when`(deliveries).claimForDispatch(
            any(UUID::class.java) ?: UUID(0, 0),
            any(Instant::class.java) ?: Instant.EPOCH,
            any(Instant::class.java) ?: Instant.EPOCH
        )
        val event = sampleEvent(subject = "user@example.com")
        val delivery = NotificationEmailDeliveryEntity(event.notificationId, status = NotificationEmailDeliveryEntity.PENDING)
        doReturn(Optional.of(delivery)).`when`(deliveries).findById(event.notificationId)
        doReturn(NotificationConsumptionOutcome.APPLIED).`when`(processor).process(event)
        doReturn(NotificationPreferences(emailEnabled = true)).`when`(preferenceStore).get("user@example.com")
        doReturn(true).`when`(deliveryRateLimiter).allow("user@example.com")
        doReturn(EmailDeliveryOutcome.DELIVERED).`when`(emailDispatcher)
            .send("user@example.com", "Notification: expense.created", "Dinner was added")
        doReturn(1).`when`(deliveries).updateStatusIfClaimed(
            any(UUID::class.java) ?: UUID(0, 0),
            anyString(),
            any(Instant::class.java) ?: Instant.EPOCH,
            any(Instant::class.java) ?: Instant.EPOCH
        )

        val consumerWithDeliveries = NotificationEventConsumer(
            processor, processedEvents, preferenceStore, emailDispatcher, deliveryRateLimiter, deliveries
        )

        val outcome = consumerWithDeliveries.consume(event)
        assertEquals(NotificationConsumptionOutcome.APPLIED, outcome)

        verify(deliveries).updateStatusIfClaimed(
            any(UUID::class.java) ?: UUID(0, 0),
            anyString(),
            any(Instant::class.java) ?: Instant.EPOCH,
            any(Instant::class.java) ?: Instant.EPOCH
        )
        verify(deliveries, never()).save(any(NotificationEmailDeliveryEntity::class.java) ?: NotificationEmailDeliveryEntity(UUID(0, 0)))
    }

    @Test
    fun `stale dispatch claimant cannot overwrite a newer status`() {
        val deliveries = mock(NotificationEmailDeliveryRepository::class.java)
        val event = sampleEvent(subject = "stale@example.com")
        val delivery = NotificationEmailDeliveryEntity(
            event.notificationId,
            status = NotificationEmailDeliveryEntity.PENDING
        )
        doReturn(NotificationConsumptionOutcome.APPLIED).`when`(processor).process(event)
        doReturn(Optional.of(delivery)).`when`(deliveries).findById(event.notificationId)
        doReturn(1).`when`(deliveries).claimForDispatch(
            any(UUID::class.java) ?: UUID(0, 0),
            any(Instant::class.java) ?: Instant.EPOCH,
            any(Instant::class.java) ?: Instant.EPOCH
        )
        doReturn(NotificationPreferences(emailEnabled = true)).`when`(preferenceStore).get("stale@example.com")
        doReturn(true).`when`(deliveryRateLimiter).allow("stale@example.com")
        doReturn(EmailDeliveryOutcome.DELIVERED).`when`(emailDispatcher)
            .send("stale@example.com", "Notification: expense.created", "Dinner was added")
        doReturn(0).`when`(deliveries).updateStatusIfClaimed(
            any(UUID::class.java) ?: UUID(0, 0),
            anyString(),
            any(Instant::class.java) ?: Instant.EPOCH,
            any(Instant::class.java) ?: Instant.EPOCH
        )

        NotificationEventConsumer(
            processor,
            processedEvents,
            preferenceStore,
            emailDispatcher,
            deliveryRateLimiter,
            deliveries
        ).consume(event)

        verify(deliveries, never()).save(any(NotificationEmailDeliveryEntity::class.java) ?: NotificationEmailDeliveryEntity(UUID(0, 0)))
    }

    private fun sampleEvent(subject: String, recipientEmail: String? = null): NotificationEvent =
        NotificationEvent(
            eventId = UUID.randomUUID(),
            notificationId = UUID.randomUUID(),
            subject = subject,
            eventType = "expense.created",
            message = "Dinner was added",
            occurredAt = Instant.parse("2026-09-18T10:00:00Z"),
            recipientEmail = recipientEmail,
            title = "expense.created",
            body = "Dinner was added"
        )
}
