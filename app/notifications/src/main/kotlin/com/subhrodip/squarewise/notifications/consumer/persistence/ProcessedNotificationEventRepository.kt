package com.subhrodip.squarewise.notifications.consumer.persistence

import org.springframework.data.jpa.repository.JpaRepository
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
}
