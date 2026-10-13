@file:Suppress("CanConvertToMultiDollarString")

package com.subhrodip.squarewise.expensecore.expenses.service

import com.subhrodip.squarewise.expensecore.expenses.persistence.repository.ExpenseIdempotencyRepository

import java.time.Clock
import java.time.Duration
import java.time.Instant
import org.springframework.data.domain.PageRequest
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

/**
 * Reclaims expired idempotency claims in bounded batches.
 *
 * Claims remain replayable for [retention] and are then intentionally removed;
 * callers retrying after that window must supply a new mutation claim.
 */
@Component
class ExpenseIdempotencyCleanup(
    private val repository: ExpenseIdempotencyRepository,
    private val clock: Clock = Clock.systemUTC(),
    private val retention: Duration = DEFAULT_RETENTION,
    private val batchSize: Int = DEFAULT_BATCH_SIZE,
    private val maxBatches: Int = DEFAULT_MAX_BATCHES
) {
    init {
        require(!retention.isNegative && !retention.isZero) { "idempotency retention must be positive" }
        require(batchSize > 0) { "idempotency cleanup batch size must be positive" }
        require(maxBatches > 0) { "idempotency cleanup max batches must be positive" }
    }

    /** Deletes claims older than the configured replay window across bounded batches. */
    @Scheduled(fixedDelayString = "\${squarewise.expense-idempotency.cleanup-delay-ms:3600000}")
    @org.springframework.transaction.annotation.Transactional
    fun cleanupExpired(): Int {
        val cutoff: Instant = clock.instant().minus(retention)
        var totalDeleted = 0
        for (batch in 0 until maxBatches) {
            val deleted = repository.purgeOlderThan(cutoff, batchSize)
            totalDeleted += deleted
            if (deleted < batchSize) break
        }
        return totalDeleted
    }

    companion object {
        val DEFAULT_RETENTION: Duration = Duration.ofHours(24)
        const val DEFAULT_BATCH_SIZE: Int = 500
        const val DEFAULT_MAX_BATCHES: Int = 50
    }
}
