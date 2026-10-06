package com.subhrodip.squarewise.errors.exceptions

import com.subhrodip.squarewise.errors.catalog.ExpenseErrors
import com.subhrodip.squarewise.errors.diagnostics.ErrorDiagnostics

/** Optimistic-concurrency failure represented by the governed conflict definition. */
class ConcurrencyConflictException(
    diagnostics: ErrorDiagnostics = ErrorDiagnostics.EMPTY,
    cause: Throwable? = null,
) : SquarewiseException(ExpenseErrors.GROUP_NAME_CONFLICT, diagnostics, cause)
