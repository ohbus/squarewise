package com.subhrodip.squarewise.notifications.email.delivery

import com.subhrodip.squarewise.notifications.email.persistence.NotificationEmailDeliveryRepository
import java.time.Clock
import java.time.Duration
import java.time.Instant
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

/**
 * Scheduled background task that purges delivered or skipped notification email delivery records
 * older than the retention threshold in bounded batches, keeping the table bounded at scale.
 */
@Component
@ConditionalOnProperty(prefix = "squarewise.notifications.email-deliveries", name = ["cleanup-enabled"], havingValue = "true", matchIfMissing = true)
class NotificationEmailDeliveryRetentionCleanup(
    private val repository: NotificationEmailDeliveryRepository,
    private val clock: Clock = Clock.systemUTC(),
    @Value("\${squarewise.notifications.email-delivery-retention-days:30}") retentionDays: Long = 30,
    @Value("\${squarewise.notifications.email-delivery-cleanup-batch-size:500}") private val batchSize: Int = DEFAULT_BATCH_SIZE,
    @Value("\${squarewise.notifications.email-delivery-cleanup-max-batches:50}") private val maxBatches: Int = DEFAULT_MAX_BATCHES
) {
    private val log = LoggerFactory.getLogger(NotificationEmailDeliveryRetentionCleanup::class.java)
    private val retention: Duration = Duration.ofDays(retentionDays)

    init {
        require(!retention.isNegative && !retention.isZero) { "email delivery retention must be positive" }
        require(batchSize > 0) { "email delivery cleanup batch size must be positive" }
        require(maxBatches > 0) { "email delivery cleanup max batches must be positive" }
    }

    /** Purges completed email delivery records older than [retention] across bounded batches. */
    @Scheduled(fixedDelayString = "\${squarewise.notifications.email-delivery-cleanup-delay-ms:3600000}")
    @Transactional
    fun cleanupCompleted(): Int {
        val cutoff: Instant = clock.instant().minus(retention)
        var totalDeleted = 0
        for (batch in 0 until maxBatches) {
            val deleted = repository.purgeDeliveredOrSkippedOlderThan(cutoff, batchSize)
            totalDeleted += deleted
            if (deleted < batchSize) break
        }
        if (totalDeleted > 0) {
            log.info("Purged {} completed notification email delivery records (cutoff={})", totalDeleted, cutoff)
        }
        return totalDeleted
    }

    companion object {
        const val DEFAULT_BATCH_SIZE: Int = 500
        const val DEFAULT_MAX_BATCHES: Int = 50
    }
}
