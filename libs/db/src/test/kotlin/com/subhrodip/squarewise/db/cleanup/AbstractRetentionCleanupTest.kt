package com.subhrodip.squarewise.db.cleanup

import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/** Unit tests for [AbstractRetentionCleanup]. */
class AbstractRetentionCleanupTest {

    private val fixedInstant = Instant.parse("2026-10-13T12:00:00Z")
    private val fixedClock = Clock.fixed(fixedInstant, ZoneOffset.UTC)

    private class TestRetentionCleanup(
        clock: Clock,
        retention: Duration = DEFAULT_RETENTION,
        batchSize: Int = DEFAULT_BATCH_SIZE,
        maxBatches: Int = DEFAULT_MAX_BATCHES,
        private val batchResults: List<Int> = listOf(0)
    ) : AbstractRetentionCleanup(
        clock = clock,
        retention = retention,
        batchSize = batchSize,
        maxBatches = maxBatches,
        subjectName = "test-retention"
    ) {
        private var callIndex = 0
        var recordedCutoff: Instant? = null
        var recordedLimit: Int? = null

        fun run(): Int = executeBatchCleanup()

        override fun purgeBatch(cutoff: Instant, limit: Int): Int {
            recordedCutoff = cutoff
            recordedLimit = limit
            val result = if (callIndex < batchResults.size) batchResults[callIndex] else 0
            callIndex++
            return result
        }
    }

    @Test
    fun `executeBatchCleanup purges single batch when deleted count is less than batch size`() {
        val cleanup = TestRetentionCleanup(
            clock = fixedClock,
            retention = Duration.ofDays(7),
            batchSize = 100,
            batchResults = listOf(42)
        )

        val total = cleanup.run()

        assertEquals(42, total)
        assertEquals(fixedInstant.minus(Duration.ofDays(7)), cleanup.recordedCutoff)
        assertEquals(100, cleanup.recordedLimit)
    }

    @Test
    fun `executeBatchCleanup drains backlog across multiple batches until partial batch`() {
        val cleanup = TestRetentionCleanup(
            clock = fixedClock,
            retention = Duration.ofDays(7),
            batchSize = 100,
            maxBatches = 5,
            batchResults = listOf(100, 100, 35)
        )

        val total = cleanup.run()

        assertEquals(235, total)
    }

    @Test
    fun `validates constructor parameters`() {
        assertFailsWith<IllegalArgumentException> {
            TestRetentionCleanup(clock = fixedClock, retention = Duration.ZERO)
        }
        assertFailsWith<IllegalArgumentException> {
            TestRetentionCleanup(clock = fixedClock, retention = Duration.ofDays(-1))
        }
        assertFailsWith<IllegalArgumentException> {
            TestRetentionCleanup(clock = fixedClock, batchSize = 0)
        }
        assertFailsWith<IllegalArgumentException> {
            TestRetentionCleanup(clock = fixedClock, maxBatches = 0)
        }
    }
}
