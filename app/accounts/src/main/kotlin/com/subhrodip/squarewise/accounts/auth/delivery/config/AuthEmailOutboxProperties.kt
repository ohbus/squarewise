package com.subhrodip.squarewise.accounts.auth.delivery.config

import org.springframework.boot.context.properties.ConfigurationProperties

/** Configuration for the Accounts-owned protected auth-email outbox publisher. */
@ConfigurationProperties(prefix = "squarewise.auth-email-outbox")
data class AuthEmailOutboxProperties(
    var enabled: Boolean = false,
    var exchange: String = "squarewise.events",
    var routingKey: String = "auth.email.requested.v1",
    var pollDelayMs: Long = 1000,
    var leaseSeconds: Long = 30,
    var retryAfterSeconds: Long = 5,
    var maximumAttempts: Int = 5,
    var confirmTimeoutMs: Long = 5000
)
