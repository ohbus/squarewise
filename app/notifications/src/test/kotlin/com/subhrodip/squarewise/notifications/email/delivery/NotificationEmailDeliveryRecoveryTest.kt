package com.subhrodip.squarewise.notifications.email.delivery

import com.subhrodip.squarewise.notifications.consumer.service.NotificationEventConsumer
import com.subhrodip.squarewise.notifications.consumer.model.NotificationEvent
import com.subhrodip.squarewise.notifications.email.persistence.NotificationEmailDeliveryEntity
import com.subhrodip.squarewise.notifications.email.persistence.NotificationEmailDeliveryRepository
import com.subhrodip.squarewise.notifications.inbox.persistence.NotificationInboxEntity
import com.subhrodip.squarewise.notifications.inbox.persistence.NotificationInboxRepository
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.Optional
import java.util.UUID
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.doReturn
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.ArgumentCaptor
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.PageRequest

/** Verifies recovery rebuilds a notification event from durable inbox and lease state. */
class NotificationEmailDeliveryRecoveryTest {
    @Test
    fun `replays expired dispatching delivery`() {
        val notificationId = UUID.randomUUID()
        val deliveries = mock(NotificationEmailDeliveryRepository::class.java)
        val inbox = mock(NotificationInboxRepository::class.java)
        val consumer = mock(NotificationEventConsumer::class.java)
        val now = Instant.parse("2026-10-28T12:00:00Z")
        val delivery = NotificationEmailDeliveryEntity(
            notificationId = notificationId,
            status = NotificationEmailDeliveryEntity.DISPATCHING,
            recipientEmail = "alice@example.com",
            updatedAt = now.minusSeconds(301)
        )
        val item = NotificationInboxEntity(
            notificationId = notificationId,
            subject = "sqw:alice",
            eventType = "expense.updated",
            message = "Dinner changed",
            occurredAt = now.minusSeconds(600)
        )
        doReturn(listOf(delivery)).`when`(deliveries).findDispatchingBefore(
            any(Instant::class.java) ?: Instant.EPOCH,
            any(Pageable::class.java) ?: PageRequest.of(0, 1)
        )
        doReturn(Optional.of(item)).`when`(inbox).findById(notificationId)

        val recovered = NotificationEmailDeliveryRecovery(
            deliveries,
            inbox,
            consumer,
            Clock.fixed(now, ZoneOffset.UTC)
        ).recoverExpired()

        assertEquals(1, recovered)
        val event = ArgumentCaptor.forClass(NotificationEvent::class.java)
        verify(consumer).consume(
            event.capture() ?: NotificationEvent(
                UUID(0, 0), UUID(0, 0), "placeholder", "placeholder", "placeholder", Instant.EPOCH
            )
        )
        assertEquals("alice@example.com", event.value.recipientEmail)
    }
}
