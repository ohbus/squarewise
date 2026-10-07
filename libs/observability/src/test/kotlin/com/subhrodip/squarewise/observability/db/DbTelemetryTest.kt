package com.subhrodip.squarewise.observability.db

import com.subhrodip.squarewise.observability.errors.ObservabilityPlatformException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import io.micrometer.core.instrument.simple.SimpleMeterRegistry

class DbTelemetryTest {

    @Test
    fun `measureQuery records successful and failed blocks`() {
        val telemetry = DbTelemetry(slowQueryThresholdMs = 10)

        telemetry.route("groups", "writer")
        telemetry.failure("reader")
        telemetry.acquisition("groups", "writer", -5)
        telemetry.lockWait("groups")
        telemetry.deadlock("expenses")
        telemetry.lag("reader", 12)

        assertEquals("value", telemetry.measureQuery("groups", "writer") { "value" })
        assertThrows(IllegalStateException::class.java) {
            telemetry.measureQuery("expenses", "reader") {
                throw IllegalStateException("query failed")
            }
        }

        val snapshot = telemetry.snapshot()
        assertEquals(1, snapshot.failures)
        assertEquals(1, snapshot.acquisitions)
        assertEquals(0, snapshot.acquisitionTotalMs)
        assertEquals(1, snapshot.lockWaits)
        assertEquals(1, snapshot.deadlocks)
        assertEquals(2, snapshot.queries)
        assertEquals(0, snapshot.slowQueries)
        assertTrue(snapshot.queryTotalMs >= 0)

        telemetry.queryDuration("slow", "reader", 10)
        assertEquals(1, telemetry.snapshot().slowQueries)
    }

    /** Verifies registry-backed metrics retain bounded labels and exact event counts. */
    @Test
    fun `publishes bounded registry metrics for every observation`() {
        val registry = SimpleMeterRegistry()
        val telemetry = DbTelemetry(registry, slowQueryThresholdMs = 10)

        telemetry.route("groups", "writer")
        telemetry.failure("reader")
        telemetry.acquisition("groups", "writer", 7)
        telemetry.queryDuration("groups", "writer", -1)
        telemetry.queryDuration("groups", "reader", 10)
        telemetry.lockWait("groups")
        telemetry.deadlock("groups")
        telemetry.lag("reader", 12)

        assertEquals(
            1.0,
            registry.counter("squarewise.db.route", "operation", "groups", "route", "writer").count()
        )
        assertEquals(
            1.0,
            registry.counter("squarewise.db.reader.failure", "reader", "reader").count()
        )
        assertEquals(
            1L,
            registry.timer("squarewise.db.pool.acquisition", "operation", "groups", "pool", "writer").count()
        )
        assertEquals(
            1L,
            registry.timer("squarewise.db.query.duration", "operation", "groups", "route", "writer").count()
        )
        assertEquals(
            1.0,
            registry.counter("squarewise.db.query.slow", "operation", "groups").count()
        )
        assertEquals(
            1.0,
            registry.counter("squarewise.db.lock.wait", "operation", "groups").count()
        )
        assertEquals(
            1.0,
            registry.counter("squarewise.db.deadlock", "operation", "groups").count()
        )
        assertEquals(
            12.0,
            registry.get("squarewise.db.reader.lag.ms").tag("reader", "reader").gauge().value()
        )
        assertEquals(2L, telemetry.snapshot().queries)
        assertEquals(1, telemetry.snapshot().slowQueries)
    }

    @Test
    fun `rejects a non-positive slow query threshold`() {
        assertThrows(ObservabilityPlatformException::class.java) {
            DbTelemetry(slowQueryThresholdMs = 0)
        }
    }

    @Test
    fun `default telemetry starts with empty counters without a registry`() {
        val telemetry = DbTelemetry()

        assertEquals(0, telemetry.snapshot().failures)
        assertEquals(0, telemetry.snapshot().queries)
        assertEquals(0, telemetry.snapshot().jdbcStatements)
        telemetry.route("groups", "writer")
        telemetry.lag("reader", 0)
        telemetry.jdbcStatement("groups.list")
        assertEquals(0, telemetry.snapshot().queries)
        assertEquals(1, telemetry.snapshot().jdbcStatements)
    }

    @Test
    fun `records bounded JDBC statement telemetry`() {
        val registry = SimpleMeterRegistry()
        val telemetry = DbTelemetry(registry)

        telemetry.jdbcStatement("groups.list")

        assertEquals(1, telemetry.snapshot().jdbcStatements)
        assertEquals(
            1.0,
            registry.counter("squarewise.db.statement", "operation", "groups.list").count()
        )
    }

    @Test
    fun `snapshot defaults every optional counter to zero`() {
        val snapshot = DbTelemetrySnapshot(failures = 2)

        assertEquals(2, snapshot.failures)
        assertEquals(0, snapshot.acquisitions)
        assertEquals(0, snapshot.acquisitionTotalMs)
        assertEquals(0, snapshot.lockWaits)
        assertEquals(0, snapshot.deadlocks)
        assertEquals(0, snapshot.queries)
        assertEquals(0, snapshot.queryTotalMs)
        assertEquals(0, snapshot.slowQueries)
    }

    @Test
    fun `acquisition and queryDuration handle null Timer gracefully`() {
        val mockRegistry = org.mockito.Mockito.mock(io.micrometer.core.instrument.MeterRegistry::class.java)
        org.mockito.Mockito.`when`(
            mockRegistry.timer(
                org.mockito.ArgumentMatchers.eq("squarewise.db.pool.acquisition"),
                org.mockito.ArgumentMatchers.any(io.micrometer.core.instrument.Tags::class.java)
            )
        ).thenReturn(null)
        org.mockito.Mockito.`when`(
            mockRegistry.timer(
                org.mockito.ArgumentMatchers.eq("squarewise.db.query.duration"),
                org.mockito.ArgumentMatchers.any(io.micrometer.core.instrument.Tags::class.java)
            )
        ).thenReturn(null)

        val telemetry = DbTelemetry(mockRegistry, slowQueryThresholdMs = 10)
        telemetry.acquisition("groups", "writer", 5)
        telemetry.queryDuration("groups", "writer", 5)

        val snapshot = telemetry.snapshot()
        assertEquals(1, snapshot.acquisitions)
        assertEquals(5, snapshot.acquisitionTotalMs)
        assertEquals(1, snapshot.queries)
        assertEquals(5, snapshot.queryTotalMs)
    }
}
