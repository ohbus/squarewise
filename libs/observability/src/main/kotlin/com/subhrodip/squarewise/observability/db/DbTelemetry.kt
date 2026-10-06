package com.subhrodip.squarewise.observability.db

import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Tags
import java.util.concurrent.atomic.AtomicLong
import java.time.Duration

/** Low-cardinality database route and reader-health telemetry. */
class DbTelemetry(
    private val registry: MeterRegistry? = null,
    private val slowQueryThresholdMs: Long = 500
) {
    init { require(slowQueryThresholdMs >= 1) { "slowQueryThresholdMs must be positive" } }
    private val failureCount = AtomicLong()
    private val acquisitionCount = AtomicLong()
    private val acquisitionTotalMs = AtomicLong()
    private val lockWaitCount = AtomicLong()
    private val deadlockCount = AtomicLong()
    private val queryCount = AtomicLong()
    private val queryTotalMs = AtomicLong()
    private val slowQueryCount = AtomicLong()
    private val jdbcStatementCount = AtomicLong()

    /** Records a connection route for a validated operation name. */
    fun route(operation: String, route: String) {
        registry?.counter("squarewise.db.route", Tags.of("operation", operation, "route", route))?.increment()
    }

    /** Records a reader connection/probe failure. */
    fun failure(reader: String) {
        failureCount.incrementAndGet()
        registry?.counter("squarewise.db.reader.failure", "reader", reader)?.increment()
    }

    /** Records bounded pool-acquisition timing without SQL text or parameters. */
    fun acquisition(operation: String, pool: String, durationMs: Long) {
        acquisitionCount.incrementAndGet()
        acquisitionTotalMs.addAndGet(durationMs.coerceAtLeast(0))
        registry?.timer("squarewise.db.pool.acquisition", Tags.of("operation", operation, "pool", pool))
            ?.record(Duration.ofMillis(durationMs.coerceAtLeast(0)))
    }

    /** Records query duration using only stable operation and route labels. */
    fun queryDuration(operation: String, route: String, durationMs: Long) {
        val boundedDuration = durationMs.coerceAtLeast(0)
        queryCount.incrementAndGet()
        queryTotalMs.addAndGet(boundedDuration)
        registry?.timer("squarewise.db.query.duration", Tags.of("operation", operation, "route", route))
            ?.record(Duration.ofMillis(boundedDuration))
        if (boundedDuration >= slowQueryThresholdMs) {
            slowQueryCount.incrementAndGet()
            registry?.counter("squarewise.db.query.slow", "operation", operation)?.increment()
        }
    }

    /** Records one Hibernate/JDBC statement without retaining SQL text or parameters. */
    fun jdbcStatement(operation: String) {
        jdbcStatementCount.incrementAndGet()
        registry?.counter("squarewise.db.statement", "operation", operation)?.increment()
    }

    /** Measures one bounded query block without capturing SQL, parameters, or payloads. */
    inline fun <T> measureQuery(operation: String, route: String, block: () -> T): T {
        val started = System.nanoTime()
        return try {
            block()
        } finally {
            queryDuration(operation, route, (System.nanoTime() - started) / 1_000_000)
        }
    }

    /** Records a lock-wait observation using only the stable operation label. */
    fun lockWait(operation: String) {
        lockWaitCount.incrementAndGet()
        registry?.counter("squarewise.db.lock.wait", "operation", operation)?.increment()
    }

    /** Records a deadlock observation using only the stable operation label. */
    fun deadlock(operation: String) {
        deadlockCount.incrementAndGet()
        registry?.counter("squarewise.db.deadlock", "operation", operation)?.increment()
    }

    /** Records the latest replay lag in milliseconds for a named reader. */
    fun lag(reader: String, lagMs: Long) {
        registry?.gauge("squarewise.db.reader.lag.ms", Tags.of("reader", reader), lagMs)
    }

    /** Snapshot counters useful to tests and diagnostic endpoints. */
    fun snapshot(): DbTelemetrySnapshot = DbTelemetrySnapshot(
        failures = failureCount.get(),
        acquisitions = acquisitionCount.get(),
        acquisitionTotalMs = acquisitionTotalMs.get(),
        lockWaits = lockWaitCount.get(),
        deadlocks = deadlockCount.get(),
        queries = queryCount.get(),
        queryTotalMs = queryTotalMs.get(),
        slowQueries = slowQueryCount.get(),
        jdbcStatements = jdbcStatementCount.get()
    )
}
