package com.subhrodip.squarewise.errors.exceptions

import com.subhrodip.squarewise.errors.catalog.PlatformErrors
import com.subhrodip.squarewise.errors.code.ErrorDefinition
import com.subhrodip.squarewise.errors.diagnostics.ErrorDiagnostics
import com.subhrodip.squarewise.errors.diagnostics.MapDiagnostics
import com.subhrodip.squarewise.errors.http.FieldViolation

/** Governed validation failure with bounded field diagnostics. */
open class DomainValidationException(
    val violations: List<FieldViolation>,
    definition: ErrorDefinition = PlatformErrors.REQUEST_VALIDATION_FAILED,
    diagnostics: ErrorDiagnostics = MapDiagnostics.of(
        violations.mapIndexed { index, violation ->
            "violation.$index.field" to violation.field
        }.toMap()
    ),
    cause: Throwable? = null,
) : SquarewiseException(definition, diagnostics, cause)
