package com.subhrodip.squarewise.errors.exceptions

import com.subhrodip.squarewise.errors.catalog.PlatformErrors
import com.subhrodip.squarewise.errors.code.ErrorDefinition
import com.subhrodip.squarewise.errors.diagnostics.ErrorDiagnostics

/** Optimistic-concurrency failure represented by the governed conflict definition. */
open class ConcurrencyConflictException(
    definition: ErrorDefinition = PlatformErrors.RESOURCE_CONFLICT,
    diagnostics: ErrorDiagnostics = ErrorDiagnostics.EMPTY,
    cause: Throwable? = null,
) : SquarewiseException(definition, diagnostics, cause)
