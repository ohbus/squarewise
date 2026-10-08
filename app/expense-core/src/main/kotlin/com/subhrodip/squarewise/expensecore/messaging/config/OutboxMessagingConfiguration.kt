package com.subhrodip.squarewise.expensecore.messaging.config

import org.springframework.amqp.rabbit.core.RabbitTemplate
import tools.jackson.databind.ObjectMapper
import com.subhrodip.squarewise.expensecore.messaging.broker.BrokerPublisher
import com.subhrodip.squarewise.expensecore.messaging.broker.RabbitBrokerPublisher
import com.subhrodip.squarewise.expensecore.messaging.outbox.service.OutboxPublisher
import com.subhrodip.squarewise.expensecore.messaging.outbox.persistence.OutboxStore
import java.time.Duration
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.annotation.EnableScheduling

/** Wires the broker and outbox publisher adapters. */
@Configuration
@EnableScheduling
@EnableConfigurationProperties(OutboxRelayProperties::class)
class OutboxMessagingConfiguration {
    @Bean
    @ConditionalOnProperty(prefix = "squarewise.outbox", name = ["rabbit-enabled"], havingValue = "true")
    fun rabbitBrokerPublisher(
        rabbitTemplate: RabbitTemplate,
        objectMapper: ObjectMapper
    ): BrokerPublisher = RabbitBrokerPublisher(rabbitTemplate, objectMapper)

    @Bean
    @ConditionalOnProperty(prefix = "squarewise.outbox", name = ["enabled"], havingValue = "true")
    fun outboxPublisher(
        relay: OutboxStore,
        brokerPublisher: BrokerPublisher,
        objectMapper: ObjectMapper,
        properties: OutboxRelayProperties
    ): OutboxPublisher = OutboxPublisher(
        relay = relay,
        publisher = brokerPublisher,
        objectMapper = objectMapper,
        batchSize = properties.batchSize,
        lease = Duration.ofSeconds(properties.leaseSeconds),
        maxAttempts = properties.maxAttempts,
        retryAfter = Duration.ofSeconds(properties.retryAfterSeconds)
    )
}
