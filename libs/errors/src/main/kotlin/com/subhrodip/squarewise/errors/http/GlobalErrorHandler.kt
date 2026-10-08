@file:Suppress("CanConvertToMultiDollarString")

package com.subhrodip.squarewise.errors.http

import com.subhrodip.squarewise.errors.catalog.PlatformErrors
import com.subhrodip.squarewise.errors.code.CategoryCode
import com.subhrodip.squarewise.errors.code.ErrorDefinition
import com.subhrodip.squarewise.errors.exceptions.DomainValidationException
import com.subhrodip.squarewise.errors.exceptions.SquarewiseException
import com.subhrodip.squarewise.errors.request.RequestIdContext
import com.subhrodip.squarewise.errors.web.ProblemDetailsDto
import com.subhrodip.squarewise.errors.web.ViolationDto
import java.net.URI
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.dao.OptimisticLockingFailureException
import org.springframework.http.HttpStatus
import org.springframework.http.HttpStatusCode
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.ServletRequestBindingException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.HttpMediaTypeNotAcceptableException
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException

/**
 * Global REST controller advice translating domain and framework exceptions into RFC 7807
 * problem details with structured, multi-level diagnostic logging.
 *
 * Invariants:
 * - Unhandled server exceptions (500) are logged at ERROR with full stack trace and correlation ID.
 * - Client errors (400 validation failures, malformed payloads, 404s, 409 conflicts) are logged at WARN.
 * - Sensitive values (passwords, tokens, raw amounts) are never echoed into logs.
 */
