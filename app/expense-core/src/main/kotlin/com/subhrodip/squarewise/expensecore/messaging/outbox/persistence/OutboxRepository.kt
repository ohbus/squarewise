package com.subhrodip.squarewise.expensecore.messaging.outbox.persistence

import jakarta.persistence.LockModeType
import java.util.UUID
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository

/**
 * Spring Data JPA repository for [OutboxEntity].
 *
 * Provides pessimistic locking queries for acknowledging and rejecting outbox entries.
 */
@Repository
interface OutboxRepository : JpaRepository<OutboxEntity, UUID> {
    /**
     * Finds an outbox entity by event ID with a pessimistic write lock for safe concurrent status update.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select message from OutboxEntity message where message.eventId = :eventId")
    fun findForUpdate(@Param("eventId") eventId: UUID): OutboxEntity?

    /**
     * Retrieves all outbox entities ordered chronologically by occurrence time and event ID.
     */
    fun findAllByOrderByOccurredAtAscEventIdAsc(): List<OutboxEntity>
}
