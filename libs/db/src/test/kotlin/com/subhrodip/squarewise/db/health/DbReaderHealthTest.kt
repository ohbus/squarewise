package com.subhrodip.squarewise.db.health

import com.subhrodip.squarewise.db.errors.DbPlatformException
import com.subhrodip.squarewise.db.routing.DbExecutionContext
import com.subhrodip.squarewise.db.routing.DbOperationKind
import com.subhrodip.squarewise.db.routing.ReadConsistency
import com.subhrodip.squarewise.db.routing.DbWatermark
import java.time.Duration
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import org.mockito.Mockito.doThrow
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import javax.sql.DataSource

class DbReaderHealthTest {
    private val query = DbExecutionContext("expense.search", DbOperationKind.QUERY, ReadConsistency.EVENTUAL, readerEligible = true)
    private val strongQuery = query.copy(consistency = ReadConsistency.STRONG)

    @Test
    fun `fails closed after repeated reader failures`() {
        val health = DbReaderHealth(failureThreshold = 2, openDuration = Duration.ofSeconds(10))
        health.register("search")
        health.markFailure("search")
        assertEquals(DbReaderDecision.Fail, health.route(query, "search"))
        health.markFailure("search")
        assertEquals(DbReaderState.OPEN, health.state("search"))
        assertEquals(DbReaderDecision.Fail, health.route(query, "search"))
        assertEquals(DbReaderDecision.Writer, health.route(strongQuery, "search"))
    }

    @Test
    fun `recovery closes circuit and allows reader`() {
        val health = DbReaderHealth(failureThreshold = 1)
        health.markFailure("search")
        health.markHealthy("search")
        assertEquals(DbReaderState.HEALTHY, health.state("search"))
        assertEquals(DbReaderDecision.Reader, health.route(query, "search"))
    }

    @Test
    fun `reader cannot answer before required causal watermark`() {
        val health = DbReaderHealth()
        health.markHealthy("search", DbWatermark.parse("0/10"))
        val causal = query.copy(requiredWatermark = "0/20")
        assertEquals(DbReaderDecision.Fail, health.route(causal, "search"))
        health.markHealthy("search", DbWatermark.parse("0/20"))
        assertEquals(DbReaderDecision.Reader, health.route(causal, "search"))
    }

    /** Constructor guards reject configurations that could disable or busy-loop health checks. */
    @Test
    fun `health policy requires positive threshold and open duration`() {
        assertFailsWith<DbPlatformException> { DbReaderHealth(failureThreshold = 0) }
        assertFailsWith<DbPlatformException> { DbReaderHealth(openDuration = Duration.ZERO) }
        assertFailsWith<DbPlatformException> { DbReaderHealth(openDuration = Duration.ofSeconds(-1)) }
    }

    /** Lagging, disconnected, and unknown readers all fail closed for eventual reads. */
    @Test
    fun `unavailable reader states fail closed`() {
        val health = DbReaderHealth()
        health.register("search")

        health.markLagging("search")
        assertEquals(DbReaderState.LAGGING, health.state("search"))
        assertEquals(DbReaderDecision.Fail, health.route(query, "search"))

        health.markDisconnected("search")
        assertEquals(DbReaderState.DISCONNECTED, health.state("search"))
        assertEquals(DbReaderDecision.Fail, health.route(query, "search"))
        assertEquals(DbReaderState.DISCONNECTED, health.state("unknown"))
        assertEquals(DbReaderDecision.Fail, health.route(query, "unknown"))
    }

    /** An open circuit becomes probeable only after the configured recovery interval. */
    @Test
    fun `open circuit transitions to disconnected after timeout`() {
        val clock = MutableClock(Instant.parse("2026-01-01T00:00:00Z"))
        val health = DbReaderHealth(
            failureThreshold = 1,
            openDuration = Duration.ofSeconds(10),
            clock = clock,
        )
        health.markFailure("search")
        health.markFailure("search")
        assertEquals(DbReaderState.OPEN, health.state("search"))
        health.markLagging("search")
        assertEquals(DbReaderState.OPEN, health.state("search"))

        clock.now = clock.now.plusSeconds(11)
        assertEquals(DbReaderState.DISCONNECTED, health.state("search"))
    }

    /** Verifies the circuit remains open at the exact deadline and only transitions after it. */
    @Test
    fun `open circuit remains closed to probing at the exact deadline`() {
        val clock = MutableClock(Instant.parse("2026-01-01T00:00:00Z"))
        val health = DbReaderHealth(
            failureThreshold = 1,
            openDuration = Duration.ofSeconds(10),
            clock = clock,
        )

        health.markFailure("search")
        clock.now = clock.now.plusSeconds(10)
        assertEquals(DbReaderState.OPEN, health.state("search"))
        health.markFailure("search")
        assertEquals(DbReaderState.OPEN, health.state("search"))
    }

    /** A missing replay watermark is unsafe even when the reader otherwise appears healthy. */
    @Test
    fun `causal query fails when reader has not replayed a watermark`() {
        val health = DbReaderHealth()
        health.markHealthy("search")

        assertEquals(
            DbReaderDecision.Fail,
            health.route(query.copy(requiredWatermark = "0/20"), "search")
        )
    }

    /** Scheduler probes classify caught-up, lagging, null-lag, and failed readers independently. */
    @Test
    fun `scheduler applies probe outcomes without probing the writer`() {
        val health = DbReaderHealth()
        val probe = mock(DbReaderLagProbe::class.java)
        val caughtUp = mock(DataSource::class.java)
        val lagging = mock(DataSource::class.java)
        val unknownLag = mock(DataSource::class.java)
        val failed = mock(DataSource::class.java)
        `when`(probe.measureResult(caughtUp)).thenReturn(DbReaderProbeResult(10, DbWatermark.parse("0/20")))
        `when`(probe.measureResult(lagging)).thenReturn(DbReaderProbeResult(101, null))
        `when`(probe.measureResult(unknownLag)).thenReturn(DbReaderProbeResult(null, null))
        doThrow(IllegalStateException("probe failed")).`when`(probe).measureResult(failed)

        val scheduler = DbReaderHealthScheduler(
            readers = mapOf("caught-up" to caughtUp, "lagging" to lagging, "unknown" to unknownLag, "failed" to failed),
            health = health,
            lagBudgetMs = 100,
            probe = probe,
        )
        scheduler.probeReaders()

        assertEquals(DbReaderState.HEALTHY, health.state("caught-up"))
        assertEquals(DbReaderState.LAGGING, health.state("lagging"))
        assertEquals(DbReaderState.LAGGING, health.state("unknown"))
        assertEquals(DbReaderState.DISCONNECTED, health.state("failed"))
    }

    /** Scheduler construction rejects a non-positive lag budget. */
    @Test
    fun `scheduler requires a positive lag budget`() {
        assertFailsWith<DbPlatformException> {
            DbReaderHealthScheduler(emptyMap(), DbReaderHealth(), lagBudgetMs = 0)
        }
    }

    private class MutableClock(var now: Instant) : Clock() {
        override fun getZone(): ZoneId = ZoneId.of("UTC")

        override fun withZone(zone: ZoneId): Clock = this

        override fun instant(): Instant = now
    }

}
