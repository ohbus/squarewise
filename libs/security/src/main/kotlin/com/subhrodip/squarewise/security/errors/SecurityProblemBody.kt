package com.subhrodip.squarewise.security.errors

import com.subhrodip.squarewise.errors.code.ErrorDefinition

/** Renders a bounded security Problem Details JSON object from static catalog values. */
internal object SecurityProblemBody {
    fun render(definition: ErrorDefinition, requestId: String): String =
        """{"type":"https://squarewise.example/problems/${definition.errorName.lowercase()}","title":"${definition.title}","status":${definition.httpStatus ?: 500},"detail":"${definition.safeDetail}","instance":"/errors/${definition.errorName.lowercase()}","code":"${definition.legacyCode ?: definition.errorName}","numericCode":"${definition.numericCode.value}","errorName":"${definition.errorName}","requestId":"$requestId","source":"security"}"""
}
