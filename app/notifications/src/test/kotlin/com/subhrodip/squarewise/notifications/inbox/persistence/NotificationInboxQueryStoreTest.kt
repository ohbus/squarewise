package com.subhrodip.squarewise.notifications.inbox.persistence

import com.subhrodip.squarewise.notifications.inbox.model.InboxItem
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID

/** Verifies the default reader-side keyset page filters and bounds results. */
class NotificationInboxQueryStoreTest {
    @Test
    fun `returns the first bounded page when no cursor is supplied`() {
        val newest = item(2, Instant.parse("2026-10-02T00:00:00Z"))
        val oldest = item(1, Instant.parse("2026-10-01T00:00:00Z"))
        val store = object : NotificationInboxQueryStore {
            override fun list(subject: String) = listOf(newest, oldest)
        }

        assertEquals(listOf(newest), store.page("alice", null, null, 1))
    }

    @Test
    fun `returns only items after a timestamp and id cursor`() {
        val cursor = item(2, Instant.parse("2026-10-02T00:00:00Z"))
        val sameTimestampLowerId = item(1, cursor.occurredAt)
        val older = item(3, Instant.parse("2026-10-01T00:00:00Z"))
        val store = object : NotificationInboxQueryStore {
            override fun list(subject: String) = listOf(cursor, sameTimestampLowerId, older)
        }

        assertEquals(listOf(sameTimestampLowerId, older), store.page("alice", cursor.occurredAt, cursor.notificationId, 10))
        assertEquals(listOf(cursor, sameTimestampLowerId), store.page("alice", cursor.occurredAt, null, 2))
    }

    private fun item(id: Long, occurredAt: Instant) = InboxItem(
        notificationId = UUID(0L, id),
        eventType = "expense.created",
        message = "message",
        occurredAt = occurredAt
    )
}
