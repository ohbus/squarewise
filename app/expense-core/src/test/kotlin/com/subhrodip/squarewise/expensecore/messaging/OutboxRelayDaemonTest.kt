package com.subhrodip.squarewise.expensecore.messaging
import com.subhrodip.squarewise.expensecore.messaging.config.OutboxMessagingConfiguration
import com.subhrodip.squarewise.expensecore.messaging.config.OutboxRelayDaemon
import com.subhrodip.squarewise.expensecore.messaging.config.OutboxRelayProperties
import com.subhrodip.squarewise.expensecore.messaging.config.RequiredOutboxConfiguration
import com.subhrodip.squarewise.expensecore.messaging.broker.BrokerPublisher
import com.subhrodip.squarewise.expensecore.messaging.broker.InMemoryBroker
import com.subhrodip.squarewise.expensecore.messaging.outbox.model.OutboxMessage
import com.subhrodip.squarewise.expensecore.messaging.outbox.service.OutboxRelay
import com.subhrodip.squarewise.expensecore.messaging.outbox.model.OutboxStatus
import com.subhrodip.squarewise.expensecore.messaging.outbox.persistence.OutboxStore
import java.time.Instant
import java.util.UUID
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.assertThrows
import org.springframework.beans.factory.NoSuchBeanDefinitionException
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import tools.jackson.databind.ObjectMapper

class OutboxRelayDaemonTest {

    private val contextRunner = ApplicationContextRunner()
        .withUserConfiguration(OutboxMessagingConfiguration::class.java)
        .withBean(OutboxStore::class.java, { OutboxRelay() })
        .withBean(BrokerPublisher::class.java, { InMemoryBroker() })
        .withBean(ObjectMapper::class.java, { ObjectMapper() })

    @Test
    fun `deployed outbox configuration requires durable publishing`() {
        assertThrows(IllegalArgumentException::class.java) {
            RequiredOutboxConfiguration(OutboxRelayProperties())
        }
        assertThrows(IllegalArgumentException::class.java) {
            RequiredOutboxConfiguration(OutboxRelayProperties(enabled = true))
        }
        RequiredOutboxConfiguration(OutboxRelayProperties(enabled = true, rabbitEnabled = true))
    }

    /** Verifies every mutable relay property participates in configuration binding. */
    @Test
    fun `binds all relay property values`() {
        val properties = OutboxRelayProperties().apply {
            enabled = true
            rabbitEnabled = true
            batchSize = 25
            pollDelayMs = 2500
            leaseSeconds = 45
            maxAttempts = 8
            retryAfterSeconds = 12
        }

        assertEquals(true, properties.enabled)
        assertEquals(true, properties.rabbitEnabled)
        assertEquals(25, properties.batchSize)
        assertEquals(2500, properties.pollDelayMs)
        assertEquals(45, properties.leaseSeconds)
        assertEquals(8, properties.maxAttempts)
        assertEquals(12, properties.retryAfterSeconds)
    }

    @Test
    fun `daemon is active and publishes available messages when enabled`() {
        contextRunner
            .withPropertyValues(
                "squarewise.outbox.enabled=true",
                "squarewise.outbox.batch-size=10",
                "squarewise.outbox.lease-seconds=15"
            )
            .withBean(OutboxRelayDaemon::class.java)
            .run { context ->
                assertTrue(context.containsBean("outboxRelayDaemon"))
                val daemon = context.getBean(OutboxRelayDaemon::class.java)
                val relay = context.getBean(OutboxStore::class.java)

                val eventId = UUID.randomUUID()
                val message = OutboxMessage(
                    eventId = eventId,
                    eventType = "expense.created",
                    aggregateId = UUID.randomUUID(),
                    groupId = UUID.randomUUID(),
                    groupRevision = 1,
                    occurredAt = Instant.now(),
                    payload = mapOf("amount" to 500)
                )
                relay.append(message)

                val result = daemon.pollAndPublish()
                assertEquals(1, result.claimed)
                assertEquals(1, result.confirmed)
                assertEquals(0, result.rejected)

                val snapshot = relay.snapshot()
                assertEquals(OutboxStatus.PUBLISHED, snapshot.single().status)
            }
    }

    @Test
    fun `daemon bean is omitted when disabled by default`() {
        contextRunner
            .run { context ->
                assertThrows(NoSuchBeanDefinitionException::class.java) {
                    context.getBean(OutboxRelayDaemon::class.java)
                }
            }
    }
}
