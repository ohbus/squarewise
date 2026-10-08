@file:Suppress("CanConvertToMultiDollarString")

package com.subhrodip.squarewise.expensecore.messaging.config

import com.subhrodip.squarewise.expensecore.messaging.outbox.service.OutboxPublisher
import com.subhrodip.squarewise.expensecore.messaging.outbox.model.PublishBatchResult
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

/**
 * Background daemon polling available outbox messages and pumping them to the broker.
 * Gated by `squarewise.outbox.enabled=true` so tests and non-worker containers remain isolated.
 */
@Component
@ConditionalOnProperty(prefix = "squarewise.outbox", name = ["enabled"], havingValue = "true")
class OutboxRelayDaemon(
    private val publisher: OutboxPublisher
) {
    @Scheduled(fixedDelayString = "\${squarewise.outbox.poll-delay-ms:1000}")
    fun pollAndPublish(): PublishBatchResult {
        return publisher.publishAvailable()
    }
}
