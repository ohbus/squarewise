package com.subhrodip.squarewise.db.config

import com.subhrodip.squarewise.db.routing.DbContextHolder
import com.subhrodip.squarewise.db.routing.DbExecutionContext
import com.subhrodip.squarewise.db.routing.DbOperationKind
import com.subhrodip.squarewise.observability.db.DbTelemetry
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import kotlin.test.Test
import kotlin.test.assertEquals

/** Verifies SQL inspection counts one generated statement with the active operation label. */
class DbStatementInspectorTest {
    @Test
    fun `records generated SQL without changing it`() {
        val telemetry = DbTelemetry(SimpleMeterRegistry())
        val inspector = DbStatementInspector(telemetry)

        val sql = DbContextHolder.withContext(DbExecutionContext("groups.list", DbOperationKind.QUERY)) {
            inspector.inspect("select 1")
        }

        assertEquals("select 1", sql)
        assertEquals(1, telemetry.snapshot().jdbcStatements)
    }
}
