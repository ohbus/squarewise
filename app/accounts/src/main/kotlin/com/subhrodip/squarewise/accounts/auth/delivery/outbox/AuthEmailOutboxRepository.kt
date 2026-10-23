package com.subhrodip.squarewise.accounts.auth.delivery.outbox

import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.repository.query.Param

import java.util.UUID
import java.time.Instant
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query

/** Persistence port for Accounts-owned authentication-email delivery records. */
interface AuthEmailOutboxRepository : JpaRepository<AuthEmailOutboxEntity, UUID> {
    /** Finds a delivery record by its idempotent event identifier. */
    fun findByEventId(eventId: UUID): AuthEmailOutboxEntity?

    /** Finds one available delivery while holding a database row lock. */
    @Query(
        value = """
            SELECT * FROM auth_email_outbox
            WHERE (status = 'PENDING' OR (status = 'CLAIMED' AND available_at <= :now))
              AND available_at <= :now
            ORDER BY created_at ASC
            LIMIT 1 FOR UPDATE SKIP LOCKED
        """,
        nativeQuery = true
    )
    fun findAvailableForClaim(@Param("now") now: Instant): AuthEmailOutboxEntity?

    /** Acknowledges only the currently claimed event. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update AuthEmailOutboxEntity e set e.status = 'PUBLISHED', e.availableAt = :now where e.eventId = :eventId and e.status = 'CLAIMED'")
    fun acknowledge(@Param("eventId") eventId: UUID, @Param("now") now: Instant): Int

    /** Returns a claimed event to retry or parks it after the attempt limit. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update AuthEmailOutboxEntity e set e.status = case when e.attempts >= :maximumAttempts then 'PARKED' else 'PENDING' end, e.availableAt = :availableAt where e.eventId = :eventId and e.status = 'CLAIMED'")
    fun reject(@Param("eventId") eventId: UUID, @Param("maximumAttempts") maximumAttempts: Int, @Param("availableAt") availableAt: Instant): Int

    /**
     * Purges published auth-email outbox events older than [cutoff] in bounded batches.
     */
    @Modifying
    @Query(
        value = """
            DELETE FROM auth_email_outbox
            WHERE event_id IN (
                SELECT event_id FROM auth_email_outbox
                WHERE status = 'PUBLISHED' AND created_at < :cutoff
                LIMIT :limit
            )
        """,
        nativeQuery = true
    )
    fun purgePublishedOlderThan(@Param("cutoff") cutoff: Instant, @Param("limit") limit: Int): Int
}
