package com.subhrodip.squarewise.db.config

import com.subhrodip.squarewise.db.errors.DbPlatformException
import com.subhrodip.squarewise.db.health.DbReaderState
import com.subhrodip.squarewise.db.health.DbReaderHealth
import com.subhrodip.squarewise.db.health.DbReaderDecision

import com.subhrodip.squarewise.db.routing.DbContextHolder
import com.subhrodip.squarewise.db.routing.DbExecutionContext
import com.subhrodip.squarewise.db.routing.DbOperationKind
import com.subhrodip.squarewise.db.routing.DbRoute
import com.subhrodip.squarewise.db.routing.ReadConsistency
import java.sql.Connection
import java.sql.SQLException
import javax.sql.DataSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertFailsWith
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`

/** Verifies that explicit datasource routes cannot bypass operation safety policy. */
class DbRoutingDataSourceTest {
    @Test
    fun `writer-only context rejects explicit reader connection`() {
        val writer = mock(DataSource::class.java)
        val reader = mock(DataSource::class.java)
        val routing = DbRoutingDataSource(writer, mapOf("replica" to reader))

        assertFailsWith<DbPlatformException> {
            DbContextHolder.withContext(DbExecutionContext("expense.create", DbOperationKind.COMMAND)) {
                routing.connection(DbRoute.READER, "replica")
            }
        }
    }

    @Test
    fun `approved eventual query may acquire explicit reader connection`() {
        val writer = mock(DataSource::class.java)
        val reader = mock(DataSource::class.java)
        val connection = mock(Connection::class.java)
        `when`(reader.connection).thenReturn(connection)
        val routing = DbRoutingDataSource(writer, mapOf("replica" to reader))

        DbContextHolder.withContext(
            DbExecutionContext("expense.search", DbOperationKind.QUERY, ReadConsistency.EVENTUAL, readerEligible = true)
        ) {
            assertSame(connection, routing.connection(DbRoute.READER, "replica"))
        }
    }

    @Test
    fun `reader connection defaults to the first configured reader`() {
        val writer = mock(DataSource::class.java)
        val reader = mock(DataSource::class.java)
        val connection = mock(Connection::class.java)
        `when`(reader.connection).thenReturn(connection)
        val routing = DbRoutingDataSource(writer, mapOf("replica" to reader))

        DbContextHolder.withContext(
            DbExecutionContext("expense.search", DbOperationKind.QUERY, ReadConsistency.EVENTUAL, readerEligible = true)
        ) {
            assertSame(connection, routing.connection(DbRoute.READER))
        }
    }

    @Test
    fun `multi-reader connection load balances round-robin across healthy replicas`() {
        val writer = mock(DataSource::class.java)
        val reader1 = mock(DataSource::class.java)
        val reader2 = mock(DataSource::class.java)
        val conn1 = mock(Connection::class.java)
        val conn2 = mock(Connection::class.java)
        `when`(reader1.connection).thenReturn(conn1)
        `when`(reader2.connection).thenReturn(conn2)
        val routing = DbRoutingDataSource(writer, linkedMapOf("replica1" to reader1, "replica2" to reader2))

        DbContextHolder.withContext(
            DbExecutionContext("expense.search", DbOperationKind.QUERY, ReadConsistency.EVENTUAL, readerEligible = true)
        ) {
            val first = routing.resolveReaderName()
            val second = routing.resolveReaderName()
            val third = routing.resolveReaderName()
            assertEquals("replica1", first)
            assertEquals("replica2", second)
            assertEquals("replica1", third)
        }
    }

    /** Default connection acquisition routes writer-only work to the writer pool. */
    @Test
    fun `default connection acquisition uses writer for command`() {
        val writer = mock(DataSource::class.java)
        val connection = mock(Connection::class.java)
        `when`(writer.connection).thenReturn(connection)
        val routing = DbRoutingDataSource(writer, emptyMap())

        DbContextHolder.withContext(DbExecutionContext("expense.create", DbOperationKind.COMMAND)) {
            assertSame(connection, routing.getConnection())
        }
    }

    @Test
    fun `credentialed connection acquisition follows the current route`() {
        val writer = mock(DataSource::class.java)
        val connection = mock(Connection::class.java)
        `when`(writer.connection).thenReturn(connection)
        val routing = DbRoutingDataSource(writer, emptyMap())

        DbContextHolder.withContext(DbExecutionContext("expense.create", DbOperationKind.COMMAND)) {
            assertSame(connection, routing.getConnection("user", "password"))
        }
    }

    /** Missing reader names and reader JDBC failures fail closed with observable diagnostics. */
    @Test
    fun `reader acquisition rejects unknown and failed pools`() {
        val writer = mock(DataSource::class.java)
        val reader = mock(DataSource::class.java)
        val health = DbReaderHealth(failureThreshold = 1)
        val routing = DbRoutingDataSource(writer, mapOf("replica" to reader), health)
        val context = DbExecutionContext("expense.search", DbOperationKind.QUERY, ReadConsistency.EVENTUAL, true)

        DbContextHolder.withContext(context) {
            assertFailsWith<DbPlatformException> { routing.connection(DbRoute.READER, "missing") }
            `when`(reader.connection).thenThrow(SQLException("reader unavailable"))
            assertFailsWith<DbPlatformException> { routing.connection(DbRoute.READER, "replica") }
        }
        assertEquals(DbReaderState.OPEN, health.state("replica"))
    }

    /** A failed reader circuit prevents implicit query acquisition until recovery. */
    @Test
    fun `implicit query routing fails when reader circuit is open`() {
        val writer = mock(DataSource::class.java)
        val reader = mock(DataSource::class.java)
        val health = DbReaderHealth(failureThreshold = 1)
        health.markFailure("replica")
        val routing = DbRoutingDataSource(writer, mapOf("replica" to reader), health)

        DbContextHolder.withContext(
            DbExecutionContext("expense.search", DbOperationKind.QUERY, ReadConsistency.EVENTUAL, readerEligible = true)
        ) {
            assertFailsWith<DbPlatformException> { routing.getConnection() }
        }
    }

    @Test
    fun `implicit healthy eventual query acquires the first reader`() {
        val writer = mock(DataSource::class.java)
        val reader = mock(DataSource::class.java)
        val connection = mock(Connection::class.java)
        `when`(reader.connection).thenReturn(connection)
        val routing = DbRoutingDataSource(writer, mapOf("replica" to reader))

        DbContextHolder.withContext(
            DbExecutionContext("expense.search", DbOperationKind.QUERY, ReadConsistency.EVENTUAL, readerEligible = true)
        ) {
            assertSame(connection, routing.getConnection())
        }
    }

    @Test
    fun `implicit eventual query fails closed when no readers are configured`() {
        val routing = DbRoutingDataSource(mock(DataSource::class.java), emptyMap())

        DbContextHolder.withContext(
            DbExecutionContext("expense.search", DbOperationKind.QUERY, ReadConsistency.EVENTUAL, readerEligible = true)
        ) {
            assertFailsWith<DbPlatformException> { routing.getConnection() }
        }
    }

    @Test
    fun `implicit query routes to targeted reader when targetReader is specified`() {
        val writer = mock(DataSource::class.java)
        val reader1 = mock(DataSource::class.java)
        val reader2 = mock(DataSource::class.java)
        val conn2 = mock(Connection::class.java)
        `when`(reader2.connection).thenReturn(conn2)
        val routing = DbRoutingDataSource(writer, mapOf("replica-1" to reader1, "replica-2" to reader2))

        DbContextHolder.withContext(
            DbExecutionContext(
                operationName = "expense.search",
                kind = DbOperationKind.QUERY,
                consistency = ReadConsistency.EVENTUAL,
                readerEligible = true,
                targetReader = "replica-2"
            )
        ) {
            assertSame(conn2, routing.getConnection())
        }
    }

    @Test
    fun `implicit query falls back to healthy reader when targeted reader is unhealthy`() {
        val writer = mock(DataSource::class.java)
        val reader1 = mock(DataSource::class.java)
        val reader2 = mock(DataSource::class.java)
        val conn2 = mock(Connection::class.java)
        `when`(reader2.connection).thenReturn(conn2)
        val health = DbReaderHealth(failureThreshold = 1)
        health.markFailure("replica-1")
        val routing = DbRoutingDataSource(writer, mapOf("replica-1" to reader1, "replica-2" to reader2), health)

        DbContextHolder.withContext(
            DbExecutionContext(
                operationName = "expense.search",
                kind = DbOperationKind.QUERY,
                consistency = ReadConsistency.EVENTUAL,
                readerEligible = true,
                targetReader = "replica-1"
            )
        ) {
            assertSame(conn2, routing.getConnection())
        }
    }

    @Test
    fun `getConnection with credentials routes to appropriate datasource`() {
        val writer = mock(DataSource::class.java)
        val conn = mock(Connection::class.java)
        `when`(writer.connection).thenReturn(conn)
        val routing = DbRoutingDataSource(writer, emptyMap())

        DbContextHolder.withContext(DbExecutionContext("expense.create", DbOperationKind.COMMAND)) {
            assertSame(conn, routing.getConnection("user", "pass"))
        }
    }

    @Test
    fun `explicit connection with blank readerName resolves reader automatically`() {
        val writer = mock(DataSource::class.java)
        val reader = mock(DataSource::class.java)
        val conn = mock(Connection::class.java)
        `when`(reader.connection).thenReturn(conn)
        val routing = DbRoutingDataSource(writer, mapOf("replica" to reader))

        DbContextHolder.withContext(
            DbExecutionContext("expense.search", DbOperationKind.QUERY, ReadConsistency.EVENTUAL, readerEligible = true)
        ) {
            assertSame(conn, routing.connection(DbRoute.READER, ""))
        }
    }

    @Test
    fun `readerDataSources exposes map of configured reader datasources`() {
        val writer = mock(DataSource::class.java)
        val reader = mock(DataSource::class.java)
        val routing = DbRoutingDataSource(writer, mapOf("replica" to reader))

        assertEquals(mapOf("replica" to reader), routing.readerDataSources())
    }

    @Test
    fun `throws database unavailable when reader health decision is Fail`() {
        val writer = mock(DataSource::class.java)
        val reader = mock(DataSource::class.java)
        val health = mock(DbReaderHealth::class.java)
        val context = DbExecutionContext("expense.search", DbOperationKind.QUERY, ReadConsistency.EVENTUAL, readerEligible = true)
        `when`(health.route(context, "replica")).thenReturn(DbReaderDecision.Fail)
        val routing = DbRoutingDataSource(writer, mapOf("replica" to reader), health)

        DbContextHolder.withContext(context) {
            assertFailsWith<DbPlatformException> { routing.getConnection() }
        }
    }

    @Test
    fun `resolveReaderName returns target when target is unhealthy and no healthy replicas exist`() {
        val writer = mock(DataSource::class.java)
        val reader = mock(DataSource::class.java)
        val health = mock(DbReaderHealth::class.java)
        `when`(health.state("unhealthy-replica")).thenReturn(DbReaderState.DISCONNECTED)
        val routing = DbRoutingDataSource(writer, mapOf("unhealthy-replica" to reader), health)
        DbContextHolder.withContext(
            DbExecutionContext("query", DbOperationKind.QUERY, targetReader = "unhealthy-replica")
        ) {
            assertEquals("unhealthy-replica", routing.resolveReaderName())
        }
    }

    @Test
    fun `resolveReaderName returns first configured reader when target is blank and all replicas are unhealthy`() {
        val writer = mock(DataSource::class.java)
        val reader = mock(DataSource::class.java)
        val health = mock(DbReaderHealth::class.java)
        `when`(health.state("replica")).thenReturn(DbReaderState.DISCONNECTED)
        val routing = DbRoutingDataSource(writer, mapOf("replica" to reader), health)
        DbContextHolder.withContext(
            DbExecutionContext("query", DbOperationKind.QUERY, targetReader = "   ")
        ) {
            assertEquals("replica", routing.resolveReaderName())
        }
    }

    @Test
    fun `resolveReaderName returns empty string when no readers are configured`() {
        val writer = mock(DataSource::class.java)
        val routing = DbRoutingDataSource(writer, emptyMap())
        DbContextHolder.withContext(DbExecutionContext("query", DbOperationKind.QUERY)) {
            assertEquals("", routing.resolveReaderName())
        }
    }

    @Test
    fun `resolveReaderName filters out candidates not meeting watermark eligibility`() {
        val writer = mock(DataSource::class.java)
        val reader1 = mock(DataSource::class.java)
        val reader2 = mock(DataSource::class.java)
        val health = mock(DbReaderHealth::class.java)
        val context = DbExecutionContext("query", DbOperationKind.QUERY)
        `when`(health.state("r1")).thenReturn(DbReaderState.HEALTHY)
        `when`(health.state("r2")).thenReturn(DbReaderState.HEALTHY)
        `when`(health.route(context, "r1")).thenReturn(DbReaderDecision.Fail)
        `when`(health.route(context, "r2")).thenReturn(DbReaderDecision.Reader)
        val routing = DbRoutingDataSource(writer, linkedMapOf("r1" to reader1, "r2" to reader2), health)
        DbContextHolder.withContext(context) {
            assertEquals("r2", routing.resolveReaderName())
        }
    }
}

