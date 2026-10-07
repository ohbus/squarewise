package com.subhrodip.squarewise.errors.exceptions

import com.subhrodip.squarewise.errors.catalog.AccountsErrors
import com.subhrodip.squarewise.errors.diagnostics.ErrorDiagnostics
import com.subhrodip.squarewise.errors.diagnostics.MapDiagnostics
import com.subhrodip.squarewise.errors.http.FieldViolation

/** Governed validation failure with bounded field diagnostics. */
class DomainValidationException(
    violations: List<FieldViolation>,
    diagnostics: ErrorDiagnostics = MapDiagnostics.of(
        violations.mapIndexed { index, violation ->
            "violation.$index.field" to violation.field
        }.toMap()
    ),
    cause: Throwable? = null,
) : SquarewiseException(AccountsErrors.PROFILE_REQUEST_INVALID, diagnostics, cause)
