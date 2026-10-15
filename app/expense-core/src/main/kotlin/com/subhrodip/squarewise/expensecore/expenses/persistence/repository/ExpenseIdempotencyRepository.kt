package com.subhrodip.squarewise.expensecore.expenses.persistence.repository

import com.subhrodip.squarewise.expensecore.expenses.persistence.entity.ExpenseIdempotencyEntity
import java.util.UUID
import java.time.Instant
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Repository

/** Repository for durable expense mutation claims. */
@Repository
interface ExpenseIdempotencyRepository : JpaRepository<ExpenseIdempotencyEntity, UUID> {
    /** Finds a prior claim in the actor and group operation scope. */
    fun findByGroupIdAndActorSubjectAndOperationAndIdempotencyKey(
        groupId: UUID,
        actorSubject: String,
        operation: String,
        idempotencyKey: String
    ): ExpenseIdempotencyEntity?

    /** Returns at most one cleanup batch of claims older than [createdBefore]. */
    fun findByCreatedAtBefore(createdBefore: Instant, pageable: Pageable): List<ExpenseIdempotencyEntity>

    /** Purges expired idempotency claims older than [cutoff] in bounded batches directly in the database. */
    @Modifying
    @Query(
        value = """
            DELETE FROM expense_idempotency
            WHERE idempotency_id IN (
                SELECT idempotency_id FROM expense_idempotency
                WHERE created_at < :cutoff
                LIMIT :limit
            )
        """,
        nativeQuery = true
    )
    fun purgeOlderThan(
        @Param("cutoff") cutoff: Instant,
        @Param("limit") limit: Int
    ): Int
}

