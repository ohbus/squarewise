package com.subhrodip.squarewise.expensecore.messaging

import com.subhrodip.squarewise.expensecore.messaging.outbox.model.OutboxMessage
import com.subhrodip.squarewise.expensecore.messaging.outbox.service.OutboxRelay
import com.subhrodip.squarewise.expensecore.messaging.outbox.model.OutboxStatus
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.Duration
import java.util.UUID

class OutboxTest {
    @Test
    fun `claims in order and acknowledges exactly once`() {
        val relay = OutboxRelay { Instant.parse("2026-01-01T00:00:00Z") }
        val id = UUID.randomUUID()
        relay.append(OutboxMessage(id, "expense.created", UUID.randomUUID(), UUID.randomUUID(), 1, Instant.EPOCH, emptyMap()))
        assertEquals(listOf(id), relay.claim(10, Duration.ofMinutes(1)).map { it.eventId })
        relay.acknowledge(id)
        assertEquals(OutboxStatus.PUBLISHED, relay.snapshot().single().status)
        assertEquals(0, relay.claim(10, Duration.ofMinutes(1)).size)
    }

    @Test
    fun `expired lease can be reclaimed`() {
        var now = Instant.parse("2026-01-01T00:00:00Z")
        val relay = OutboxRelay { now }
        relay.append(OutboxMessage(UUID.randomUUID(), "expense.created", UUID.randomUUID(), UUID.randomUUID(), 1, Instant.EPOCH, emptyMap()))
        relay.claim(1, Duration.ofMinutes(1))
        now = now.plus(Duration.ofMinutes(2))
        assertEquals(1, relay.claim(1, Duration.ofMinutes(1)).size)
        assertEquals(2, relay.snapshot().single().attempts)
    }

    @Test
    fun `failed message waits for retry and parks after final attempt`() {
        var now = Instant.parse("2026-01-01T00:00:00Z")
        val relay = OutboxRelay { now }
        val id = UUID.randomUUID()
        relay.append(OutboxMessage(id, "expense.created", UUID.randomUUID(), UUID.randomUUID(), 1, now, emptyMap()))

        relay.claim(1, Duration.ofMinutes(1))
        relay.reject(id, maxAttempts = 2, retryAfter = Duration.ofMinutes(5))
        assertEquals(0, relay.claim(1, Duration.ofMinutes(1)).size)
        now = now.plus(Duration.ofMinutes(5))
        relay.claim(1, Duration.ofMinutes(1))
        relay.reject(id, maxAttempts = 2, retryAfter = Duration.ZERO)

        assertEquals(OutboxStatus.PARKED, relay.snapshot().single().status)
        assertEquals(0, relay.claim(1, Duration.ofMinutes(1)).size)
    }

    @Test
    fun `reject validates retry policy and ignores an unknown event`() {
        val relay = OutboxRelay { Instant.parse("2026-01-01T00:00:00Z") }

        assertThrows(IllegalArgumentException::class.java) {
            relay.reject(UUID.randomUUID(), maxAttempts = 0, retryAfter = Duration.ZERO)
        }
        assertThrows(IllegalArgumentException::class.java) {
            relay.reject(UUID.randomUUID(), maxAttempts = 1, retryAfter = Duration.ofSeconds(-1))
        }

        relay.reject(UUID.randomUUID(), maxAttempts = 1, retryAfter = Duration.ZERO)
        assertEquals(0, relay.snapshot().size)
    }

    @Test
    fun `claim and append enforce event state boundaries`() {
        val now = Instant.parse("2026-01-01T00:00:00Z")
        val eventId = UUID.randomUUID()
        val relay = OutboxRelay { now }
        val message = OutboxMessage(
            eventId,
            "expense.created",
            UUID.randomUUID(),
            UUID.randomUUID(),
            1,
            now,
            emptyMap()
        )

        assertThrows(IllegalArgumentException::class.java) { relay.claim(0, Duration.ofMinutes(1)) }
        relay.append(message)
        assertThrows(IllegalArgumentException::class.java) { relay.append(message.copy()) }

        val claimedWithoutLease = message.copy(status = OutboxStatus.CLAIMED, leaseUntil = null)
        relay.append(claimedWithoutLease.copy(eventId = UUID.randomUUID()))
        assertEquals(1, relay.claim(10, Duration.ofMinutes(1)).size)
        assertEquals(0, relay.claim(10, Duration.ofMinutes(1)).size)

        relay.acknowledge(UUID.randomUUID())
        assertEquals(2, relay.snapshot().size)
    }
}
