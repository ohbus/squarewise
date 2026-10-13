package com.subhrodip.squarewise.expensecore.messaging.outbox.service

import com.subhrodip.squarewise.db.cleanup.AbstractRetentionCleanup
import com.subhrodip.squarewise.db.routing.DbWriter
import com.subhrodip.squarewise.expensecore.messaging.outbox.persistence.OutboxRepository
import java.time.Clock
import java.time.Duration
import java.time.Instant
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.transaction.PlatformTransactionManager

/**
 * Scheduled background task that purges published outbox events older than the retention threshold
 * in bounded batches, keeping the outbox table bounded at high scale.
 */
@Component
@ConditionalOnProperty(prefix = "squarewise.outbox", name = ["cleanup-enabled"], havingValue = "true", matchIfMissing = true)
class OutboxRetentionCleanup(
    private val repository: OutboxRepository,
    clock: Clock = Clock.systemUTC(),
    retention: Duration = DEFAULT_RETENTION,
    batchSize: Int = DEFAULT_BATCH_SIZE,
    maxBatches: Int = DEFAULT_MAX_BATCHES,
    @Autowired(required = false) transactionManager: PlatformTransactionManager? = null
) : AbstractRetentionCleanup(
    clock = clock,
    retention = retention,
    batchSize = batchSize,
    maxBatches = maxBatches,
    transactionManager = transactionManager,
    subjectName = "outbox"
) {
    /**
     * Purges published messages older than [retention] across bounded batches.
     *
     * @return total count of purged outbox records
     */
    @Scheduled(fixedDelayString = "\${squarewise.outbox.cleanup-delay-ms:3600000}")
    @DbWriter("outbox.cleanup")
    fun cleanupPublished(): Int = executeBatchCleanup()

    override fun purgeBatch(cutoff: Instant, limit: Int): Int =
        repository.purgePublishedOlderThan(cutoff, limit)

    companion object {
        val DEFAULT_RETENTION: Duration = AbstractRetentionCleanup.DEFAULT_RETENTION
        const val DEFAULT_BATCH_SIZE: Int = AbstractRetentionCleanup.DEFAULT_BATCH_SIZE
        const val DEFAULT_MAX_BATCHES: Int = AbstractRetentionCleanup.DEFAULT_MAX_BATCHES
    }
}
