package com.subhrodip.squarewise.db.config
import com.subhrodip.squarewise.db.errors.DbPlatformException
import com.subhrodip.squarewise.db.health.DbReaderState
import com.subhrodip.squarewise.db.health.DbReaderHealth

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
}
