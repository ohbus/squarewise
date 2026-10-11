package com.subhrodip.squarewise.bff.messaging.config

import com.subhrodip.squarewise.bff.messaging.service.BffEventConsumer
import com.subhrodip.squarewise.bff.messaging.transport.RabbitBffEventListener
import com.subhrodip.squarewise.bff.messaging.transport.DeadLetterPublisher
import com.subhrodip.squarewise.bff.realtime.LiveUpdateFanout
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.springframework.amqp.core.AcknowledgeMode
import org.springframework.amqp.rabbit.connection.ConnectionFactory
import tools.jackson.databind.ObjectMapper

/**
 * Verifies the BFF RabbitMQ bean graph preserves replica isolation and explicit
 * acknowledgement semantics before a broker-backed integration test is run.
 */
class BffMessagingConfigurationTest {
    private val configuration = BffMessagingConfiguration()
    private val properties = BffMessagingProperties(
        enabled = true,
        exchange = "squarewise.events.test",
        routingKey = "group.#"
    )

    /** Verifies exchange, temporary queue, and binding invariants from configuration properties. */
    @Test
    fun `builds durable exchange and replica-specific routing`() {
        val exchange = configuration.eventsTopicExchange(properties)
        val queue = configuration.bffReplicaQueue()
        val binding = configuration.bffReplicaBinding(queue, exchange, properties)

        assertThat(exchange.name).isEqualTo("squarewise.events.test")
        assertThat(exchange.isDurable).isTrue()
        assertThat(exchange.isAutoDelete).isFalse()
        assertThat(queue.isExclusive).isTrue()
        assertThat(queue.isAutoDelete).isTrue()
        assertThat(binding.exchange).isEqualTo(exchange.name)
        assertThat(binding.destination).isEqualTo(queue.name)
        assertThat(binding.routingKey).isEqualTo("group.#")
    }

    /** Verifies the listener container requires manual acknowledgement and the event adapter. */
    @Test
    fun `builds manually acknowledged listener container`() {
        val consumer = configuration.bffEventConsumer(LiveUpdateFanout(), configuration.bffEventDeduplicator(properties))
        val listener = configuration.rabbitBffEventListener(
            consumer,
            mock(ObjectMapper::class.java),
            DeadLetterPublisher { }
        )
        val queue = configuration.bffReplicaQueue()
        val container = configuration.bffMessageListenerContainer(
            mock(ConnectionFactory::class.java),
            queue,
            listener
        )

        assertThat(listener).isInstanceOf(RabbitBffEventListener::class.java)
        assertThat(container.acknowledgeMode).isEqualTo(AcknowledgeMode.MANUAL)
        assertThat(container.isAutoStartup).isTrue()
        assertThat(container.queueNames).containsExactly(queue.name)
        assertThat(container.messageListener).isSameAs(listener)
        assertThat(consumer).isInstanceOf(BffEventConsumer::class.java)
    }
}
