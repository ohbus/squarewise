package com.subhrodip.squarewise.notifications.consumer.service

import com.subhrodip.squarewise.notifications.consumer.config.NotificationMessagingProperties
import com.subhrodip.squarewise.notifications.consumer.persistence.ProcessedNotificationEventRepository
import java.time.Clock
import java.time.Duration
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.data.domain.PageRequest
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate

/**
 * Reclaims old notification deduplication markers in bounded batches.
 *
 * Markers remain for [retention] so broker replay stays idempotent. The retention
 * period must remain at least as long as the approved broker replay guarantee.
 *
 * @param repository durable processed-event marker store
 * @param properties configured replay retention and cleanup batch size
 * @param clock source used to calculate the cutoff
 * @param retention replay-retention override for deterministic tests
 * @param batchSize cleanup batch-size override for deterministic tests
 */
@Component
class ProcessedNotificationEventCleanup(
    private val repository: ProcessedNotificationEventRepository,
    properties: NotificationMessagingProperties,
    private val clock: Clock = Clock.systemUTC(),
    private val retention: Duration = properties.processedEventRetention,
    private val batchSize: Int = properties.processedEventCleanupBatchSize,
    private val maxBatches: Int = DEFAULT_MAX_BATCHES,
    @Autowired(required = false) transactionManager: PlatformTransactionManager? = null
) {
    private val transactionTemplate = transactionManager?.let { TransactionTemplate(it) }

    init {
        require(!retention.isNegative && !retention.isZero) {
            "processed event retention must be positive"
        }
        require(batchSize > 0) {
            "processed event cleanup batch size must be positive"
        }
        require(maxBatches > 0) {
            "processed event cleanup max batches must be positive"
        }
    }

    /** Deletes markers older than the configured replay window across bounded batches. */
    @Scheduled(fixedDelayString = "\${squarewise.notifications.processed-event-cleanup-delay-ms:3600000}")
    fun cleanupExpired(): Int {
        val cutoff = clock.instant().minus(retention)
        var totalDeleted = 0
        for (batch in 0 until maxBatches) {
            val deleted = transactionTemplate?.execute {
                repository.purgeProcessedOlderThan(cutoff, batchSize)
            } ?: repository.purgeProcessedOlderThan(cutoff, batchSize)
            totalDeleted += deleted
            if (deleted < batchSize) break
        }
        return totalDeleted
    }

    companion object {
        const val DEFAULT_MAX_BATCHES: Int = 50
    }
}
