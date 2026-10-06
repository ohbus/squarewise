package com.subhrodip.squarewise.errors.exceptions

import com.subhrodip.squarewise.errors.catalog.ErrorCatalog
import com.subhrodip.squarewise.errors.code.ErrorDefinition
import com.subhrodip.squarewise.errors.diagnostics.ErrorDiagnostics
import com.subhrodip.squarewise.errors.domain.ApplicationException
import com.subhrodip.squarewise.errors.domain.ErrorCode

/**
 * Governed base exception for catalogued Squarewise failures.
 *
 * Public mappers must use [definition] and [diagnostics], never this exception's
 * cause or message. Construction rejects definitions absent from the compiled catalog.
 */
abstract class SquarewiseException protected constructor(
    val definition: ErrorDefinition,
    val diagnostics: ErrorDiagnostics = ErrorDiagnostics.EMPTY,
    cause: Throwable? = null,
    messageOverride: String? = null,
) : ApplicationException(legacyCode(definition), messageOverride ?: definition.errorName, cause) {
    init {
        require(ErrorCatalog.all.any { it === definition }) {
            "SquarewiseException requires a compiled catalog definition"
        }
    }

    private companion object {
        fun legacyCode(definition: ErrorDefinition): ErrorCode = when (definition.legacyCode) {
            "ERR-02" -> ErrorCode.ERR_02
            "ERR-03" -> ErrorCode.ERR_03
            "ERR-04" -> ErrorCode.ERR_04
            "ERR-05" -> ErrorCode.ERR_05
            "ERR-06" -> ErrorCode.ERR_06
            "ERR-11" -> ErrorCode.ERR_11
            else -> ErrorCode.ERR_01
        }
    }
}
