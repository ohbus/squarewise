package com.subhrodip.squarewise.errors.web

import com.subhrodip.squarewise.errors.code.ErrorDefinition
import java.net.URI

/** Builds safe RFC 9457 bodies from compiled error definitions. */
class ProblemDetailsFactory(private val source: String) {
    /**
     * Create a public problem body using only catalog-controlled text.
     *
     * @param definition the immutable public error identity
     * @param requestId the bounded request correlation identifier
     * @param violations validated field-level diagnostics, if any
     * @return a problem body whose detail never depends on an exception message
     */
    fun create(
        definition: ErrorDefinition,
        requestId: String,
        violations: List<ViolationDto> = emptyList(),
    ): ProblemDetailsDto = ProblemDetailsDto(
        type = URI("https://squarewise.example/problems/${definition.errorName.lowercase()}"),
        title = definition.title,
        status = definition.httpStatus ?: 500,
        detail = definition.safeDetail,
        instance = "/errors/${definition.errorName.lowercase()}",
        code = definition.category.name,
        requestId = requestId,
        source = source,
        numericCode = definition.numericCode.value,
        errorName = definition.errorName,
        messageKey = definition.messageKey,
        violations = violations,
    )
}
