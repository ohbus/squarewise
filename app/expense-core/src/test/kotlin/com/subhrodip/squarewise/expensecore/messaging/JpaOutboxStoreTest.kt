package com.subhrodip.squarewise.expensecore.messaging

import com.subhrodip.squarewise.expensecore.messaging.outbox.persistence.JpaOutboxStore
import com.subhrodip.squarewise.expensecore.messaging.outbox.model.OutboxMessage
import com.subhrodip.squarewise.expensecore.messaging.outbox.persistence.OutboxRepository
import com.subhrodip.squarewise.expensecore.messaging.outbox.model.OutboxStatus
import java.time.Duration
import java.time.Instant
import java.util.UUID
import java.util.concurrent.Callable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest

/**
 * Integration tests for [JpaOutboxStore] verifying transactional claim, retry,
 * acknowledgement, parking, and concurrency semantics against the persistent outbox table.
 *
 * Each test purges the outbox table via [setUp] to ensure isolation from other
 * integration tests executing in the shared Spring Boot context.
 */
@SpringBootTest
class JpaOutboxStoreTest @Autowired constructor(
    private val store: JpaOutboxStore,
    private val repository: OutboxRepository
) {
    /**
     * Purges all existing outbox messages prior to each test invocation, guaranteeing
     * an isolated baseline independent of test execution order.
     */
    @BeforeEach
    fun setUp() {
        repository.deleteAll()
    }

    /**
     * Verifies that outbox events transition cleanly through claim, retry backoff,
     * final parking on exceeding maximum attempts, and successful acknowledgement.
     */
    @Test
    fun `persists claim retry acknowledgement and parking state`() {
        val retryId = UUID.randomUUID()
        val publishedId = UUID.randomUUID()
        store.append(message(retryId, Instant.now().minusSeconds(2), mapOf("amount" to 1250, "currency" to "EUR")))
        store.append(message(publishedId, Instant.now().minusSeconds(1)))

        val firstClaim = store.claim(1, Duration.ofMinutes(1)).single()
        assertEquals(retryId, firstClaim.eventId)
        assertEquals(1, firstClaim.attempts)
        store.reject(retryId, maxAttempts = 2, retryAfter = Duration.ZERO)

        val retried = store.claim(1, Duration.ofMinutes(1)).single()
        assertEquals(retryId, retried.eventId)
        assertEquals(2, retried.attempts)
        store.reject(retryId, maxAttempts = 2, retryAfter = Duration.ZERO)

        val nextClaim = store.claim(1, Duration.ofMinutes(1)).single()
        assertEquals(publishedId, nextClaim.eventId)
        store.acknowledge(publishedId)

        val snapshot = store.snapshot().associateBy { it.eventId }
        assertEquals(OutboxStatus.PARKED, snapshot.getValue(retryId).status)
        assertEquals(OutboxStatus.PUBLISHED, snapshot.getValue(publishedId).status)
        assertEquals(mapOf("amount" to 1250, "currency" to "EUR"), snapshot.getValue(retryId).payload)
        assertTrue(store.claim(10, Duration.ofMinutes(1)).isEmpty())
    }

    /**
     * Verifies that concurrent threads executing batch claims against the persistent
     * outbox using SELECT FOR UPDATE SKIP LOCKED claim disjoint event sets without duplicates.
     */
    @Test
    fun `competing workers claim each event once`() {
        val ids = (1..12).map { index ->
            UUID.randomUUID().also { store.append(message(it, Instant.now().minusSeconds(20L - index))) }
        }.toSet()
        val executor = Executors.newFixedThreadPool(3)
        val start = CountDownLatch(1)
        val claims = (1..3).map {
            executor.submit(Callable {
                start.await()
                store.claim(12, Duration.ofMinutes(1)).map(OutboxMessage::eventId)
            })
        }
        start.countDown()
        val claimedIds = claims.flatMap { it.get() }
        executor.shutdown()

        assertEquals(ids, claimedIds.toSet())
        assertEquals(ids.size, claimedIds.size)
        assertTrue(store.claim(12, Duration.ofMinutes(1)).isEmpty())
    }

    /**
     * Verifies that when an outbox message lease expires, it becomes eligible for
     * reclaiming by subsequent worker claim operations with an incremented attempt counter.
     */
    @Test
    fun `expired persisted lease is reclaimable`() {
        val eventId = UUID.randomUUID()
        store.append(message(eventId, Instant.now().minusSeconds(10)).copy(
            status = OutboxStatus.CLAIMED,
            attempts = 1,
            leaseUntil = Instant.now().minusSeconds(1)
        ))

        val reclaimed = store.claim(1, Duration.ofMinutes(1)).single()

        assertEquals(eventId, reclaimed.eventId)
        assertEquals(2, reclaimed.attempts)
        assertEquals(OutboxStatus.CLAIMED, reclaimed.status)
    }

    /** Verifies claim rejects invalid policies and skips messages that are not yet eligible. */
    @Test
    fun `claim validates policy and skips future pending and active leases`() {
        assertThrows(IllegalArgumentException::class.java) {
            store.claim(0, Duration.ofMinutes(1))
        }
        assertThrows(IllegalArgumentException::class.java) {
            store.claim(-1, Duration.ofMinutes(1))
        }
        assertThrows(IllegalArgumentException::class.java) {
            store.claim(1, Duration.ZERO)
        }
        assertThrows(IllegalArgumentException::class.java) {
            store.claim(1, Duration.ofSeconds(-1))
        }

        val future = Instant.now().plusSeconds(60)
        val pendingId = UUID.randomUUID()
        val claimedId = UUID.randomUUID()
        store.append(message(pendingId, Instant.now()).copy(availableAt = future))
        store.append(
            message(claimedId, Instant.now()).copy(
                status = OutboxStatus.CLAIMED,
                attempts = 1,
                leaseUntil = future
            )
        )

        assertTrue(store.claim(10, Duration.ofMinutes(1)).isEmpty())
        val snapshot = store.snapshot().associateBy { it.eventId }
        assertEquals(OutboxStatus.PENDING, snapshot.getValue(pendingId).status)
        assertEquals(OutboxStatus.CLAIMED, snapshot.getValue(claimedId).status)
    }

    /** Verifies durable append preserves the first payload and rejects duplicate event identifiers. */
    @Test
    fun `append rejects duplicate event ID without replacing the persisted message`() {
        val eventId = UUID.randomUUID()
        val original = message(eventId, Instant.now(), mapOf("amount" to 1250, "currency" to "EUR"))
        val replacement = original.copy(payload = mapOf("amount" to 9999, "currency" to "USD"))

        store.append(original)

        assertThrows(IllegalArgumentException::class.java) {
            store.append(replacement)
        }

        assertEquals(original.payload, store.snapshot().single().payload)
    }

    /** Verifies durable retry-policy validation and the harmless unknown-event no-op. */
    @Test
    fun `reject validates retry policy and ignores an unknown event`() {
        val unknownEventId = UUID.randomUUID()

        assertThrows(IllegalArgumentException::class.java) {
            store.reject(unknownEventId, maxAttempts = 0, retryAfter = Duration.ZERO)
        }
        assertThrows(IllegalArgumentException::class.java) {
            store.reject(unknownEventId, maxAttempts = 1, retryAfter = Duration.ofSeconds(-1))
        }

        store.reject(unknownEventId, maxAttempts = 1, retryAfter = Duration.ZERO)
        assertTrue(store.snapshot().isEmpty())
    }

    /** Verifies acknowledging an unknown event is a durable no-op rather than a failure. */
    @Test
    fun `acknowledge ignores an unknown event`() {
        store.acknowledge(UUID.randomUUID())

        assertTrue(store.snapshot().isEmpty())
    }

    /**
     * Creates a test [OutboxMessage] fixture with the specified parameters.
     */
    private fun message(
        eventId: UUID,
        occurredAt: Instant,
        payload: Map<String, Any?> = emptyMap()
    ) = OutboxMessage(
        eventId = eventId,
        eventType = "expense.created",
        aggregateId = UUID.randomUUID(),
        groupId = UUID.randomUUID(),
        groupRevision = 1,
        occurredAt = occurredAt,
        payload = payload
    )
}
