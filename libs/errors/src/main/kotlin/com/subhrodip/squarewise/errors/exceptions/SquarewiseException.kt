package com.subhrodip.squarewise.errors.exceptions

import com.subhrodip.squarewise.errors.catalog.ErrorCatalog
import com.subhrodip.squarewise.errors.code.ErrorDefinition
import com.subhrodip.squarewise.errors.diagnostics.ErrorDiagnostics

/**
 * Governed base exception for catalogued Squarewise failures.
 *
 * Public mappers must use [definition] and [diagnostics], never this exception's
 * cause or message. Construction rejects definitions absent from the compiled catalog.
 */
abstract class SquarewiseException protected constructor(
    val definition: ErrorDefinition,
    val diagnostics: ErrorDiagnostics = ErrorDiagnostics.EMPTY,
    cause: Throwable? = null,
    messageOverride: String? = null,
) : RuntimeException(messageOverride ?: definition.errorName, cause) {
    init {
        require(ErrorCatalog.contains(definition)) {
            "SquarewiseException requires a compiled catalog definition"
        }
    }
}
