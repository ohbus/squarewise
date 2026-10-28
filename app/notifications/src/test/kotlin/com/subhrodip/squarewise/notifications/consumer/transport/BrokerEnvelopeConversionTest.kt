package com.subhrodip.squarewise.notifications.consumer.transport

import java.time.Instant
import java.util.UUID
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

/** Verifies broker-envelope fallback and bounded conversion into notification events. */
class BrokerEnvelopeConversionTest {
    private val eventId = UUID.fromString("00000000-0000-7000-8000-000000000401")
    private val aggregateId = UUID.fromString("00000000-0000-7000-8000-000000000402")
    private val groupId = UUID.fromString("00000000-0000-7000-8000-000000000403")

    /** Verifies malformed notification IDs fall back to the aggregate and recipient/description fields map. */
    @Test
    fun `maps fallback notification fields without losing the envelope identity`() {
        val event = envelope(
            payload = mapOf(
                "notificationId" to "not-a-uuid",
                "recipientId" to "alice@example.com",
                "description" to "Expense description"
            )
        ).toNotificationEvent()

        assertEquals(eventId, event.notificationId)
        assertEquals("alice@example.com", event.subject)
        assertEquals("Expense description", event.message)
        assertEquals(eventId, event.eventId)
    }

    @Test
    fun `gives aggregate changes distinct fallback notification identities`() {
        val first = envelope(eventType = "expense.created").copy(
            eventId = UUID.randomUUID(),
            payload = mapOf("subject" to "alice", "message" to "Created")
        ).toNotificationEvent()
        val second = envelope(eventType = "expense.updated").copy(
            eventId = UUID.randomUUID(),
            payload = mapOf("subject" to "alice", "message" to "Updated")
        ).toNotificationEvent()
        val replay = envelope(eventType = "expense.created").copy(
            eventId = first.eventId,
            payload = mapOf("subject" to "alice", "message" to "Created")
        ).toNotificationEvent()

        assertEquals(first.eventId, first.notificationId)
        assertEquals(second.eventId, second.notificationId)
        assertEquals(first.notificationId, replay.notificationId)
    }

    /** Verifies explicit values are bounded to the public notification field limits. */
    @Test
    fun `rejects oversized converted notification fields`() {
        assertThrows(InvalidEnvelopeException::class.java) {
            envelope(
                eventType = "t".repeat(240),
                payload = mapOf(
                    "subject" to "s".repeat(240),
                    "message" to "m".repeat(2200)
                )
            ).toNotificationEvent()
        }
    }

    /** Verifies each supported subject fallback preserves a usable notification event. */
    @Test
    fun `uses subject and message fallback fields in precedence order`() {
        assertEquals(
            "subject@example.com",
            envelope(payload = mapOf("subject" to "subject@example.com", "message" to "Direct message"))
                .toNotificationEvent().subject
        )
        assertEquals(
            "recipient@example.com",
            envelope(payload = mapOf("recipient" to "recipient@example.com"))
                .toNotificationEvent().subject
        )
        assertEquals(
            "user@example.com",
            envelope(payload = mapOf("userId" to "user@example.com"))
                .toNotificationEvent().subject
        )
        assertThrows(InvalidEnvelopeException::class.java) { envelope(payload = emptyMap()).toNotificationEvent() }
    }

    /** Verifies blank selected fields never fall back to a group UUID recipient. */
    @Test
    fun `rejects blank subject when no recipient exists`() {
        val event = envelope(
            payload = mapOf(
                "subject" to "   ",
                "recipient" to "",
                "recipientId" to "recipient@example.com",
                "message" to "",
                "description" to "Description fallback"
            )
        ).toNotificationEvent()

        assertEquals("recipient@example.com", event.subject)

        assertThrows(InvalidEnvelopeException::class.java) {
            envelope(payload = mapOf("subject" to " ", "message" to " ", "description" to " ")).toNotificationEvent()
        }
    }

    private fun envelope(
        eventType: String = "expense.created",
        payload: Map<String, Any?> = emptyMap()
    ): BrokerEnvelope = BrokerEnvelope(
        eventId = eventId,
        eventType = eventType,
        schemaVersion = 1,
        aggregateId = aggregateId,
        groupId = groupId,
        groupRevision = 1,
        occurredAt = Instant.parse("2026-09-17T20:00:00Z"),
        payload = payload
    )
}
