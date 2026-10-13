package com.subhrodip.squarewise.accounts.auth.session

import java.time.Duration
import java.time.Instant
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

/**
 * Scheduled background task that purges expired and revoked authentication sessions
 * in bounded batches to maintain bounded table growth at scale.
 */
@Component
@ConditionalOnProperty(name = ["squarewise.auth.session-cleanup-enabled"], havingValue = "true", matchIfMissing = true)
class AuthSessionCleanupTask(
    private val authSessionRepository: AuthSessionRepository,
    @Value("\${squarewise.auth.session-retention-days:30}") private val retentionDays: Long = 30,
    @Value("\${squarewise.auth.session-cleanup-batch-size:500}") private val batchSize: Int = 500,
    @Value("\${squarewise.auth.session-cleanup-max-batches:50}") private val maxBatches: Int = 50
) {
    private val log = LoggerFactory.getLogger(AuthSessionCleanupTask::class.java)

    init {
        require(retentionDays > 0) { "session retention days must be positive" }
        require(batchSize > 0) { "session cleanup batch size must be positive" }
        require(maxBatches > 0) { "session cleanup max batches must be positive" }
    }

    /**
     * Purges expired or revoked sessions older than [retentionDays] days across bounded batches.
     */
    @Scheduled(cron = "\${squarewise.auth.session-cleanup-cron:0 30 * * * *}")
    @Transactional
    fun executeCleanup(): Int {
        val cutoff = Instant.now().minus(Duration.ofDays(retentionDays))
        var totalDeleted = 0
        for (batch in 0 until maxBatches) {
            val deletedCount = authSessionRepository.purgeExpiredOrRevokedOlderThan(cutoff, batchSize)
            totalDeleted += deletedCount
            if (deletedCount < batchSize) break
        }
        if (totalDeleted > 0) {
            log.info("Purged {} expired/revoked auth sessions (cutoff={})", totalDeleted, cutoff)
        }
        return totalDeleted
    }
}
