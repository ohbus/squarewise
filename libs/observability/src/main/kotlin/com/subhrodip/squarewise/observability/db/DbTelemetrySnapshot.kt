package com.subhrodip.squarewise.observability.db

/** In-process counters retained even when no metrics registry is configured. */
data class DbTelemetrySnapshot(
    val failures: Long,
    val acquisitions: Long = 0,
    val acquisitionTotalMs: Long = 0,
    val lockWaits: Long = 0,
    val deadlocks: Long = 0,
    val queries: Long = 0,
    val queryTotalMs: Long = 0,
    val slowQueries: Long = 0,
    val jdbcStatements: Long = 0
)
