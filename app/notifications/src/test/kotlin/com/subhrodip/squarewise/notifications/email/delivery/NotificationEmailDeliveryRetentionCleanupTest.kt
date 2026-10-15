package com.subhrodip.squarewise.notifications.email.delivery

import com.subhrodip.squarewise.notifications.email.persistence.NotificationEmailDeliveryRepository
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.mockito.Mockito.any
import org.mockito.Mockito.eq
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`

class NotificationEmailDeliveryRetentionCleanupTest {
    private val repository = mock(NotificationEmailDeliveryRepository::class.java)
    private val fixedClock = Clock.fixed(Instant.parse("2026-10-15T12:00:00Z"), ZoneOffset.UTC)

    @Test
    fun `cleanupCompleted purges expired deliveries in single batch`() {
        val cleanup = NotificationEmailDeliveryRetentionCleanup(
            repository = repository,
            clock = fixedClock,
            retentionDays = 7,
            batchSize = 100,
            maxBatches = 5
        )
        `when`(repository.purgeDeliveredOrSkippedOlderThan(any(Instant::class.java) ?: Instant.EPOCH, eq(100))).thenReturn(42)

        val deleted = cleanup.cleanupCompleted()

        assertEquals(42, deleted)
        verify(repository).purgeDeliveredOrSkippedOlderThan(any(Instant::class.java) ?: Instant.EPOCH, eq(100))
    }

    @Test
    fun `cleanupCompleted drains backlog across multiple batches`() {
        val cleanup = NotificationEmailDeliveryRetentionCleanup(
            repository = repository,
            clock = fixedClock,
            retentionDays = 7,
            batchSize = 100,
            maxBatches = 5
        )
        `when`(repository.purgeDeliveredOrSkippedOlderThan(any(Instant::class.java) ?: Instant.EPOCH, eq(100)))
            .thenReturn(100)
            .thenReturn(100)
            .thenReturn(25)

        val deleted = cleanup.cleanupCompleted()

        assertEquals(225, deleted)
    }

    @Test
    fun `validates retention and batch size parameters`() {
        assertThrows(IllegalArgumentException::class.java) {
            NotificationEmailDeliveryRetentionCleanup(repository, fixedClock, retentionDays = 0)
        }
        assertThrows(IllegalArgumentException::class.java) {
            NotificationEmailDeliveryRetentionCleanup(repository, fixedClock, retentionDays = -1)
        }
        assertThrows(IllegalArgumentException::class.java) {
            NotificationEmailDeliveryRetentionCleanup(repository, fixedClock, batchSize = 0)
        }
        assertThrows(IllegalArgumentException::class.java) {
            NotificationEmailDeliveryRetentionCleanup(repository, fixedClock, maxBatches = 0)
        }
    }
}
