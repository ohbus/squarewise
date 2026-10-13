@file:Suppress("CanConvertToMultiDollarString")

package com.subhrodip.squarewise.expensecore.messaging.outbox.service

import com.subhrodip.squarewise.expensecore.messaging.outbox.persistence.OutboxRepository
import java.time.Clock
import java.time.Duration
import java.time.Instant
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

/**
 * Scheduled background task that purges published outbox events older than the retention threshold
 * in bounded batches, keeping the outbox table bounded at high scale.
 */
@Component
@ConditionalOnProperty(prefix = "squarewise.outbox", name = ["cleanup-enabled"], havingValue = "true", matchIfMissing = true)
class OutboxRetentionCleanup(
    private val repository: OutboxRepository,
    private val clock: Clock = Clock.systemUTC(),
    private val retention: Duration = DEFAULT_RETENTION,
    private val batchSize: Int = DEFAULT_BATCH_SIZE
) {
    init {
        require(!retention.isNegative && !retention.isZero) { "outbox retention must be positive" }
        require(batchSize > 0) { "outbox cleanup batch size must be positive" }
    }

    /**
     * Purges at most [batchSize] published messages older than [retention].
     */
    @Scheduled(fixedDelayString = "\${squarewise.outbox.cleanup-delay-ms:3600000}")
    @Transactional
    fun cleanupPublished(): Int {
        val cutoff: Instant = clock.instant().minus(retention)
        return repository.purgePublishedOlderThan(cutoff, batchSize)
    }

    companion object {
        val DEFAULT_RETENTION: Duration = Duration.ofDays(7)
        const val DEFAULT_BATCH_SIZE: Int = 500
    }
}
