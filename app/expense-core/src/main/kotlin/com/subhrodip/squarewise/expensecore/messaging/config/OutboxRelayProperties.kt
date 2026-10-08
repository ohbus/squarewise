package com.subhrodip.squarewise.expensecore.messaging.config

import org.springframework.boot.context.properties.ConfigurationProperties

/** Configuration properties governing transactional outbox relay behavior. */
@ConfigurationProperties(prefix = "squarewise.outbox")
data class OutboxRelayProperties(
    var enabled: Boolean = false,
    var rabbitEnabled: Boolean = false,
    var batchSize: Int = 100,
    var pollDelayMs: Long = 1000,
    var leaseSeconds: Long = 30,
    var maxAttempts: Int = 5,
    var retryAfterSeconds: Long = 5
)
