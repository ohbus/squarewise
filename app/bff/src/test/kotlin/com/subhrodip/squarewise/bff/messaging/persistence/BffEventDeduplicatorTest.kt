package com.subhrodip.squarewise.bff.messaging.persistence

import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** Verifies bounded BFF event deduplication rejects unsafe cache capacities. */
class BffEventDeduplicatorTest {
    /** Verifies zero and negative capacities fail before an unbounded cache can be created. */
    @Test
    fun `rejects non-positive capacity`() {
        assertThrows(IllegalArgumentException::class.java) {
            BffEventDeduplicator(0)
        }
        assertThrows(IllegalArgumentException::class.java) {
            BffEventDeduplicator(-1)
        }
    }

    @Test
    fun `reports and clears bounded duplicate state`() {
        val deduplicator = BffEventDeduplicator(2)
        val first = java.util.UUID.randomUUID()
        val second = java.util.UUID.randomUUID()

        assertFalse(deduplicator.isDuplicateAndMark(first))
        assertFalse(deduplicator.isDuplicateAndMark(second))
        assertTrue(deduplicator.isDuplicateAndMark(first))
        assertEquals(2, deduplicator.size())

        deduplicator.clear()

        assertEquals(0, deduplicator.size())
        assertFalse(deduplicator.isDuplicateAndMark(first))
    }

    @Test
    fun `releases an in-flight claim so a failed delivery can be retried`() {
        val deduplicator = BffEventDeduplicator()
        val eventId = java.util.UUID.randomUUID()

        assertTrue(deduplicator.tryClaim(eventId))
        deduplicator.release(eventId)

        assertTrue(deduplicator.tryClaim(eventId))
    }
}
