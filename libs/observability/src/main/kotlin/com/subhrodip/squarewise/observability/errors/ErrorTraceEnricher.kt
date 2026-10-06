package com.subhrodip.squarewise.observability.errors

import com.subhrodip.squarewise.errors.code.ErrorCategory
import com.subhrodip.squarewise.errors.code.ErrorDefinition
import com.subhrodip.squarewise.errors.observability.ErrorTraceSpan

/** Adds bounded catalog identity attributes to an active tracing span. */
object ErrorTraceEnricher {
    /** Enrich [span] and mark it failed only for server-side or unexpected errors. */
    fun enrich(span: ErrorTraceSpan, definition: ErrorDefinition, unexpected: Boolean = false) {
        val category = ErrorCategory.entries.single { it.digit == definition.numericCode.categoryDigit }
        span.setAttribute("error.code", definition.numericCode.value)
        span.setAttribute("error.name", definition.errorName)
        span.setAttribute("error.type", category.name)
        if (unexpected || (definition.httpStatus ?: 500) >= 500) span.markError()
    }
}
