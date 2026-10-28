package com.subhrodip.squarewise.notifications.inbox

import com.subhrodip.squarewise.notifications.inbox.persistence.JpaNotificationInboxStore
import com.subhrodip.squarewise.notifications.inbox.persistence.NotificationInboxRepository

import com.subhrodip.squarewise.notifications.inbox.api.InboxController
import com.subhrodip.squarewise.notifications.inbox.model.InboxItem
import com.subhrodip.squarewise.notifications.inbox.persistence.InMemoryNotificationInboxStore
import com.subhrodip.squarewise.notifications.inbox.persistence.NotificationInboxStore
import com.subhrodip.squarewise.notifications.inbox.service.NotificationInboxService

import com.subhrodip.squarewise.notifications.consumer.persistence.ProcessedNotificationEventRepository
import com.subhrodip.squarewise.notifications.delivery.persistence.JpaEventDeduplicator
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
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
        "spring.datasource.url=jdbc:h2:mem:notification_inbox;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.jpa.open-in-view=false",
        "spring.rabbitmq.listener.simple.auto-startup=false"
    ]
)
class JpaNotificationInboxStoreTest @Autowired constructor(
    private val store: JpaNotificationInboxStore,
    private val deduplicator: JpaEventDeduplicator,
    private val inboxRepository: NotificationInboxRepository,
    private val eventRepository: ProcessedNotificationEventRepository
) {
    @Test
    fun `persists subject-scoped inbox items in stable cursor order`() {
        inboxRepository.deleteAll()
        val firstId = UUID.fromString("00000000-0000-7000-8000-000000000001")
        val secondId = UUID.fromString("00000000-0000-7000-8000-000000000002")
        val occurredAt = Instant.parse("2026-09-17T12:00:00Z")

        store.append("alice", InboxItem(firstId, "expense.created", "First", occurredAt))
        store.append("alice", InboxItem(secondId, "expense.updated", "Second", occurredAt))
        store.append("bob", InboxItem(UUID.randomUUID(), "expense.created", "Other", occurredAt))

        val inbox = NotificationInboxService(store)
        assertEquals(listOf(secondId), inbox.page("alice", null, 1).items.map(InboxItem::notificationId))
        val firstPage = inbox.page("alice", null, 1)
        assertEquals(listOf(firstId), inbox.page("alice", firstPage.nextCursor, 1).items.map(InboxItem::notificationId))
        assertEquals(1, inbox.list("bob").size)
    }

    @Test
    fun `treats a partial cursor as the first page`() {
        inboxRepository.deleteAll()
        val occurredAt = Instant.parse("2026-09-17T12:00:00Z")
        store.append("partial", InboxItem(UUID.randomUUID(), "expense.created", "Item", occurredAt))

        assertEquals(1, store.page("partial", occurredAt, null, 10).size)
    }

    @Test
    fun `treats a cursor with only an id as the first page`() {
        inboxRepository.deleteAll()
        store.append("partial-id", InboxItem(UUID.randomUUID(), "expense.created", "Item", Instant.EPOCH))

        assertEquals(1, store.page("partial-id", null, UUID.randomUUID(), 10).size)
    }

    @Test
    fun `rejects page sizes outside the bounded contract`() {
        assertThrows(IllegalArgumentException::class.java) {
            store.page("alice", null, null, 0)
        }
        assertThrows(IllegalArgumentException::class.java) {
            store.page("alice", null, null, 101)
        }
    }

    @Test
    fun `persists event id deduplication across adapter calls`() {
        eventRepository.deleteAll()
        val eventId = UUID.fromString("00000000-0000-7000-8000-000000000003")

        assertTrue(deduplicator.firstDelivery(eventId))
        assertFalse(deduplicator.firstDelivery(eventId))
        assertEquals(1, eventRepository.count())
    }

    @Test
    fun `accepts one event when duplicate deliveries race`() {
        eventRepository.deleteAll()
        val eventId = UUID.fromString("00000000-0000-7000-8000-000000000004")
        val executor = Executors.newFixedThreadPool(2)

        try {
            val outcomes = executor.invokeAll(
                listOf(
                    Callable { deduplicator.firstDelivery(eventId) },
                    Callable { deduplicator.firstDelivery(eventId) }
                )
            ).map { it.get() }

            assertEquals(1, outcomes.count { it })
            assertEquals(1, eventRepository.count())
        } finally {
            executor.shutdownNow()
        }
    }

    @Test
    fun `marks notification as read for matching subject`() {
        inboxRepository.deleteAll()
        val notificationId = UUID.fromString("00000000-0000-7000-8000-000000000005")
        val occurredAt = Instant.parse("2026-09-17T12:00:00Z")

        store.append("alice", InboxItem(notificationId, "expense.created", "Unread item", occurredAt, read = false))

        assertFalse(store.list("alice").first().read)

        val wrongSubjectMarked = store.markAsRead("bob", notificationId)
        assertFalse(wrongSubjectMarked)
        assertFalse(store.list("alice").first().read)

        val unknownMarked = store.markAsRead("alice", UUID.randomUUID())
        assertFalse(unknownMarked)

        val marked = store.markAsRead("alice", notificationId)
        assertTrue(marked)
        assertTrue(store.list("alice").first().read)

        val entity = inboxRepository.findById(notificationId).orElseThrow()
        assertTrue(entity.read)
    }

    @Test
    fun `rejects invalid subjects and inbox payloads before persistence`() {
        inboxRepository.deleteAll()
        assertThrows(IllegalArgumentException::class.java) { store.list(" ") }
        assertThrows(IllegalArgumentException::class.java) {
            store.append("alice", InboxItem(UUID.randomUUID(), "", "Message", Instant.now()))
        }
        assertThrows(IllegalArgumentException::class.java) {
            store.append("alice", InboxItem(UUID.randomUUID(), "event", " ", Instant.now()))
        }
        store.append("a".repeat(256), InboxItem(UUID.randomUUID(), "event", "Message", Instant.now()))
        assertThrows(IllegalArgumentException::class.java) {
            store.append("a".repeat(257), InboxItem(UUID.randomUUID(), "event", "Message", Instant.now()))
        }
        assertEquals(1, inboxRepository.count())
    }

    @Test
    fun `rejects an overlength event type before persistence`() {
        inboxRepository.deleteAll()

        assertThrows(IllegalArgumentException::class.java) {
            store.append(
                "alice",
                InboxItem(UUID.randomUUID(), "e".repeat(201), "Message", Instant.EPOCH)
            )
        }

        assertEquals(0, inboxRepository.count())
    }

    @Test
    fun `rejects an overlength message before persistence`() {
        inboxRepository.deleteAll()

        assertThrows(IllegalArgumentException::class.java) {
            store.append(
                "alice",
                InboxItem(UUID.randomUUID(), "event", "m".repeat(2001), Instant.EPOCH)
            )
        }

        assertEquals(0, inboxRepository.count())
    }
}
