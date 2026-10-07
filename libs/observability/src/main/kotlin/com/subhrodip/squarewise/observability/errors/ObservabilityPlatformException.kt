package com.subhrodip.squarewise.observability.errors

import com.subhrodip.squarewise.errors.code.ErrorDefinition
import com.subhrodip.squarewise.errors.diagnostics.ErrorDiagnostics
import com.subhrodip.squarewise.errors.exceptions.SquarewiseException

/** Carries a governed Platform error from observability adapters. */
class ObservabilityPlatformException(
    definition: ErrorDefinition,
    message: String? = null,
    cause: Throwable? = null,
    diagnostics: ErrorDiagnostics = ErrorDiagnostics.EMPTY,
) : SquarewiseException(definition, diagnostics, cause, message)
