package com.subhrodip.squarewise.errors.web

import com.subhrodip.squarewise.errors.catalog.PlatformErrors
import com.subhrodip.squarewise.errors.code.ErrorDefinition
import com.subhrodip.squarewise.errors.exceptions.DomainValidationException
import com.subhrodip.squarewise.errors.exceptions.SquarewiseException
import com.subhrodip.squarewise.errors.request.RequestIdContext
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.bind.ServletRequestBindingException
import org.springframework.web.HttpRequestMethodNotSupportedException
import org.springframework.web.HttpMediaTypeNotAcceptableException
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException
import org.springframework.dao.OptimisticLockingFailureException

/**
 * Explicit servlet exception boundary that never copies throwable text to a response.
 *
 * Framework exceptions map to static platform definitions; governed exceptions map
 * directly from their compiled catalog definition.
 */
@RestControllerAdvice
class GlobalErrorAdvice(
    @Value("\${spring.application.name:unknown}") private val serviceName: String = "unknown",
) {
    private val problemFactory = ProblemDetailsFactory(serviceName)
    /** Map a governed domain failure with additive identity fields and rich field violations if present. */
    @ExceptionHandler(SquarewiseException::class)
    fun governed(exception: SquarewiseException): ResponseEntity<ProblemDetailsDto> {
        val violations = if (exception is DomainValidationException) {
            exception.violations.map {
                ViolationDto(
                    field = it.field.take(MAX_FIELD_LENGTH),
                    message = it.message.take(MAX_FIELD_LENGTH),
                    messageKey = it.messageKey,
                    rejectedValue = sanitizeRejectedValue(it.field, it.rejectedValue),
                )
            }
        } else {
            emptyList()
        }
        return response(exception.definition, violations = violations)
    }

    /** Map bean-validation failures with rich field diagnostics and i18n keys. */
    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun validation(exception: MethodArgumentNotValidException): ResponseEntity<ProblemDetailsDto> {
        val violations = exception.bindingResult.fieldErrors.take(MAX_VIOLATIONS).map {
            ViolationDto(
                field = it.field.take(MAX_FIELD_LENGTH),
                message = it.defaultMessage?.take(MAX_FIELD_LENGTH) ?: "The value is invalid",
                messageKey = it.code?.let { code -> "validation.${code.lowercase()}" },
                rejectedValue = sanitizeRejectedValue(it.field, it.rejectedValue),
            )
        }
        return response(PlatformErrors.REQUEST_VALIDATION_FAILED, violations = violations)
    }

    /** Map malformed JSON to static safe detail text. */
    @ExceptionHandler(HttpMessageNotReadableException::class)
    fun malformed(@Suppress("UNUSED_PARAMETER") exception: HttpMessageNotReadableException? = null): ResponseEntity<ProblemDetailsDto> =
        response(PlatformErrors.REQUEST_BODY_MALFORMED)

    /** Map missing or malformed request bindings without copying framework text. */
    @ExceptionHandler(ServletRequestBindingException::class)
    fun requestBinding(@Suppress("UNUSED_PARAMETER") exception: ServletRequestBindingException): ResponseEntity<ProblemDetailsDto> =
        response(PlatformErrors.REQUEST_VALUE_INVALID)

    /** Map path and query conversion failures to one stable validation identity. */
    @ExceptionHandler(MethodArgumentTypeMismatchException::class)
    fun argumentTypeMismatch(@Suppress("UNUSED_PARAMETER") exception: MethodArgumentTypeMismatchException): ResponseEntity<ProblemDetailsDto> =
        response(PlatformErrors.REQUEST_VALUE_INVALID)

    /** Map optimistic locking failures to a static conflict response. */
    @ExceptionHandler(OptimisticLockingFailureException::class)
    fun optimisticLock(@Suppress("UNUSED_PARAMETER") exception: OptimisticLockingFailureException): ResponseEntity<ProblemDetailsDto> =
        response(PlatformErrors.RESOURCE_CONFLICT)

    /** Map direct input exceptions that escaped a lower application boundary. */
    @ExceptionHandler(IllegalArgumentException::class)
    fun illegalArgument(@Suppress("UNUSED_PARAMETER") exception: IllegalArgumentException): ResponseEntity<ProblemDetailsDto> =
        response(PlatformErrors.REQUEST_VALUE_INVALID)

    /** Map unsupported response negotiation to the catalog identity. */
    @ExceptionHandler(HttpMediaTypeNotAcceptableException::class)
    fun notAcceptable(): ResponseEntity<ProblemDetailsDto> =
        response(PlatformErrors.REPRESENTATION_NOT_ACCEPTABLE)

    /** Preserve the Allow header while returning a governed method-not-allowed problem. */
    @ExceptionHandler(HttpRequestMethodNotSupportedException::class)
    fun methodNotAllowed(exception: HttpRequestMethodNotSupportedException): ResponseEntity<ProblemDetailsDto> =
        response(PlatformErrors.METHOD_NOT_ALLOWED, headers = HttpHeaders().apply {
            set(HttpHeaders.ALLOW, exception.supportedHttpMethods?.joinToString(", ") ?: "")
        })

    /** Fail closed for non-fatal unexpected exceptions and log the root cause once. */
    @ExceptionHandler(Exception::class)
    fun unexpected(exception: Exception): ResponseEntity<ProblemDetailsDto> {
        log.error("Unhandled exception at servlet boundary [requestId={}]", RequestIdContext.get(), exception)
        return response(PlatformErrors.UNEXPECTED_INTERNAL_ERROR)
    }

    private fun response(
        definition: ErrorDefinition,
        violations: List<ViolationDto> = emptyList(),
        headers: HttpHeaders = HttpHeaders(),
    ): ResponseEntity<ProblemDetailsDto> {
        val status = definition.httpStatus ?: HttpStatus.INTERNAL_SERVER_ERROR.value()
        val body = problemFactory.create(definition, RequestIdContext.get(), violations)
        val responseHeaders = HttpHeaders(headers)
        responseHeaders.contentType = MediaType.APPLICATION_PROBLEM_JSON
        if (status == HttpStatus.TOO_MANY_REQUESTS.value()) {
            responseHeaders.set(HttpHeaders.RETRY_AFTER, "60")
        }
        return ResponseEntity.status(status).headers(responseHeaders).body(body)
    }

    /** Compatibility name retained for standalone MVC tests during the handler migration. */
    fun governedException(exception: SquarewiseException): ResponseEntity<ProblemDetailsDto> = governed(exception)

    /** Compatibility name retained for standalone MVC tests during the handler migration. */
    fun messageNotReadable(exception: HttpMessageNotReadableException): ResponseEntity<ProblemDetailsDto> = malformed(exception)

    private fun sanitizeRejectedValue(field: String, value: Any?): Any? {
        if (value == null) return null
        val lowerField = field.lowercase()
        if (lowerField.contains("password") || lowerField.contains("token") || lowerField.contains("secret") || lowerField.contains("key")) {
            return "[REDACTED]"
        }
        return value.toString().take(MAX_FIELD_LENGTH)
    }

    private companion object {
        const val MAX_VIOLATIONS: Int = 50
        const val MAX_FIELD_LENGTH: Int = 256
        private val log = LoggerFactory.getLogger(GlobalErrorAdvice::class.java)
    }
}
