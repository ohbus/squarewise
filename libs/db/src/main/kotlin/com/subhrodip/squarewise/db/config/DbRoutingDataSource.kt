package com.subhrodip.squarewise.db.config

import com.subhrodip.squarewise.db.routing.DbRoute
import com.subhrodip.squarewise.db.routing.DbContextHolder
import com.subhrodip.squarewise.db.errors.DbPlatformException
import com.subhrodip.squarewise.db.health.DbReaderDecision
import com.subhrodip.squarewise.db.health.DbReaderHealth
import com.subhrodip.squarewise.db.policy.DbRouteGuard
import com.subhrodip.squarewise.errors.catalog.PlatformErrors
import com.subhrodip.squarewise.observability.db.DbTelemetry
import java.sql.Connection
import java.sql.SQLException
import java.util.concurrent.atomic.AtomicInteger
import javax.sql.DataSource
import org.springframework.jdbc.datasource.AbstractDataSource

import com.subhrodip.squarewise.db.health.DbReaderState

/** Routes connections to the writer or a named read pool at acquisition time. */
class DbRoutingDataSource(
    private val writer: DataSource,
    private val readers: Map<String, DataSource>,
    private val readerHealth: DbReaderHealth = DbReaderHealth(),
    private val telemetry: DbTelemetry = DbTelemetry(),
    private val routeGuard: DbRouteGuard = DbRouteGuard(),
    private val writers: Map<String, DataSource> = emptyMap()
) : AbstractDataSource() {
    private val roundRobinCounter = AtomicInteger(0)

    init { readers.keys.forEach(readerHealth::register) }

    override fun getConnection(): Connection {
        val (route, readerName) = routeForCurrentContext()
        return connection(route, readerName)
    }

    override fun getConnection(username: String?, password: String?): Connection {
        val (route, readerName) = routeForCurrentContext()
        return connection(route, readerName)
    }

    /** Returns a connection for an explicit route. */
    fun connection(route: DbRoute, readerName: String = resolveReaderName()): Connection {
        val context = DbContextHolder.current()
        routeGuard.validate(context, route)
        val operation = context.operationName
        if (route == DbRoute.WRITER) {
            val started = System.nanoTime()
            val writerName = resolveWriterName()
            val selectedWriter = if (writerName.isNotBlank()) writers[writerName] ?: writer else writer
            val poolTag = if (writerName.isNotBlank()) "writer.$writerName" else "writer"
            return try { selectedWriter.connection } finally {
                telemetry.route(operation, poolTag)
                telemetry.acquisition(operation, poolTag, (System.nanoTime() - started) / 1_000_000)
            }
        }
        val targetName = if (readerName.isNotBlank()) readerName else resolveReaderName()
        val reader = readers[targetName] ?: throw DbPlatformException(
            PlatformErrors.PLATFORM_CONFIGURATION_INVALID,
            "No configured reader pool is available"
        )
        return try {
            val started = System.nanoTime()
            try {
                reader.connection
                    .also { telemetry.route(operation, "reader") }
            } finally {
                telemetry.acquisition(operation, "reader", (System.nanoTime() - started) / 1_000_000)
            }
        } catch (failure: SQLException) {
            readerHealth.markFailure(targetName)
            telemetry.failure(targetName)
            throw DbPlatformException(PlatformErrors.DATABASE_UNAVAILABLE, cause = failure)
        }
    }

    /** Returns the configured reader pools for health probing and diagnostics. */
    fun readerDataSources(): Map<String, DataSource> = readers.toMap()

    /** Returns the configured writer pools for diagnostics. */
    fun writerDataSources(): Map<String, DataSource> = writers.toMap()

    /** Resolves only an explicitly selected writer; unqualified writes use the primary pool. */
    fun resolveWriterName(): String {
        val context = DbContextHolder.current()
        val target = context.targetWriter?.takeIf { it.isNotBlank() }
        if (target != null) {
            if (target !in writers) {
                throw DbPlatformException(
                    PlatformErrors.PLATFORM_CONFIGURATION_INVALID,
                    "Unknown writer target: $target"
                )
            }
            return target
        }
        return ""
    }

    /** Resolves the target reader pool name based on context target or round-robin among healthy readers, with automatic failover. */
    fun resolveReaderName(): String {
        val context = DbContextHolder.current()
        val target = context.targetReader?.takeIf { it.isNotBlank() }
        if (target != null && readerHealth.state(target) == DbReaderState.HEALTHY) {
            return target
        }
        val healthyCandidates = readers.keys.filter {
            readerHealth.state(it) == DbReaderState.HEALTHY &&
                readerHealth.route(context, it) == DbReaderDecision.Reader
        }
        if (healthyCandidates.isNotEmpty()) {
            val idx = (roundRobinCounter.getAndIncrement() and Int.MAX_VALUE) % healthyCandidates.size
            return healthyCandidates[idx]
        }
        return target ?: readers.keys.firstOrNull() ?: ""
    }

    private fun routeForCurrentContext(): Pair<DbRoute, String> {
        val context = DbContextHolder.current()
        val readerName = resolveReaderName()
        val decision = readerHealth.route(context, readerName)
        val route = when (decision) {
            DbReaderDecision.Reader -> DbRoute.READER
            DbReaderDecision.Writer -> DbRoute.WRITER
            DbReaderDecision.Fail -> if (!context.isWriterOnly()) {
                telemetry.fallback(context.operationName)
                DbRoute.WRITER
            } else {
                throw DbPlatformException(
                    PlatformErrors.DATABASE_UNAVAILABLE,
                    "No healthy reader is available"
                )
            }
        }
        return route to readerName
    }
}
