package com.subhrodip.squarewise.errors.exceptions

import com.subhrodip.squarewise.errors.catalog.ExpenseErrors
import com.subhrodip.squarewise.errors.diagnostics.ErrorDiagnostics
import com.subhrodip.squarewise.errors.diagnostics.MapDiagnostics
import com.subhrodip.squarewise.errors.diagnostics.ResourceIdentifier

/** Resource lookup failure whose definition supports anti-enumeration mapping. */
class EntityNotFoundException(
    resource: ResourceIdentifier,
    diagnostics: ErrorDiagnostics = MapDiagnostics.of(
        mapOf("resource.type" to resource.value)
    ),
    cause: Throwable? = null,
) : SquarewiseException(ExpenseErrors.GROUP_NOT_FOUND, diagnostics, cause)
