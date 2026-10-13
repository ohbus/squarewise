package com.subhrodip.squarewise.db.routing

import java.lang.annotation.Inherited

/**
 * Declaratively marks a repository, service, or controller method/class to explicitly
 * route queries and commands to the primary database writer instance.
 *
 * Provides a lightweight bootstrap annotation for declaring primary writer execution.
 */
@Target(AnnotationTarget.FUNCTION, AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
@MustBeDocumented
@Inherited
annotation class DbWriter(
    /**
     * Stable lowercase dot-delimited operation identity for telemetry and auditing.
     * If blank, defaults to inferred operation name from class and method.
     */
    val operation: String = ""
)
