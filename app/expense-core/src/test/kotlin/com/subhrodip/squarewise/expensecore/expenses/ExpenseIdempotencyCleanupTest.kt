package com.subhrodip.squarewise.expensecore.expenses

import com.subhrodip.squarewise.expensecore.expenses.persistence.entity.ExpenseIdempotencyEntity
import com.subhrodip.squarewise.expensecore.expenses.persistence.repository.ExpenseIdempotencyRepository
import com.subhrodip.squarewise.expensecore.expenses.service.ExpenseIdempotencyCleanup

import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.mockito.Mockito.any
import org.mockito.Mockito.mock
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.springframework.data.domain.PageRequest
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.TransactionStatus

class ExpenseIdempotencyCleanupTest {
    @Test
    fun `deletes one bounded expired claim batch`() {
        val repository = mock(ExpenseIdempotencyRepository::class.java)
        val now = Instant.parse("2026-09-21T00:00:00Z")
        val cutoff = now.minus(Duration.ofHours(24))
        `when`(repository.purgeOlderThan(cutoff, 1)).thenReturn(1, 0)
        val cleanup = ExpenseIdempotencyCleanup(repository, Clock.fixed(now, ZoneOffset.UTC), Duration.ofHours(24), 1)

        assertEquals(1, cleanup.cleanupExpired())
        verify(repository, times(2)).purgeOlderThan(cutoff, 1)
    }

    /** Verifies an empty expired-claim page is a bounded no-op. */
    @Test
    fun `returns zero and does not delete when no claims are expired`() {
        val repository = mock(ExpenseIdempotencyRepository::class.java)
        val now = Instant.parse("2026-09-21T00:00:00Z")
        val cutoff = now.minus(Duration.ofHours(24))
        `when`(repository.purgeOlderThan(cutoff, 10)).thenReturn(0)
        val cleanup = ExpenseIdempotencyCleanup(repository, Clock.fixed(now, ZoneOffset.UTC), Duration.ofHours(24), 10)

        assertEquals(0, cleanup.cleanupExpired())
        verify(repository).purgeOlderThan(cutoff, 10)
    }

    @Test
    fun `drains multiple batches of expired claims`() {
        val repository = mock(ExpenseIdempotencyRepository::class.java)
        val now = Instant.parse("2026-09-21T00:00:00Z")
        val cutoff = now.minus(Duration.ofHours(24))
        `when`(repository.purgeOlderThan(cutoff, 1))
            .thenReturn(1)
            .thenReturn(1)
            .thenReturn(0)

        val cleanup = ExpenseIdempotencyCleanup(repository, Clock.fixed(now, ZoneOffset.UTC), Duration.ofHours(24), 1, 5)

        assertEquals(2, cleanup.cleanupExpired())
        verify(repository, times(3)).purgeOlderThan(cutoff, 1)
    }

    @Test
    fun `cleanupExpired wraps batch in transaction when transaction manager is present`() {
        val repository = mock(ExpenseIdempotencyRepository::class.java)
        val tm = mock(PlatformTransactionManager::class.java)
        val status = mock(TransactionStatus::class.java)
        `when`(tm.getTransaction(any())).thenReturn(status)

        val now = Instant.parse("2026-09-21T00:00:00Z")
        val cutoff = now.minus(Duration.ofHours(24))
        val cleanup = ExpenseIdempotencyCleanup(
            repository,
            Clock.fixed(now, ZoneOffset.UTC),
            Duration.ofHours(24),
            10,
            transactionManager = tm
        )
        `when`(repository.purgeOlderThan(cutoff, 10)).thenReturn(5)

        assertEquals(5, cleanup.cleanupExpired())
        verify(tm).commit(status)
    }

    @Test
    fun `default constructor and companion constants have expected values`() {
        val repository = mock(ExpenseIdempotencyRepository::class.java)
        assertEquals(Duration.ofHours(24), ExpenseIdempotencyCleanup.DEFAULT_RETENTION)
        assertEquals(500, ExpenseIdempotencyCleanup.DEFAULT_BATCH_SIZE)
        assertEquals(50, ExpenseIdempotencyCleanup.DEFAULT_MAX_BATCHES)

        val defaultCleanup = ExpenseIdempotencyCleanup(repository)
        `when`(repository.purgeOlderThan(any(Instant::class.java) ?: Instant.EPOCH, any(Int::class.java) ?: 500)).thenReturn(0)
        assertEquals(0, defaultCleanup.cleanupExpired())
    }

    /** Verifies cleanup refuses replay-retention and batch configurations that cannot be safe. */
    @Test
    fun `rejects non-positive retention and batch size`() {
        val repository = mock(ExpenseIdempotencyRepository::class.java)
        val clock = Clock.fixed(Instant.EPOCH, ZoneOffset.UTC)

        assertThrows(IllegalArgumentException::class.java) {
            ExpenseIdempotencyCleanup(repository, clock, Duration.ZERO, 1)
        }
        assertThrows(IllegalArgumentException::class.java) {
            ExpenseIdempotencyCleanup(repository, clock, Duration.ofHours(1), 0)
        }
        assertThrows(IllegalArgumentException::class.java) {
            ExpenseIdempotencyCleanup(repository, clock, Duration.ofHours(-1), 1)
        }
        assertThrows(IllegalArgumentException::class.java) {
            ExpenseIdempotencyCleanup(repository, clock, Duration.ofHours(1), -1)
        }
        assertThrows(IllegalArgumentException::class.java) {
            ExpenseIdempotencyCleanup(repository, clock, Duration.ofHours(1), 1, 0)
        }
    }
}
