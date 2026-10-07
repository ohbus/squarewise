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
import javax.sql.DataSource
import org.springframework.jdbc.datasource.AbstractDataSource

/** Routes connections to the writer or a named read pool at acquisition time. */
class DbRoutingDataSource(
    private val writer: DataSource,
    private val readers: Map<String, DataSource>,
    private val readerHealth: DbReaderHealth = DbReaderHealth(),
    private val telemetry: DbTelemetry = DbTelemetry(),
    private val routeGuard: DbRouteGuard = DbRouteGuard()
) : AbstractDataSource() {
    init { readers.keys.forEach(readerHealth::register) }

    override fun getConnection(): Connection = connection(routeForCurrentContext())

    override fun getConnection(username: String?, password: String?): Connection = connection(routeForCurrentContext())

    /** Returns a connection for an explicit route. */
    fun connection(route: DbRoute, readerName: String = readers.keys.firstOrNull() ?: ""): Connection {
        val context = DbContextHolder.current()
        routeGuard.validate(context, route)
        val operation = context.operationName
        if (route == DbRoute.WRITER) {
            val started = System.nanoTime()
            return try { writer.connection } finally {
                telemetry.route(operation, "writer")
                telemetry.acquisition(operation, "writer", (System.nanoTime() - started) / 1_000_000)
            }
        }
        val reader = readers[readerName] ?: throw DbPlatformException(
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
            readerHealth.markFailure(readerName)
            telemetry.failure(readerName)
            throw DbPlatformException(PlatformErrors.DATABASE_UNAVAILABLE, cause = failure)
        }
    }

    /** Returns the configured reader pools for health probing and diagnostics. */
    fun readerDataSources(): Map<String, DataSource> = readers.toMap()

    private fun routeForCurrentContext(): DbRoute = when (readerHealth.route(DbContextHolder.current(), readers.keys.firstOrNull() ?: "")) {
        DbReaderDecision.Reader -> DbRoute.READER
        DbReaderDecision.Writer -> DbRoute.WRITER
        DbReaderDecision.Fail -> throw DbPlatformException(
            PlatformErrors.DATABASE_UNAVAILABLE,
            "No healthy reader is available"
        )
    }
}
