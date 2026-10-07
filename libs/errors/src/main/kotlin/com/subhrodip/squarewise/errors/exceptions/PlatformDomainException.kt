package com.subhrodip.squarewise.errors.exceptions

import com.subhrodip.squarewise.errors.code.ErrorDefinition
import com.subhrodip.squarewise.errors.diagnostics.ErrorDiagnostics

/** Carries a governed Platform failure across shared-library boundaries. */
class PlatformDomainException(
    definition: ErrorDefinition,
    message: String? = null,
    cause: Throwable? = null,
    diagnostics: ErrorDiagnostics = ErrorDiagnostics.EMPTY,
) : SquarewiseException(definition, diagnostics, cause, message)
