package com.subhrodip.squarewise.notifications.email

import com.subhrodip.squarewise.notifications.email.persistence.NotificationEmailDeliveryEntity
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID

/** Verifies durable email-delivery state is readable and mutable for JPA lifecycle use. */
class NotificationEmailDeliveryEntityTest {
    @Test
    fun `exposes all delivery state fields`() {
        val notificationId = UUID.randomUUID()
        val createdAt = Instant.parse("2026-10-01T00:00:00Z")
        val updatedAt = createdAt.plusSeconds(30)
        val entity = NotificationEmailDeliveryEntity(notificationId, createdAt = createdAt)

        entity.status = NotificationEmailDeliveryEntity.DELIVERED
        entity.updatedAt = updatedAt

        assertEquals(notificationId, entity.notificationId)
        assertEquals(NotificationEmailDeliveryEntity.DELIVERED, entity.status)
        assertEquals(createdAt, entity.createdAt)
        assertEquals(updatedAt, entity.updatedAt)
    }
}
