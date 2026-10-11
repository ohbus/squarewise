package com.subhrodip.squarewise.bff.messaging.config

import com.subhrodip.squarewise.bff.messaging.service.BffEventConsumer
import com.subhrodip.squarewise.bff.messaging.persistence.BffEventDeduplicator
import com.subhrodip.squarewise.bff.messaging.transport.RabbitBffEventListener
import com.subhrodip.squarewise.bff.messaging.transport.DeadLetterPublisher

import com.subhrodip.squarewise.bff.realtime.LiveUpdateFanout
import org.springframework.amqp.core.AcknowledgeMode
import org.springframework.amqp.core.AnonymousQueue
import org.springframework.amqp.core.Binding
import org.springframework.amqp.core.BindingBuilder
import org.springframework.amqp.core.Queue
import org.springframework.amqp.core.TopicExchange
import org.springframework.amqp.rabbit.connection.ConnectionFactory
import org.springframework.amqp.rabbit.listener.SimpleMessageListenerContainer
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import tools.jackson.databind.ObjectMapper

@Configuration
@EnableConfigurationProperties(BffMessagingProperties::class)
class BffMessagingConfiguration {

    @Bean
    fun bffEventDeduplicator(properties: BffMessagingProperties): BffEventDeduplicator =
        BffEventDeduplicator(properties.deduplicatorCapacity)

    @Bean
    fun bffEventConsumer(
        fanout: LiveUpdateFanout,
        deduplicator: BffEventDeduplicator
    ): BffEventConsumer = BffEventConsumer(fanout, deduplicator)

    @Bean
    @ConditionalOnProperty(prefix = "squarewise.bff.messaging", name = ["enabled"], havingValue = "true")
    fun eventsTopicExchange(properties: BffMessagingProperties): TopicExchange =
        TopicExchange(properties.exchange, true, false)

    /**
     * Each BFF replica creates its own exclusive, auto-delete, temporary queue.
     * This guarantees that every BFF replica receives a distinct copy of every published group event.
     */
    @Bean
    @ConditionalOnProperty(prefix = "squarewise.bff.messaging", name = ["enabled"], havingValue = "true")
    fun bffReplicaQueue(): Queue = AnonymousQueue()

    @Bean
    @ConditionalOnProperty(prefix = "squarewise.bff.messaging", name = ["enabled"], havingValue = "true")
    fun bffReplicaBinding(
        bffReplicaQueue: Queue,
        eventsTopicExchange: TopicExchange,
        properties: BffMessagingProperties
    ): Binding = BindingBuilder
        .bind(bffReplicaQueue)
        .to(eventsTopicExchange)
        .with(properties.routingKey)

    @Bean
    @ConditionalOnProperty(prefix = "squarewise.bff.messaging", name = ["enabled"], havingValue = "true")
    fun rabbitBffEventListener(
        bffEventConsumer: BffEventConsumer,
        objectMapper: ObjectMapper,
        deadLetterPublisher: DeadLetterPublisher
    ): RabbitBffEventListener = RabbitBffEventListener(bffEventConsumer, objectMapper, deadLetterPublisher = deadLetterPublisher)

    @Bean
    @ConditionalOnProperty(prefix = "squarewise.bff.messaging", name = ["enabled"], havingValue = "true")
    fun bffMessageListenerContainer(
        connectionFactory: ConnectionFactory,
        bffReplicaQueue: Queue,
        rabbitBffEventListener: RabbitBffEventListener
    ): SimpleMessageListenerContainer = SimpleMessageListenerContainer().apply {
        setConnectionFactory(connectionFactory)
        setQueues(bffReplicaQueue)
        setMessageListener(rabbitBffEventListener)
        acknowledgeMode = AcknowledgeMode.MANUAL
        setAutoStartup(true)
    }
}
