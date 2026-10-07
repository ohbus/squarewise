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
        put("code", legacyCode(definition.legacyCode))
        put("numericCode", definition.numericCode.value)
        put("errorName", definition.errorName)
        put("requestId", requestId)
        put("source", "squarewise-bff")
        put("timestamp", Instant.now().toString())
    }

    private fun legacyCode(legacyCode: String?): String = when (legacyCode) {
        "ERR-02" -> "VALIDATION_FAILED"
        "ERR-03" -> "UNAUTHENTICATED"
        "ERR-04" -> "FORBIDDEN"
        "ERR-05" -> "NOT_FOUND"
        "ERR-06", "ERR-09" -> "CONFLICT"
        "ERR-11" -> "RATE_LIMITED"
        else -> "INTERNAL_ERROR"
    }
}
