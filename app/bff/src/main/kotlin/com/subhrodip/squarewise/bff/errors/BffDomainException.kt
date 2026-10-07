package com.subhrodip.squarewise.bff.errors

import com.subhrodip.squarewise.errors.code.ErrorDefinition
import com.subhrodip.squarewise.errors.diagnostics.ErrorDiagnostics
import com.subhrodip.squarewise.errors.exceptions.SquarewiseException

/** Exception carrying a governed BFF definition to GraphQL error mapping. */
class BffDomainException(
    definition: ErrorDefinition,
    message: String? = null,
    cause: Throwable? = null,
    diagnostics: ErrorDiagnostics = ErrorDiagnostics.EMPTY,
) : SquarewiseException(definition, diagnostics, cause, message)
