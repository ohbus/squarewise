package com.subhrodip.squarewise.errors.graphql

import com.subhrodip.squarewise.errors.code.ErrorDefinition
import com.subhrodip.squarewise.errors.web.ProblemDetailsDto
import java.time.Instant

/** Builds additive GraphQL extensions without mutating upstream error identity. */
object GraphQLExtensionsFormatter {
    /** Preserve every available upstream Problem Details identity field. */
    fun fromProblem(problem: ProblemDetailsDto): Map<String, Any> = buildMap {
        put("code", problem.code)
        put("numericCode", problem.numericCode)
        put("errorName", problem.errorName)
        put("requestId", problem.requestId)
        put("source", problem.source)
        put("timestamp", problem.timestamp.toString())
    }

    /** Format a local catalog definition with the supplied request context. */
    fun fromDefinition(definition: ErrorDefinition, requestId: String): Map<String, Any> = buildMap {
        put("code", definition.category.name)
        put("numericCode", definition.numericCode.value)
        put("errorName", definition.errorName)
        put("messageKey", definition.messageKey)
        put("requestId", requestId)
        put("source", "squarewise-bff")
        put("timestamp", Instant.now().toString())
    }

}
