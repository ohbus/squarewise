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
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.springframework.data.domain.PageRequest

class ExpenseIdempotencyCleanupTest {
    @Test
    fun `deletes one bounded expired claim batch`() {
        val repository = mock(ExpenseIdempotencyRepository::class.java)
        val now = Instant.parse("2026-09-21T00:00:00Z")
        val cutoff = now.minus(Duration.ofHours(24))
        `when`(repository.purgeOlderThan(cutoff, 1)).thenReturn(1, 0)
        val cleanup = ExpenseIdempotencyCleanup(repository, Clock.fixed(now, ZoneOffset.UTC), Duration.ofHours(24), 1)

        assertEquals(1, cleanup.cleanupExpired())
        verify(repository, org.mockito.Mockito.times(2)).purgeOlderThan(cutoff, 1)
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
        verify(repository, org.mockito.Mockito.times(3)).purgeOlderThan(cutoff, 1)
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
