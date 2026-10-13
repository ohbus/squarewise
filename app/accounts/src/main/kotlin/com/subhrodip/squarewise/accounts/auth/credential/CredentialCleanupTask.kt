package com.subhrodip.squarewise.accounts.auth.credential

import java.time.Duration
import java.time.Instant
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionTemplate

/**
 * Scheduled background task that purges expired and consumed login credentials
 * to maintain bounded table growth and minimize stale credential retention.
 */
@Component
@ConditionalOnProperty(name = ["squarewise.auth.credential-cleanup-enabled"], havingValue = "true", matchIfMissing = true)
class CredentialCleanupTask(
    private val loginCredentialRepository: LoginCredentialRepository,
    @Value("\${squarewise.auth.credential-retention-expired-days:7}") private val expiredRetentionDays: Long = 7,
    @Value("\${squarewise.auth.credential-retention-consumed-hours:24}") private val consumedRetentionHours: Long = 24,
    @Value("\${squarewise.auth.credential-cleanup-batch-size:500}") private val batchSize: Int = 500,
    @Value("\${squarewise.auth.credential-cleanup-max-batches:50}") private val maxBatches: Int = 50,
    @Autowired(required = false) transactionManager: PlatformTransactionManager? = null
) {
    private val log = LoggerFactory.getLogger(CredentialCleanupTask::class.java)
    private val transactionTemplate = transactionManager?.let { TransactionTemplate(it) }

    init {
        require(expiredRetentionDays > 0) { "expired retention days must be positive" }
        require(consumedRetentionHours > 0) { "consumed retention hours must be positive" }
        require(batchSize > 0) { "credential cleanup batch size must be positive" }
        require(maxBatches > 0) { "credential cleanup max batches must be positive" }
    }

    /**
     * Purges expired credentials older than [expiredRetentionDays] days and consumed
     * credentials older than [consumedRetentionHours] hours across bounded batches.
     */
    @Scheduled(cron = "\${squarewise.auth.credential-cleanup-cron:0 0 * * * *}")
    fun executeCleanup(): Int {
        val now = Instant.now()
        val expiredCutoff = now.minus(Duration.ofDays(expiredRetentionDays))
        val consumedCutoff = now.minus(Duration.ofHours(consumedRetentionHours))

        var totalDeleted = 0
        for (batch in 0 until maxBatches) {
            val deletedCount = transactionTemplate?.execute {
                loginCredentialRepository.purgeExpiredOrConsumed(expiredCutoff, consumedCutoff, batchSize)
            } ?: loginCredentialRepository.purgeExpiredOrConsumed(expiredCutoff, consumedCutoff, batchSize)
            totalDeleted += deletedCount
            if (deletedCount < batchSize) break
        }
        if (totalDeleted > 0) {
            log.info("Purged {} expired or consumed auth login credentials (expiredCutoff={}, consumedCutoff={})", totalDeleted, expiredCutoff, consumedCutoff)
        }
        return totalDeleted
    }
}
