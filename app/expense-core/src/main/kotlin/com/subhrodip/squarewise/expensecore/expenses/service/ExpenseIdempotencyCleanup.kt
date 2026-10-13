@file:Suppress("CanConvertToMultiDollarString")

package com.subhrodip.squarewise.expensecore.expenses.service

import com.subhrodip.squarewise.db.routing.DbWriter
import com.subhrodip.squarewise.expensecore.expenses.persistence.repository.ExpenseIdempotencyRepository

import java.time.Clock
import java.time.Duration
import java.time.Instant
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate

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
    private val maxBatches: Int = DEFAULT_MAX_BATCHES,
    @Autowired(required = false) transactionManager: PlatformTransactionManager? = null
) {
    private val transactionTemplate = transactionManager?.let { TransactionTemplate(it) }

    init {
        require(!retention.isNegative && !retention.isZero) { "idempotency retention must be positive" }
        require(batchSize > 0) { "idempotency cleanup batch size must be positive" }
        require(maxBatches > 0) { "idempotency cleanup max batches must be positive" }
    }

    /** Deletes claims older than the configured replay window across bounded batches. */
    @Scheduled(fixedDelayString = "\${squarewise.expense-idempotency.cleanup-delay-ms:3600000}")
    @DbWriter("expense.idempotency.cleanup")
    fun cleanupExpired(): Int {
        val cutoff: Instant = clock.instant().minus(retention)
        var totalDeleted = 0
        for (batch in 0 until maxBatches) {
            val deleted = transactionTemplate?.execute {
                repository.purgeOlderThan(cutoff, batchSize)
            } ?: repository.purgeOlderThan(cutoff, batchSize)
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
