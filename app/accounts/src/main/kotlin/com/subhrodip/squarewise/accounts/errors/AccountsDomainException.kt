package com.subhrodip.squarewise.accounts.errors

import com.subhrodip.squarewise.errors.code.ErrorDefinition
import com.subhrodip.squarewise.errors.diagnostics.ErrorDiagnostics
import com.subhrodip.squarewise.errors.exceptions.SquarewiseException

/**
 * Catalog-governed failure raised by the Accounts bounded context.
 *
 * @param definition immutable Accounts catalog definition exposed to boundary mappers.
 * @param diagnostics bounded identifiers attached for logs and traces only.
 * @param cause internal cause retained for diagnostics and never used as response text.
 */
class AccountsDomainException(
    definition: ErrorDefinition,
    diagnostics: ErrorDiagnostics = ErrorDiagnostics.EMPTY,
    cause: Throwable? = null,
) : SquarewiseException(definition, diagnostics, cause)
