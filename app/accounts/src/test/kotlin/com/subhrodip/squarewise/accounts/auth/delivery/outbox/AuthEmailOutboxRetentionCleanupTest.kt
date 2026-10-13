package com.subhrodip.squarewise.accounts.auth.delivery.outbox

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
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
    }
}
