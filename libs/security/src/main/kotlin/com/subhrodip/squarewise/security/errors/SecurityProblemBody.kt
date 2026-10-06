package com.subhrodip.squarewise.security.errors

import com.subhrodip.squarewise.errors.code.ErrorDefinition

/** Renders a bounded security Problem Details JSON object from static catalog values. */
internal object SecurityProblemBody {
    fun render(definition: ErrorDefinition, requestId: String): String =
        """{"type":"https://squarewise.example/problems/${definition.errorName.lowercase()}","title":"${definition.title}","status":${definition.httpStatus ?: 500},"detail":"${definition.safeDetail}","instance":"/errors/${definition.errorName.lowercase()}","code":"${legacyCode(definition.legacyCode)}","numericCode":"${definition.numericCode.value}","errorName":"${definition.errorName}","requestId":"$requestId","source":"security"}"""

    private fun legacyCode(legacyCode: String?): String = LEGACY_CODES[legacyCode] ?: "INTERNAL_ERROR"

    private val LEGACY_CODES: Map<String, String> = mapOf(
        "ERR-03" to "UNAUTHENTICATED",
        "ERR-04" to "FORBIDDEN",
        "ERR-05" to "NOT_FOUND",
    )
}
