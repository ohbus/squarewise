package com.subhrodip.squarewise.db.routing

import java.lang.annotation.Inherited

/**
 * Declaratively marks a repository, service, or controller method/class to route queries
 * to read replicas, optionally targeting a specific reader instance (e.g. "search", "reporting").
 *
 * Provides a lightweight bootstrap annotation for declaring read replica query routing.
 */
@Target(AnnotationTarget.FUNCTION, AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
@MustBeDocumented
@Inherited
annotation class DbReader(
    /**
     * Stable lowercase dot-delimited operation identity for telemetry and auditing.
     * If blank, defaults to inferred operation name from class and method.
     */
    val operation: String = "",

    /**
     * Optional explicit reader instance name to route queries to (e.g. "search", "reporting").
     * When blank, routes to any available healthy reader replica via round-robin.
     */
    val target: String = "",

    /**
     * Required consistency guarantee. Defaults to EVENTUAL for read replica queries.
     */
    val consistency: ReadConsistency = ReadConsistency.EVENTUAL
)
