package com.subhrodip.squarewise.expensecore.messaging.config

import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile

/** Fails deployed profiles closed when durable event publishing is disabled. */
@Configuration
@Profile("production", "staging", "local-oidc")
class RequiredOutboxConfiguration(properties: OutboxRelayProperties) {
    init {
        require(properties.enabled) { "squarewise.outbox.enabled must be true in deployed profiles" }
        require(properties.rabbitEnabled) { "squarewise.outbox.rabbit-enabled must be true in deployed profiles" }
    }
}
