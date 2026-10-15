package com.subhrodip.squarewise.accounts.auth.delivery.outbox

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.mockito.Mockito.any
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.TransactionStatus
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset

class AuthEmailOutboxRetentionCleanupTest {
    private val repository = mock(AuthEmailOutboxRepository::class.java)
    private val fixedInstant = Instant.parse("2026-10-13T10:00:00Z")
    private val clock = Clock.fixed(fixedInstant, ZoneOffset.UTC)

    @Test
    fun `cleanupPublished delegates to repository with correct cutoff and batch size`() {
        val retention = Duration.ofDays(7)
        val expectedCutoff = fixedInstant.minus(retention)
        `when`(repository.purgePublishedOlderThan(expectedCutoff, 500)).thenReturn(42)

        val cleanup = AuthEmailOutboxRetentionCleanup(
            repository = repository,
            clock = clock,
            retention = retention,
            batchSize = 500
        )

        val purged = cleanup.cleanupPublished()

        assertEquals(42, purged)
        verify(repository).purgePublishedOlderThan(expectedCutoff, 500)
    }

    @Test
    fun `cleanupPublished drains backlog across multiple batches`() {
        val retention = Duration.ofDays(7)
        val expectedCutoff = fixedInstant.minus(retention)
        `when`(repository.purgePublishedOlderThan(expectedCutoff, 100))
            .thenReturn(100)
            .thenReturn(100)
            .thenReturn(15)

        val cleanup = AuthEmailOutboxRetentionCleanup(
            repository = repository,
            clock = clock,
            retention = retention,
            batchSize = 100,
            maxBatches = 5
        )

        val purged = cleanup.cleanupPublished()

        assertEquals(215, purged)
    }

    @Test
    fun `cleanupPublished wraps batch in transaction when transaction manager is present`() {
        val tm = mock(PlatformTransactionManager::class.java)
        val status = mock(TransactionStatus::class.java)
        `when`(tm.getTransaction(any())).thenReturn(status)

        val retention = Duration.ofDays(7)
        val expectedCutoff = fixedInstant.minus(retention)
        `when`(repository.purgePublishedOlderThan(expectedCutoff, 100)).thenReturn(10)

        val cleanup = AuthEmailOutboxRetentionCleanup(
            repository = repository,
            clock = clock,
            retention = retention,
            batchSize = 100,
            transactionManager = tm
        )

        val purged = cleanup.cleanupPublished()

        assertEquals(10, purged)
        verify(tm).commit(status)
    }

    @Test
    fun `default constructor and companion constants have expected values`() {
        assertEquals(Duration.ofDays(7), AuthEmailOutboxRetentionCleanup.DEFAULT_RETENTION)
        assertEquals(500, AuthEmailOutboxRetentionCleanup.DEFAULT_BATCH_SIZE)
        assertEquals(50, AuthEmailOutboxRetentionCleanup.DEFAULT_MAX_BATCHES)

        val defaultCleanup = AuthEmailOutboxRetentionCleanup(repository)
        `when`(repository.purgePublishedOlderThan(any(Instant::class.java) ?: Instant.EPOCH, any(Int::class.java) ?: 500)).thenReturn(0)
        assertEquals(0, defaultCleanup.cleanupPublished())
    }

    @Test
    fun `rejects invalid constructor parameters`() {
        assertThrows(IllegalArgumentException::class.java) {
            AuthEmailOutboxRetentionCleanup(
                repository = repository,
                retention = Duration.ZERO
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            AuthEmailOutboxRetentionCleanup(
                repository = repository,
                batchSize = 0
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            AuthEmailOutboxRetentionCleanup(
                repository = repository,
                maxBatches = 0
            )
        }
    }
}
