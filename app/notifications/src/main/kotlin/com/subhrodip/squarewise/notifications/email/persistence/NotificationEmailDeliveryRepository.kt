package com.subhrodip.squarewise.notifications.email.persistence

import java.time.Instant
import java.util.UUID
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
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

    /**
     * Updates the status only while the caller still owns the dispatch lease.
     *
     * @return one when the lease owner updated the row, otherwise zero.
     */
    @Modifying
    @Transactional
    @Query(
        value = """
            UPDATE notification_email_deliveries
               SET status = :status, updated_at = :now
             WHERE notification_id = :notificationId
               AND status = 'DISPATCHING'
               AND updated_at = :claimAt
        """,
        nativeQuery = true
    )
    fun updateStatusIfClaimed(
        @Param("notificationId") notificationId: UUID,
        @Param("status") status: String,
        @Param("now") now: Instant,
        @Param("claimAt") claimAt: Instant
    ): Int

    /**
     * Purges delivered or skipped email delivery records older than [cutoff] in bounded batches.
     */
    @Modifying
    @Query(
        value = """
            DELETE FROM notification_email_deliveries
            WHERE notification_id IN (
                SELECT notification_id FROM notification_email_deliveries
                WHERE (status = 'DELIVERED' OR status = 'SKIPPED')
                  AND updated_at < :cutoff
                LIMIT :limit
            )
        """,
        nativeQuery = true
    )
    fun purgeDeliveredOrSkippedOlderThan(
        @Param("cutoff") cutoff: Instant,
        @Param("limit") limit: Int
    ): Int
}
