package com.subhrodip.squarewise.errors.exceptions

import com.subhrodip.squarewise.errors.code.ErrorDefinition
import com.subhrodip.squarewise.errors.diagnostics.ErrorDiagnostics

/**
 * Governed business rule violation representing domain invariant rejections (HTTP 422).
 */
open class BusinessRuleViolationException(
    definition: ErrorDefinition,
    diagnostics: ErrorDiagnostics = ErrorDiagnostics.EMPTY,
    cause: Throwable? = null,
) : SquarewiseException(definition, diagnostics, cause)
