package com.subhrodip.squarewise.bff.graphql

import graphql.execution.AbortExecutionException

/** Typed GraphQL execution signal emitted by depth and complexity guards. */
class GraphQlLimitExceededException(
    val limit: GraphQlLimit,
    message: String,
) : AbortExecutionException(message) {
    /** Carries a non-linguistic marker to the result instrumentation. */
    override fun getExtensions(): Map<String, Any> = mapOf("squarewiseLimit" to limit.name)
}

/** The bounded GraphQL resource policy that rejected the operation. */
enum class GraphQlLimit {
    DEPTH,
    COMPLEXITY,
}
