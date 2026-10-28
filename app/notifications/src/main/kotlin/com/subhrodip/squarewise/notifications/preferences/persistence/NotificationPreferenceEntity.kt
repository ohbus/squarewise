package com.subhrodip.squarewise.notifications.preferences.persistence

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.Version

/**
 * JPA entity representing user delivery preferences for notifications.
 *
 * Maps to table `notification_preferences`.
 *
 * @property subject Authenticated subject or recipient identifier (1..256 characters).
 * @property emailEnabled Whether email delivery is enabled for the subject.
 * @property pushEnabled Whether push notifications are enabled for the subject.
 * @property version Optimistic locking version counter.
 */
@Entity
@Table(name = "notification_preferences")
class NotificationPreferenceEntity(
    @Id
    @Column(name = "subject", nullable = false, length = 256)
    var subject: String,
    @Column(name = "email_enabled", nullable = false)
    var emailEnabled: Boolean,
    @Column(name = "push_enabled", nullable = false)
    var pushEnabled: Boolean,
    @Version
    @Column(name = "version", nullable = false)
    var version: Long = 0
)
