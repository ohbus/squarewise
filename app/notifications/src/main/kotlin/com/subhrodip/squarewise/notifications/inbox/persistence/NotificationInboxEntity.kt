package com.subhrodip.squarewise.notifications.inbox.persistence

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

/**
 * JPA entity representing a notification item stored in a recipient's inbox.
 *
 * Maps to table `notification_inbox_items` and supports cursor-based pagination
 * ordered by [occurredAt] descending and [notificationId] descending.
 *
 * @property notificationId Unique identifier of the notification.
 * @property subject Authenticated subject or recipient identifier (1..256 characters).
 * @property eventType Event category or type (1..200 characters).
 * @property message User-facing notification message text (1..2000 characters).
 * @property occurredAt Instant at which the source event occurred.
 * @property read Whether this notification has been marked as read by the recipient.
 */
@Entity
@Table(name = "notification_inbox_items")
class NotificationInboxEntity(
    @Id
    @Column(name = "notification_id", nullable = false)
    var notificationId: UUID,
    @Column(name = "subject", nullable = false, length = 256)
    var subject: String,
    @Column(name = "event_type", nullable = false, length = 200)
    var eventType: String,
    @Column(name = "message", nullable = false, length = 2000)
    var message: String,
    @Column(name = "occurred_at", nullable = false)
    var occurredAt: Instant,
    @Column(name = "is_read", nullable = false)
    var read: Boolean = false
)