@RestControllerAdvice
class GlobalErrorHandler(
    @Value("\${spring.application.name:unknown}") private val serviceName: String = "unknown"
) {

    /**
     * Returns a bodyless 406 when the client requests a representation that the endpoint cannot
     * produce. A body is intentionally omitted because serializing an RFC 7807 response would
     * repeat the same content-negotiation failure.
     *
     * @return HTTP 406 with no negotiated response body
     */
    @ExceptionHandler(HttpMediaTypeNotAcceptableException::class)
    fun notAcceptable(): ResponseEntity<Void> =
        ResponseEntity.status(HttpStatus.NOT_ACCEPTABLE).build()

    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun validation(error: MethodArgumentNotValidException): ResponseEntity<ProblemDetailsDto> {
        val violations = error.bindingResult.fieldErrors.map {
            FieldViolation(
                field = it.field,
                message = it.defaultMessage ?: "invalid value",
                messageKey = it.code?.let { code -> "validation.${code.lowercase()}" },
                rejectedValue = sanitizeRejectedValue(it.field, it.rejectedValue)
            )
        }
        val summary = violations.joinToString("; ") { "${it.field}: ${it.message}" }
        log.warn("Request validation failed [requestId={}]: {}", RequestIdContext.get(), summary)
        return problem(
            PlatformErrors.REQUEST_VALIDATION_FAILED,
            HttpStatus.UNPROCESSABLE_CONTENT,
            "Validation failed",
            "One or more request parameters failed validation.",
            violations
        )
    }

    @ExceptionHandler(HttpMessageNotReadableException::class)
    fun messageNotReadable(error: HttpMessageNotReadableException): ResponseEntity<ProblemDetailsDto> {
        val rootCauseMessage = error.rootCause?.message
        val exceptionMessage = error.message
        val detail = when {
            rootCauseMessage?.isNotBlank() == true -> rootCauseMessage
            exceptionMessage?.isNotBlank() == true -> exceptionMessage
            else -> "Malformed request payload"
        }
        log.warn("Malformed HTTP request payload [requestId={}]: {}", RequestIdContext.get(), detail)
        return problem(
            PlatformErrors.REQUEST_BODY_MALFORMED,
            HttpStatus.BAD_REQUEST,
            "Malformed request payload",
            detail
        )
    }

    @ExceptionHandler(ServletRequestBindingException::class)
    fun requestBinding(error: ServletRequestBindingException): ResponseEntity<ProblemDetailsDto> {
        val detail = error.message?.takeIf { it.isNotBlank() } ?: "Invalid request binding"
        log.warn("Missing or invalid request parameter or header [requestId={}]: {}", RequestIdContext.get(), detail)
        return problem(
            PlatformErrors.REQUEST_VALUE_INVALID,
            HttpStatus.UNPROCESSABLE_CONTENT,
            "Missing or invalid request parameter or header",
            detail
        )
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException::class)
    fun argumentTypeMismatch(error: MethodArgumentTypeMismatchException): ResponseEntity<ProblemDetailsDto> {
        log.warn(
            "Type mismatch for parameter '{}' [requestId={}]: expected type '{}'",
            error.name,
            RequestIdContext.get(),
            error.requiredType?.simpleName
        )
        return problem(
            PlatformErrors.REQUEST_VALUE_INVALID,
            HttpStatus.UNPROCESSABLE_CONTENT,
            "Type mismatch for parameter ${error.name}",
            error.message
        )
    }

    @ExceptionHandler(OptimisticLockingFailureException::class)
    fun optimisticLock(error: OptimisticLockingFailureException): ResponseEntity<ProblemDetailsDto> {
        log.warn(
            "Optimistic locking conflict detected [requestId={}]: {}",
            RequestIdContext.get(),
            error.message
        )
        return problem(
            PlatformErrors.RESOURCE_CONFLICT,
            HttpStatus.CONFLICT,
            "Conflict",
            error.message ?: "Resource was updated by another transaction"
        )
    }


    @ExceptionHandler(IllegalArgumentException::class)
    fun illegalArgument(error: IllegalArgumentException): ResponseEntity<ProblemDetailsDto> {
        val detail = error.message ?: "Invalid request"
        log.warn("Invalid request argument [requestId={}]: {}", RequestIdContext.get(), detail)
        return problem(
            PlatformErrors.REQUEST_VALUE_INVALID,
            HttpStatus.UNPROCESSABLE_CONTENT,
            "Request validation failed",
            detail
        )
    }



    /** Map catalog-governed failures while preserving the established response shape and enriching diagnostics. */
    @ExceptionHandler(SquarewiseException::class)
    fun governedException(ex: SquarewiseException): ResponseEntity<ProblemDetailsDto> {
        val definition = ex.definition
        val status = HttpStatus.resolve(definition.httpStatus ?: 500) ?: HttpStatus.INTERNAL_SERVER_ERROR
        val violations = if (ex is DomainValidationException) {
            ex.violations
        } else {
            emptyList()
        }
        val detail = if (ex.message != null && ex.message != definition.errorName && !ex.message!!.startsWith(ex.javaClass.name)) {
            ex.message!!
        } else {
            definition.safeDetail
        }
        return problem(
            definition,
            status,
            title = definition.title,
            detail = detail,
            violations = violations,
            retryAfterSeconds = if (definition.category == CategoryCode.RATE_LIMIT_EXCEEDED) RATE_LIMIT_RETRY_AFTER_SECONDS else null,
        )
    }

    @ExceptionHandler(Exception::class)
    fun unexpected(error: Exception): ResponseEntity<ProblemDetailsDto> {
        log.error("Unhandled unexpected exception [requestId={}]: {}", RequestIdContext.get(), error.message, error)
        return problem(
            PlatformErrors.UNEXPECTED_INTERNAL_ERROR,
            HttpStatus.INTERNAL_SERVER_ERROR
        )
    }

    private fun problem(
        definition: ErrorDefinition,
        status: HttpStatusCode,
        title: String = "Internal server error",
        detail: String = "An unexpected error occurred",
        violations: List<FieldViolation> = emptyList(),
        retryAfterSeconds: Long? = null
    ): ResponseEntity<ProblemDetailsDto> =
        ResponseEntity.status(status)
            .headers(HttpHeaders().apply {
                retryAfterSeconds?.let { set(HttpHeaders.RETRY_AFTER, it.toString()) }
            })
            .contentType(MediaType.APPLICATION_PROBLEM_JSON)
            .body(ProblemDetailsDto(
                type = URI("https://squarewise.example/problems/${definition.errorName.lowercase()}"),
                title = title,
                status = status.value(),
                detail = detail,
                instance = "/errors/${definition.errorName.lowercase()}".take(256),
                code = definition.category.name,
                requestId = RequestIdContext.get(),
                source = serviceName,
                numericCode = definition.numericCode.value,
                errorName = definition.errorName,
                messageKey = definition.messageKey,
                violations = violations.map { ViolationDto(it.field, it.message, it.messageKey, it.rejectedValue) },
            ))

    private fun sanitizeRejectedValue(field: String, value: Any?): Any? {
        if (value == null) return null
        val lowerField = field.lowercase()
        if (lowerField.contains("password") || lowerField.contains("token") || lowerField.contains("secret") || lowerField.contains("key")) {
            return "[REDACTED]"
        }
        return value.toString().take(256)
    }

    companion object {
        private const val RATE_LIMIT_RETRY_AFTER_SECONDS = 60L
        private val log = LoggerFactory.getLogger(GlobalErrorHandler::class.java)
    }
}
