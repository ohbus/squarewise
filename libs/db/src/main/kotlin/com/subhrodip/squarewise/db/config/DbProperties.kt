package com.subhrodip.squarewise.db.config

import org.springframework.boot.context.properties.ConfigurationProperties

/** Configuration for the CQRS writer and named read pools. */
@ConfigurationProperties("squarewise.db")
data class DbProperties(
    /** Enables the shared routed datasource instead of framework defaults. */
    var enabled: Boolean = false,
    /** Authoritative writer JDBC settings. */
    var writer: PoolProperties = PoolProperties(),
    /** Named writer pool settings for multi-writer instances or shards. */
    var writers: Map<String, PoolProperties> = emptyMap(),
    /** Named read-only pool settings. */
    var readers: Map<String, PoolProperties> = emptyMap(),
    /** Explicit local-only mode that aliases configured reader names to the writer pool. */
    var readerIsWriterDiagnostic: Boolean = false,
    /** Maximum tolerated asynchronous replay lag before a reader is marked lagging. */
    var readerLagBudgetMs: Long = 5_000,
    /** Bounded interval between replay-lag probes. */
    var healthProbeIntervalMs: Long = 2_000
)
