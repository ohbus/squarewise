package com.subhrodip.squarewise.expensecore.messaging.broker

import java.time.Instant
import java.util.UUID
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** Verifies value semantics at the Expense Core broker message boundary. */
class BrokerMessageTest {
    private val eventId = UUID.randomUUID()
    private val occurredAt = Instant.parse("2026-10-01T00:00:00Z")

    @Test
    fun `messages with equal content arrays have equal value semantics`() {
        val first = message(byteArrayOf(1, 2, 3))
        val second = message(byteArrayOf(1, 2, 3))

        assertEquals(first, second)
        assertEquals(first.hashCode(), second.hashCode())
        assertTrue(first.toString().contains("payloadSize=3"))
        assertFalse(first.toString().contains("[1, 2, 3]"))
    }

    @Test
    fun `messages differ when any identity or payload field differs`() {
        val baseline = message(byteArrayOf(1, 2, 3))

        assertNotEquals(baseline, baseline.copy(eventId = UUID.randomUUID()))
        assertNotEquals(baseline, baseline.copy(eventType = "expense.updated"))
        assertNotEquals(baseline, baseline.copy(payload = byteArrayOf(1, 2, 4)))
        assertNotEquals(baseline, baseline.copy(occurredAt = occurredAt.plusSeconds(1)))
        assertNotEquals(baseline, baseline.copy(headers = mapOf("revision" to "2")))
    }

    @Test
    fun `messages are not equal to null or another type`() {
        val message = message(byteArrayOf(1))

        assertFalse(message.equals(null))
        assertFalse(message.equals("not a broker message"))
    }

    private fun message(payload: ByteArray): BrokerMessage = BrokerMessage(
        eventId = eventId,
        eventType = "expense.created",
        payload = payload,
        occurredAt = occurredAt,
        headers = mapOf("revision" to "1")
    )
}
