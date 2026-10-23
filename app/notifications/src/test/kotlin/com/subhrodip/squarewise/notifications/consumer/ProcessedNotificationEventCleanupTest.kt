package com.subhrodip.squarewise.notifications.consumer

import com.subhrodip.squarewise.notifications.consumer.config.NotificationMessagingProperties
import com.subhrodip.squarewise.notifications.consumer.persistence.ProcessedNotificationEventEntity
import com.subhrodip.squarewise.notifications.consumer.persistence.ProcessedNotificationEventRepository
import com.subhrodip.squarewise.notifications.consumer.service.ProcessedNotificationEventCleanup
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.springframework.data.domain.PageRequest

/** Verifies bounded retention cleanup for notification deduplication markers. */
class ProcessedNotificationEventCleanupTest {
    @Test
    fun `deletes one bounded expired marker batch`() {
        val repository = mock(ProcessedNotificationEventRepository::class.java)
        val now = Instant.parse("2026-10-13T00:00:00Z")
        val cutoff = now.minus(Duration.ofDays(30))
        val properties = NotificationMessagingProperties(
            processedEventRetention = Duration.ofDays(30),
            processedEventCleanupBatchSize = 1
        )
        `when`(repository.purgeProcessedOlderThan(cutoff, 1)).thenReturn(1, 0)

        val cleanup = ProcessedNotificationEventCleanup(
            repository,
            properties,
            Clock.fixed(now, ZoneOffset.UTC)
        )

        assertEquals(1, cleanup.cleanupExpired())
        verify(repository, times(2)).purgeProcessedOlderThan(cutoff, 1)
    }

    @Test
    fun `drains multiple batches of expired markers`() {
        val repository = mock(ProcessedNotificationEventRepository::class.java)
        val now = Instant.parse("2026-10-13T00:00:00Z")
        val cutoff = now.minus(Duration.ofDays(30))
        val properties = NotificationMessagingProperties(
            processedEventRetention = Duration.ofDays(30),
            processedEventCleanupBatchSize = 1
        )
        `when`(repository.purgeProcessedOlderThan(cutoff, 1))
            .thenReturn(1)
            .thenReturn(1)
            .thenReturn(0)

        val cleanup = ProcessedNotificationEventCleanup(
            repository,
            properties,
            Clock.fixed(now, ZoneOffset.UTC),
            maxBatches = 5
        )

        assertEquals(2, cleanup.cleanupExpired())
        verify(repository, times(3)).purgeProcessedOlderThan(cutoff, 1)
    }

    @Test
    fun `drains batches until maxBatches reached without partial batch`() {
        val repository = mock(ProcessedNotificationEventRepository::class.java)
        val now = Instant.parse("2026-10-13T00:00:00Z")
        val cutoff = now.minus(Duration.ofDays(30))
        val properties = NotificationMessagingProperties(
            processedEventRetention = Duration.ofDays(30),
            processedEventCleanupBatchSize = 1
        )
        `when`(repository.purgeProcessedOlderThan(cutoff, 1)).thenReturn(1, 1)

        val cleanup = ProcessedNotificationEventCleanup(
            repository,
            properties,
            Clock.fixed(now, ZoneOffset.UTC),
            maxBatches = 2
        )

        assertEquals(2, cleanup.cleanupExpired())
        verify(repository, times(2)).purgeProcessedOlderThan(cutoff, 1)
    }

    @Test
    fun `rejects non-positive retention and batch settings`() {
        val repository = mock(ProcessedNotificationEventRepository::class.java)
        val clock = Clock.fixed(Instant.EPOCH, ZoneOffset.UTC)

        assertThrows(IllegalArgumentException::class.java) {
            ProcessedNotificationEventCleanup(
                repository,
                NotificationMessagingProperties(),
                clock,
                Duration.ZERO,
                1
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            ProcessedNotificationEventCleanup(
                repository,
                NotificationMessagingProperties(),
                clock,
                Duration.ofDays(-1),
                1
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            ProcessedNotificationEventCleanup(
                repository,
                NotificationMessagingProperties(),
                clock,
                Duration.ofDays(1),
                0
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            ProcessedNotificationEventCleanup(
                repository,
                NotificationMessagingProperties(),
                clock,
                Duration.ofDays(1),
                1,
                0
            )
        }
    }
}
