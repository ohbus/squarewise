@file:Suppress("CanConvertToMultiDollarString")

package com.subhrodip.squarewise.accounts.auth.delivery.outbox

import java.time.Clock
import java.time.Duration
import java.time.Instant
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

/**
 * Scheduled background task that purges published auth-email outbox records
 * older than the retention window in bounded batches.
 */
@Component
@ConditionalOnProperty(prefix = "squarewise.auth-email-outbox", name = ["cleanup-enabled"], havingValue = "true", matchIfMissing = true)
class AuthEmailOutboxRetentionCleanup(
    private val repository: AuthEmailOutboxRepository,
    private val clock: Clock = Clock.systemUTC(),
    private val retention: Duration = DEFAULT_RETENTION,
    private val batchSize: Int = DEFAULT_BATCH_SIZE,
    private val maxBatches: Int = DEFAULT_MAX_BATCHES
) {
    init {
        require(!retention.isNegative && !retention.isZero) { "auth-email outbox retention must be positive" }
        require(batchSize > 0) { "auth-email outbox cleanup batch size must be positive" }
        require(maxBatches > 0) { "auth-email outbox cleanup max batches must be positive" }
    }

    /**
     * Purges published messages older than [retention] across bounded batches.
     */
    @Scheduled(fixedDelayString = "\${squarewise.auth-email-outbox.cleanup-delay-ms:3600000}")
    @Transactional
    fun cleanupPublished(): Int {
        val cutoff: Instant = clock.instant().minus(retention)
        var totalDeleted = 0
        for (batch in 0 until maxBatches) {
            val deleted = repository.purgePublishedOlderThan(cutoff, batchSize)
            totalDeleted += deleted
            if (deleted < batchSize) break
        }
        return totalDeleted
    }

    companion object {
        val DEFAULT_RETENTION: Duration = Duration.ofDays(7)
        const val DEFAULT_BATCH_SIZE: Int = 500
        const val DEFAULT_MAX_BATCHES: Int = 50
    }
}
