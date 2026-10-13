package com.subhrodip.squarewise.notifications.consumer.persistence

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Repository
import java.time.Instant
import java.util.UUID

/**
 * Spring Data JPA repository for [ProcessedNotificationEventEntity] tracking and deduplication queries.
 */
@Repository
interface ProcessedNotificationEventRepository : JpaRepository<ProcessedNotificationEventEntity, UUID> {
    /** Finds one bounded batch of deduplication markers older than the replay retention window. */
    fun findByProcessedAtBefore(processedAt: Instant, pageable: Pageable): List<ProcessedNotificationEventEntity>

    /** Purges notification deduplication markers older than [cutoff] in bounded batches directly in the database. */
    @Modifying
    @Query(
        value = """
            DELETE FROM notification_processed_events
            WHERE event_id IN (
                SELECT event_id FROM notification_processed_events
                WHERE processed_at < :cutoff
                LIMIT :limit
            )
        """,
        nativeQuery = true
    )
    fun purgeProcessedOlderThan(
        @Param("cutoff") cutoff: Instant,
        @Param("limit") limit: Int
    ): Int
}

