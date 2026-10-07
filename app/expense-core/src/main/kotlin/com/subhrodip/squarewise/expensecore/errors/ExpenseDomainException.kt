package com.subhrodip.squarewise.expensecore.errors

import com.subhrodip.squarewise.errors.code.ErrorDefinition
import com.subhrodip.squarewise.errors.diagnostics.ErrorDiagnostics
import com.subhrodip.squarewise.errors.exceptions.SquarewiseException

/**
 * Catalog-governed failure raised by Expense Core.
 *
 * @param definition immutable Expense Core catalog definition.
 * @param message internal compatibility context retained for legacy callers only.
 * @param cause persistence or validation cause retained for diagnostics.
 * @param diagnostics bounded identifiers for logs and traces.
 */
class ExpenseDomainException(
    definition: ErrorDefinition,
    message: String? = null,
    cause: Throwable? = null,
    diagnostics: ErrorDiagnostics = ErrorDiagnostics.EMPTY,
) : SquarewiseException(definition, diagnostics, cause, message)
