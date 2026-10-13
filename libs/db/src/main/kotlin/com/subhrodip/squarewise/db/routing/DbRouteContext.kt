package com.subhrodip.squarewise.db.routing

import java.lang.annotation.Inherited

/**
 * Declaratively configures database connection routing for a service or controller method.
 *
 * Directs database operations to read replicas or the writer instance, with optional
 * target reader instance specification, consistency level, and telemetry operation naming.
 */
@Target(AnnotationTarget.FUNCTION, AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
@MustBeDocumented
@Inherited
annotation class DbRouteContext(
    /**
     * Stable lowercase dot-delimited operation identity for telemetry and auditing.
     * If blank, defaults to inferred operation name from class and method.
     */
    val operation: String = "",

    /**
     * Whether this operation is approved for replica execution.
     */
    val readerEligible: Boolean = true,

    /**
     * Required consistency guarantee. Defaults to EVENTUAL for read replica queries.
     */
    val consistency: ReadConsistency = ReadConsistency.EVENTUAL,

    /**
     * Operation safety classification. Defaults to QUERY.
     */
    val kind: DbOperationKind = DbOperationKind.QUERY,

    /**
     * Optional explicit reader instance name to route queries to (e.g. "search", "reporting").
     * When blank, routes to any available healthy reader replica.
     */
    val targetReader: String = ""
)
