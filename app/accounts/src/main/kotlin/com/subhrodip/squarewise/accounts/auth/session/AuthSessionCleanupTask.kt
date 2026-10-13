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
    @Value("\${squarewise.auth.session-cleanup-batch-size:500}") private val batchSize: Int = 500
) {
    private val log = LoggerFactory.getLogger(AuthSessionCleanupTask::class.java)

    init {
        require(retentionDays > 0) { "session retention days must be positive" }
        require(batchSize > 0) { "session cleanup batch size must be positive" }
    }

    /**
     * Purges expired or revoked sessions older than [retentionDays] days.
     */
    @Scheduled(cron = "\${squarewise.auth.session-cleanup-cron:0 30 * * * *}")
    @Transactional
    fun executeCleanup(): Int {
        val cutoff = Instant.now().minus(Duration.ofDays(retentionDays))
        val deletedCount = authSessionRepository.purgeExpiredOrRevokedOlderThan(cutoff, batchSize)
        if (deletedCount > 0) {
            log.info("Purged {} expired/revoked auth sessions (cutoff={})", deletedCount, cutoff)
        }
        return deletedCount
    }
}
