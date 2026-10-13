@file:Suppress("CanConvertToMultiDollarString")

package com.subhrodip.squarewise.accounts.auth.delivery.outbox

import com.subhrodip.squarewise.accounts.auth.delivery.model.AuthEmailPublishOutcome
import com.subhrodip.squarewise.accounts.auth.delivery.service.AuthEmailOutboxPublisher
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

/** Polls the protected auth-email outbox only when explicitly enabled. */
@Component
@ConditionalOnProperty(prefix = "squarewise.auth-email-outbox", name = ["enabled"], havingValue = "true")
class AuthEmailOutboxRelay(
    private val publisher: AuthEmailOutboxPublisher,
    @Value("\${squarewise.auth-email-outbox.max-batch-size:100}") private val maxBatchSize: Int = 100
) {
    init {
        require(maxBatchSize > 0) { "maxBatchSize must be positive" }
    }

    /**
     * Polls and drains available authentication-email events in a bounded loop.
     * Returns the outcome of the last attempted publish or EMPTY if no items were available.
     */
    @Scheduled(fixedDelayString = "\${squarewise.auth-email-outbox.poll-delay-ms:1000}")
    fun poll(): AuthEmailPublishOutcome {
        var lastOutcome = AuthEmailPublishOutcome.EMPTY
        for (i in 0 until maxBatchSize) {
            val outcome = publisher.publishOne()
            if (outcome == AuthEmailPublishOutcome.EMPTY) break
            lastOutcome = outcome
            if (outcome != AuthEmailPublishOutcome.PUBLISHED) break
        }
        return lastOutcome
    }
}
