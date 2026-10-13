package com.subhrodip.squarewise.db.cleanup

import java.time.Clock
import java.time.Duration
import java.time.Instant
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate

/**
 * Base implementation for scheduled background tasks that purge expired or published records
 * older than a retention threshold in bounded transactional batches.
 *
 * @param clock clock used to determine the cutoff timestamp
 * @param retention age threshold beyond which records are considered eligible for purge
 * @param batchSize maximum number of records purged in a single batch
 * @param maxBatches maximum number of batches executed in a single run
 * @param transactionManager optional transaction manager to wrap each batch in an isolated transaction
 * @param subjectName label used for validation error messages
 */
abstract class AbstractRetentionCleanup(
    private val clock: Clock = Clock.systemUTC(),
    private val retention: Duration = DEFAULT_RETENTION,
    private val batchSize: Int = DEFAULT_BATCH_SIZE,
    private val maxBatches: Int = DEFAULT_MAX_BATCHES,
    transactionManager: PlatformTransactionManager? = null,
    subjectName: String = "retention"
) {
    private val transactionTemplate = transactionManager?.let { TransactionTemplate(it) }

    init {
        require(!retention.isNegative && !retention.isZero) { "$subjectName retention must be positive" }
        require(batchSize > 0) { "$subjectName cleanup batch size must be positive" }
        require(maxBatches > 0) { "$subjectName cleanup max batches must be positive" }
    }

    /**
     * Purges records older than [retention] across bounded batches by delegating each batch to [purgeBatch].
     *
     * @return total number of records deleted across all batches
     */
    protected fun executeBatchCleanup(): Int {
        val cutoff: Instant = clock.instant().minus(retention)
        var totalDeleted = 0
        for (batch in 0 until maxBatches) {
            val deleted = transactionTemplate?.execute {
                purgeBatch(cutoff, batchSize)
            } ?: purgeBatch(cutoff, batchSize)
            totalDeleted += deleted
            if (deleted < batchSize) break
        }
        return totalDeleted
    }

    /**
     * Executes a single batch purge operation for records older than [cutoff] with limit [limit].
     *
     * @param cutoff oldest timestamp boundary for retention
     * @param limit maximum records to delete in this batch
     * @return number of records actually deleted
     */
    protected abstract fun purgeBatch(cutoff: Instant, limit: Int): Int

    companion object {
        val DEFAULT_RETENTION: Duration = Duration.ofDays(7)
        const val DEFAULT_BATCH_SIZE: Int = 500
        const val DEFAULT_MAX_BATCHES: Int = 50
    }
}
