package com.subhrodip.squarewise.notifications.consumer

import com.subhrodip.squarewise.notifications.consumer.model.NotificationConsumptionOutcome
import com.subhrodip.squarewise.notifications.consumer.model.NotificationEvent
import com.subhrodip.squarewise.notifications.consumer.persistence.ProcessedNotificationEventRepository
import com.subhrodip.squarewise.notifications.consumer.service.NotificationConsumer
import com.subhrodip.squarewise.notifications.consumer.service.NotificationConsumerService
import com.subhrodip.squarewise.notifications.consumer.service.NotificationEventConsumer
import com.subhrodip.squarewise.notifications.consumer.service.TransactionalNotificationEventProcessor
import com.subhrodip.squarewise.notifications.consumer.transport.BrokerEnvelopeParser
import com.subhrodip.squarewise.notifications.consumer.transport.RabbitNotificationListener
import com.subhrodip.squarewise.notifications.preferences.persistence.PreferenceStore
import com.subhrodip.squarewise.notifications.errors.NotificationDomainException

import com.subhrodip.squarewise.notifications.inbox.persistence.NotificationInboxEntity
import com.subhrodip.squarewise.notifications.inbox.persistence.NotificationInboxRepository
import com.subhrodip.squarewise.notifications.email.persistence.NotificationEmailDeliveryRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import java.time.Instant
import java.util.UUID
import java.util.concurrent.Callable
import java.util.concurrent.Executors

@SpringBootTest(
    properties = [
        "spring.datasource.url=jdbc:h2:mem:notification_consumer;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.jpa.open-in-view=false",
        "spring.rabbitmq.listener.simple.auto-startup=false"
    ]
)
class NotificationEventConsumerTest @Autowired constructor(
    private val consumer: NotificationEventConsumer,
    private val inboxItems: NotificationInboxRepository,
    private val processedEvents: ProcessedNotificationEventRepository,
    private val emailDeliveries: NotificationEmailDeliveryRepository
) {
    @Test
    fun `applies one inbox effect and acknowledges duplicate delivery`() {
        clearState()
        val event = event(
            eventId = UUID.fromString("00000000-0000-7000-8000-000000000101"),
            notificationId = UUID.fromString("00000000-0000-7000-8000-000000000102")
        )

        assertEquals(NotificationConsumptionOutcome.APPLIED, consumer.consume(event))
        assertEquals(NotificationConsumptionOutcome.DUPLICATE, consumer.consume(event))
        assertEquals(1, inboxItems.count())
        assertEquals(1, processedEvents.count())
        assertEquals(1, emailDeliveries.count())
    }

    @Test
    fun `treats an existing notification identity as an acknowledged duplicate`() {
        clearState()
        val occupiedNotificationId = UUID.fromString("00000000-0000-7000-8000-000000000103")
        inboxItems.saveAndFlush(
            NotificationInboxEntity(
                occupiedNotificationId,
                "existing-subject",
                "expense.created",
                "Existing notification",
                Instant.parse("2026-09-17T12:00:00Z")
            )
        )
        val eventId = UUID.fromString("00000000-0000-7000-8000-000000000104")
        val duplicateEvent = event(eventId, occupiedNotificationId)

        assertEquals(NotificationConsumptionOutcome.DUPLICATE, consumer.consume(duplicateEvent))
        assertEquals(0, processedEvents.count())
        assertEquals(1, inboxItems.count())

        val retry = duplicateEvent.copy(
            notificationId = UUID.fromString("00000000-0000-7000-8000-000000000105")
        )
        assertEquals(NotificationConsumptionOutcome.APPLIED, consumer.consume(retry))
        assertEquals(1, processedEvents.count())
        assertEquals(2, inboxItems.count())
    }

    @Test
    fun `acknowledges replay with a new event identity for the same notification`() {
        clearState()
        val notificationId = UUID.fromString("00000000-0000-7000-8000-000000000108")
        val first = event(UUID.fromString("00000000-0000-7000-8000-000000000109"), notificationId)
        val replay = first.copy(eventId = UUID.fromString("00000000-0000-7000-8000-000000000110"))

        assertEquals(NotificationConsumptionOutcome.APPLIED, consumer.consume(first))
        assertEquals(NotificationConsumptionOutcome.DUPLICATE, consumer.consume(replay))
        assertEquals(1, inboxItems.count())
        assertEquals(1, processedEvents.count())
    }

    @Test
    fun `concurrent duplicate deliveries produce one local effect and one retry`() {
        clearState()
        val event = event(
            UUID.fromString("00000000-0000-7000-8000-000000000106"),
            UUID.fromString("00000000-0000-7000-8000-000000000107")
        )
        val executor = Executors.newFixedThreadPool(2)

        try {
            val outcomes = executor.invokeAll(
                listOf(
                    Callable { consumer.consume(event) },
                    Callable { consumer.consume(event) }
                )
            ).map { future ->
                runCatching { future.get() }.getOrElse { failure ->
                    assertTrue(failure.cause is NotificationDomainException)
                    null
                }
            }

            assertEquals(1, outcomes.count { it == NotificationConsumptionOutcome.APPLIED })
            assertEquals(
                1,
                outcomes.count { it == null || it == NotificationConsumptionOutcome.DUPLICATE }
            )
            assertEquals(1, inboxItems.count())
            assertEquals(1, processedEvents.count())
        } finally {
            executor.shutdownNow()
        }
    }

    @Test
    fun `rejects invalid inbox fields before recording an event`() {
        clearState()
        val valid = event(
            UUID.fromString("00000000-0000-7000-8000-000000000115"),
            UUID.fromString("00000000-0000-7000-8000-000000000116")
        )
        val invalidEvents = listOf(
            valid.copy(subject = ""),
            valid.copy(subject = "s".repeat(201)),
            valid.copy(eventType = ""),
            valid.copy(eventType = "t".repeat(201)),
            valid.copy(message = ""),
            valid.copy(message = "m".repeat(2001))
        )

        invalidEvents.forEach { invalid ->
            assertThrows(IllegalArgumentException::class.java) {
                consumer.consume(invalid.copy(eventId = UUID.randomUUID(), notificationId = UUID.randomUUID()))
            }
        }

        assertEquals(0, inboxItems.count())
        assertEquals(0, processedEvents.count())
    }

    private fun clearState() {
        processedEvents.deleteAll()
        inboxItems.deleteAll()
        emailDeliveries.deleteAll()
    }

    private fun event(eventId: UUID, notificationId: UUID) = NotificationEvent(
        eventId = eventId,
        notificationId = notificationId,
        subject = "alice",
        eventType = "expense.created",
        message = "An expense was created",
        occurredAt = Instant.parse("2026-09-17T12:00:00Z")
    )
}
