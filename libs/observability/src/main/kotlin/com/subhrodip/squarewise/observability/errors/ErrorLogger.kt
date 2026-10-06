package com.subhrodip.squarewise.observability.errors

import com.subhrodip.squarewise.errors.code.ErrorDefinition
import com.subhrodip.squarewise.errors.code.ErrorSeverity
import org.slf4j.Logger
import org.slf4j.LoggerFactory

/** Logs governed errors once with severity-appropriate stack disclosure. */
class ErrorLogger(
    private val logger: Logger = LoggerFactory.getLogger(ErrorLogger::class.java),
) {
    /** Log expected client failures without a stack and internal failures with cause context. */
    fun logError(exception: Throwable, definition: ErrorDefinition, requestId: String) {
        when (definition.severity) {
            ErrorSeverity.INFO -> logger.info("Client error [{}] {}: {}", definition.numericCode.value, definition.errorName, definition.safeDetail)
            ErrorSeverity.WARN -> logger.warn("Expected domain warning [{}] {}: {}", definition.numericCode.value, definition.errorName, definition.safeDetail)
            ErrorSeverity.ERROR, ErrorSeverity.CRITICAL -> logger.error(
                "Internal failure [{}] {} (requestId={})",
                definition.numericCode.value,
                definition.errorName,
                requestId,
                exception,
            )
        }
    }
}
