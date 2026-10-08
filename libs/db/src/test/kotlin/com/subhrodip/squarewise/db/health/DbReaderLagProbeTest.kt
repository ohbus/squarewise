package com.subhrodip.squarewise.db.health

import org.mockito.ArgumentMatchers.anyString

import com.subhrodip.squarewise.db.routing.DbWatermark
import java.sql.Connection
import java.sql.PreparedStatement
import java.sql.ResultSet
import javax.sql.DataSource
import kotlin.test.Test
import kotlin.test.assertEquals
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock

/** Verifies the JDBC replay-lag probe maps every supported PostgreSQL result shape. */
class DbReaderLagProbeTest {

    /** An empty probe result is represented as unknown lag and unknown replay position. */
    @Test
    fun `empty result set returns unknown probe result`() {
        val result = mock(ResultSet::class.java)
        `when`(result.next()).thenReturn(false)

        val probeResult = probeResult(result)

        assertEquals(null, probeResult.lagMs)
        assertEquals(null, probeResult.replayedWatermark)
    }

    /** SQL NULL lag and LSN values remain unknown rather than becoming zero or a fake watermark. */
    @Test
    fun `null replay values remain unknown`() {
        val result = mock(ResultSet::class.java)
        `when`(result.next()).thenReturn(true)
        `when`(result.getLong(1)).thenReturn(0L)
        `when`(result.wasNull()).thenReturn(true)
        `when`(result.getString(2)).thenReturn(null)

        val probeResult = probeResult(result)

        assertEquals(null, probeResult.lagMs)
        assertEquals(null, probeResult.replayedWatermark)
    }

    /** A populated PostgreSQL result maps lag milliseconds and the causal LSN exactly. */
    @Test
    fun `populated replay values are returned`() {
        val result = mock(ResultSet::class.java)
        `when`(result.next()).thenReturn(true)
        `when`(result.getLong(1)).thenReturn(125L)
        `when`(result.wasNull()).thenReturn(false)
        `when`(result.getString(2)).thenReturn("0/20")

        val probeResult = probeResult(result)

        assertEquals(125L, probeResult.lagMs)
        assertEquals(DbWatermark.parse("0/20"), probeResult.replayedWatermark)
    }

    @Test
    fun `measure exposes the replay lag from the probe result`() {
        val result = mock(ResultSet::class.java)
        `when`(result.next()).thenReturn(true)
        `when`(result.getLong(1)).thenReturn(125L)
        `when`(result.wasNull()).thenReturn(false)
        `when`(result.getString(2)).thenReturn("0/20")

        assertEquals(125L, measure(result))
    }

    private fun probeResult(result: ResultSet): DbReaderProbeResult {
        val dataSource = mock(DataSource::class.java)
        val connection = mock(Connection::class.java)
        val statement = mock(PreparedStatement::class.java)
        `when`(dataSource.connection).thenReturn(connection)
        `when`(connection.prepareStatement(anyString())).thenReturn(statement)
        `when`(statement.executeQuery()).thenReturn(result)
        return DbReaderLagProbe().measureResult(dataSource)
    }

    private fun measure(result: ResultSet): Long? {
        val dataSource = mock(DataSource::class.java)
        val connection = mock(Connection::class.java)
        val statement = mock(PreparedStatement::class.java)
        `when`(dataSource.connection).thenReturn(connection)
        `when`(connection.prepareStatement(anyString())).thenReturn(statement)
        `when`(statement.executeQuery()).thenReturn(result)
        return DbReaderLagProbe().measure(dataSource)
    }
}
