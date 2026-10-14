package com.subhrodip.squarewise.accounts.auth.delivery.outbox

import com.subhrodip.squarewise.db.cleanup.AbstractRetentionCleanup
import com.subhrodip.squarewise.db.routing.DbWriter
import java.time.Clock
import java.time.Duration
import java.time.Instant
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.transaction.PlatformTransactionManager

/**
 * Scheduled background task that purges published auth-email outbox records
 * older than the retention window in bounded batches.
 */
@Component
@ConditionalOnProperty(prefix = "squarewise.auth-email-outbox", name = ["cleanup-enabled"], havingValue = "true", matchIfMissing = true)
class AuthEmailOutboxRetentionCleanup(
    private val repository: AuthEmailOutboxRepository,
    @Autowired(required = false) clock: Clock? = null,
    @Value("\${squarewise.auth-email-outbox.retention:P7D}") retention: Duration = DEFAULT_RETENTION,
    @Value("\${squarewise.auth-email-outbox.cleanup-batch-size:500}") batchSize: Int = DEFAULT_BATCH_SIZE,
    @Value("\${squarewise.auth-email-outbox.cleanup-max-batches:50}") maxBatches: Int = DEFAULT_MAX_BATCHES,
    @Autowired(required = false) transactionManager: PlatformTransactionManager? = null
) : AbstractRetentionCleanup(
    clock = clock ?: Clock.systemUTC(),
    retention = retention,
    batchSize = batchSize,
    maxBatches = maxBatches,
    transactionManager = transactionManager,
    subjectName = "auth-email outbox"
) {
    /**
     * Purges published messages older than [retention] across bounded batches.
     *
     * @return total count of purged auth-email outbox records
     */
    @Scheduled(fixedDelayString = "\${squarewise.auth-email-outbox.cleanup-delay-ms:3600000}")
    @DbWriter("auth.email-outbox.cleanup")
    fun cleanupPublished(): Int = executeBatchCleanup()

    override fun purgeBatch(cutoff: Instant, limit: Int): Int =
        repository.purgePublishedOlderThan(cutoff, limit)

    companion object {
        val DEFAULT_RETENTION: Duration = AbstractRetentionCleanup.DEFAULT_RETENTION
        const val DEFAULT_BATCH_SIZE: Int = AbstractRetentionCleanup.DEFAULT_BATCH_SIZE
        const val DEFAULT_MAX_BATCHES: Int = AbstractRetentionCleanup.DEFAULT_MAX_BATCHES
    }
}
