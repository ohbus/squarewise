package com.subhrodip.squarewise.expensecore.messaging.outbox.service

import com.subhrodip.squarewise.expensecore.messaging.outbox.persistence.OutboxRepository
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`

class OutboxRetentionCleanupTest {
    private val repository = mock(OutboxRepository::class.java)
    private val now = Instant.parse("2026-10-13T10:00:00Z")
    private val clock = Clock.fixed(now, ZoneOffset.UTC)

    @Test
    fun `cleanupPublished delegates cutoff and batch size to repository`() {
        val cleanup = OutboxRetentionCleanup(
            repository = repository,
            clock = clock,
            retention = Duration.ofDays(7),
            batchSize = 250
        )
        val expectedCutoff = now.minus(Duration.ofDays(7))
        `when`(repository.purgePublishedOlderThan(expectedCutoff, 250)).thenReturn(42)

        val deleted = cleanup.cleanupPublished()

        assertEquals(42, deleted)
        verify(repository).purgePublishedOlderThan(expectedCutoff, 250)
    }

    @Test
    fun `cleanupPublished drains backlog across multiple batches`() {
        val cleanup = OutboxRetentionCleanup(
            repository = repository,
            clock = clock,
            retention = Duration.ofDays(7),
            batchSize = 100,
            maxBatches = 5
        )
        val expectedCutoff = now.minus(Duration.ofDays(7))
        `when`(repository.purgePublishedOlderThan(expectedCutoff, 100))
            .thenReturn(100)
            .thenReturn(100)
            .thenReturn(33)

        val deleted = cleanup.cleanupPublished()

        assertEquals(233, deleted)
    }

    @Test
    fun `rejects zero or negative retention and batch size`() {
        assertThrows(IllegalArgumentException::class.java) {
            OutboxRetentionCleanup(repository, clock, retention = Duration.ZERO, batchSize = 100)
        }
        assertThrows(IllegalArgumentException::class.java) {
            OutboxRetentionCleanup(repository, clock, retention = Duration.ofDays(7), batchSize = 0)
        }
        assertThrows(IllegalArgumentException::class.java) {
            OutboxRetentionCleanup(repository, clock, retention = Duration.ofDays(7), batchSize = 100, maxBatches = 0)
        }
    }
}
