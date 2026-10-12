package com.subhrodip.squarewise.notifications.email.persistence

import java.time.Instant
import java.util.UUID
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.transaction.annotation.Transactional

/** Repository for durable notification email delivery state. */
interface NotificationEmailDeliveryRepository : JpaRepository<NotificationEmailDeliveryEntity, UUID> {
    /** Claims a pending or expired dispatch lease for one notification. */
    @Modifying
    @Transactional
    @Query(
        value = """
            UPDATE notification_email_deliveries
               SET status = 'DISPATCHING', updated_at = :now
             WHERE notification_id = :notificationId
               AND (
                   status = 'PENDING'
                   OR (status = 'DISPATCHING' AND updated_at < :leaseCutoff)
               )
        """,
        nativeQuery = true
    )
    fun claimForDispatch(notificationId: UUID, now: Instant, leaseCutoff: Instant): Int
}
