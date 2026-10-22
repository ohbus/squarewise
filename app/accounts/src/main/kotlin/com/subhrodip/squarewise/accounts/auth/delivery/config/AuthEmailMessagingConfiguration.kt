package com.subhrodip.squarewise.accounts.auth.delivery.config

import com.subhrodip.squarewise.accounts.auth.delivery.service.AuthEmailOutboxPublisher
import com.subhrodip.squarewise.accounts.auth.delivery.service.AuthEmailOutboxService
import java.time.Duration
import org.springframework.amqp.rabbit.core.RabbitTemplate
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.annotation.EnableScheduling
import tools.jackson.databind.ObjectMapper

@Configuration
@EnableScheduling
@EnableConfigurationProperties(AuthEmailOutboxProperties::class)
class AuthEmailMessagingConfiguration {
    @Bean
    @ConditionalOnProperty(prefix = "squarewise.auth-email-outbox", name = ["enabled"], havingValue = "true")
    fun authEmailOutboxPublisher(outbox: AuthEmailOutboxService, rabbitTemplate: RabbitTemplate, objectMapper: ObjectMapper, properties: AuthEmailOutboxProperties): AuthEmailOutboxPublisher =
        AuthEmailOutboxPublisher(outbox, rabbitTemplate, objectMapper, properties.exchange, properties.routingKey, Duration.ofSeconds(properties.leaseSeconds), Duration.ofSeconds(properties.retryAfterSeconds), properties.maximumAttempts, properties.confirmTimeoutMs)
}
