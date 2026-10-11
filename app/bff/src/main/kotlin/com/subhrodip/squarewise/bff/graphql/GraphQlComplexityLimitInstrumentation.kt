package com.subhrodip.squarewise.bff.graphql

import graphql.analysis.MaxQueryComplexityInstrumentation
import graphql.execution.AbortExecutionException

/** Adds a typed marker to GraphQL Java complexity-limit aborts. */
class GraphQlComplexityLimitInstrumentation(maxComplexity: Int) : MaxQueryComplexityInstrumentation(maxComplexity) {
    override fun mkAbortException(totalComplexity: Int, maxComplexity: Int): AbortExecutionException =
        GraphQlLimitExceededException(GraphQlLimit.COMPLEXITY, "GraphQL query complexity exceeded")
}
