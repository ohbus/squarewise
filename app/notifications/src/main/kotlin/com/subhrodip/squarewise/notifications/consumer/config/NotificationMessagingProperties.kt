package com.subhrodip.squarewise.notifications.consumer.config

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

/** Configuration properties for notification and authentication-email broker resources. */
@ConfigurationProperties(prefix = "squarewise.notifications")
data class NotificationMessagingProperties(
    var queue: String = "squarewise.notifications.v2",
    var authEmailQueue: String = "squarewise.auth-email.v2",
    var deadLetterExchange: String = "squarewise.events.dlx",
    var deadLetterQueue: String = "squarewise.notifications.v2.dlq",
    var authEmailDeadLetterQueue: String = "squarewise.auth-email.v2.dlq",
    var deliveryMaxPermits: Int = 10,
    var deliveryWindowSeconds: Long = 60,
    var processedEventRetention: Duration = Duration.ofDays(30),
    var processedEventCleanupBatchSize: Int = 500
) {
    init {
        require(deliveryMaxPermits in 1..1_000_000) {
            "delivery-max-permits must be between 1 and 1000000"
        }
        require(deliveryWindowSeconds in 1..86_400) {
            "delivery-window-seconds must be between 1 and 86400"
        }
        require(!processedEventRetention.isNegative && !processedEventRetention.isZero) {
            "processed-event-retention must be positive"
        }
        require(processedEventCleanupBatchSize > 0) {
            "processed-event-cleanup-batch-size must be positive"
        }
    }
}
