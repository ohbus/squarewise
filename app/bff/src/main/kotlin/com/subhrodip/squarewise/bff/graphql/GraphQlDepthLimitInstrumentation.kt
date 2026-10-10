package com.subhrodip.squarewise.bff.graphql

import graphql.analysis.MaxQueryDepthInstrumentation
import graphql.execution.AbortExecutionException

/** Adds a typed marker to GraphQL Java depth-limit aborts. */
class GraphQlDepthLimitInstrumentation(maxDepth: Int) : MaxQueryDepthInstrumentation(maxDepth) {
    override fun mkAbortException(depth: Int, maxDepth: Int): AbortExecutionException =
        GraphQlLimitExceededException(GraphQlLimit.DEPTH, "GraphQL query depth exceeded")
}
