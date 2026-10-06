package com.subhrodip.squarewise.db.config

import com.subhrodip.squarewise.db.routing.DbContextHolder
import com.subhrodip.squarewise.observability.db.DbTelemetry
import org.hibernate.resource.jdbc.spi.StatementInspector

/** Counts Hibernate SQL statements using the current bounded database operation label. */
class DbStatementInspector(
    private val telemetry: DbTelemetry
) : StatementInspector {
    /** Records one generated SQL statement and returns it unchanged for Hibernate execution. */
    override fun inspect(sql: String): String {
        telemetry.jdbcStatement(DbContextHolder.current().operationName)
        return sql
    }
}
