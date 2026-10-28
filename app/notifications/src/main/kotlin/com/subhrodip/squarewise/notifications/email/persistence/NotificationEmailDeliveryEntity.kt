package com.subhrodip.squarewise.notifications.email.persistence

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

/** Durable email-delivery state used to retry broker redeliveries safely. */
@Entity
@Table(name = "notification_email_deliveries")
class NotificationEmailDeliveryEntity(
    @Id
    @Column(name = "notification_id", nullable = false)
    var notificationId: UUID,
    @Column(name = "status", nullable = false, length = 16)
    var status: String = PENDING,
    @Column(name = "recipient_email", length = 254)
    var recipientEmail: String? = null,
    @Column(name = "created_at", nullable = false)
    var createdAt: Instant = Instant.now(),
    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = createdAt,
) {
    companion object {
        const val PENDING = "PENDING"
        const val DISPATCHING = "DISPATCHING"
        const val DELIVERED = "DELIVERED"
        const val SKIPPED = "SKIPPED"
    }
}
