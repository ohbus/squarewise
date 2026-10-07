@file:Suppress("CanConvertToMultiDollarString")

package com.subhrodip.squarewise.errors.http

import com.subhrodip.squarewise.errors.domain.ApplicationException
import com.subhrodip.squarewise.errors.domain.ErrorCode
import com.subhrodip.squarewise.errors.catalog.PlatformErrors
import com.subhrodip.squarewise.errors.code.ErrorDefinition
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
            FieldViolation(it.field, it.defaultMessage ?: "invalid value")
        }
        val summary = violations.joinToString("; ") { "${it.field}: ${it.message}" }
        log.warn("Request validation failed [requestId={}]: {}", RequestIdContext.get(), summary)
        return problem(
            ErrorCode.ERR_02,
            HttpStatus.BAD_REQUEST,
            "Request validation failed",
            "One or more fields are invalid",
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
            ErrorCode.ERR_02,
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
            ErrorCode.ERR_02,
            HttpStatus.BAD_REQUEST,
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
            ErrorCode.ERR_02,
            HttpStatus.BAD_REQUEST,
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
            ErrorCode.ERR_06,
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
            ErrorCode.ERR_02,
            HttpStatus.BAD_REQUEST,
            "Request validation failed",
            detail
        )
    }



    @ExceptionHandler(ApplicationException::class)
    fun applicationException(ex: ApplicationException): ResponseEntity<ProblemDetailsDto> {
        val status = HttpStatus.resolve(ex.errorCode.httpStatus) ?: HttpStatus.INTERNAL_SERVER_ERROR
        return problem(
            ex.errorCode,
            status,
            title = ex.message ?: ex.errorCode.safeDetail,
            detail = ex.message ?: ex.errorCode.safeDetail,
            retryAfterSeconds = if (ex.errorCode == ErrorCode.ERR_11) RATE_LIMIT_RETRY_AFTER_SECONDS else null
        )
    }

    /** Map catalog-governed failures while preserving the established v1 response shape. */
    @ExceptionHandler(SquarewiseException::class)
    fun governedException(ex: SquarewiseException): ResponseEntity<ProblemDetailsDto> {
        val definition = ex.definition
        val status = HttpStatus.resolve(definition.httpStatus ?: 500) ?: HttpStatus.INTERNAL_SERVER_ERROR
        return ResponseEntity.status(status)
            .headers(HttpHeaders().apply {
                if (definition.legacyCode == "ERR-11") set(HttpHeaders.RETRY_AFTER, RATE_LIMIT_RETRY_AFTER_SECONDS.toString())
            })
            .contentType(MediaType.APPLICATION_PROBLEM_JSON)
            .body(problemDetails(
                type = "https://squarewise.example/problems/${definition.errorName.lowercase()}",
                title = definition.title,
                status = status.value(),
                code = legacyCode(definition.legacyCode),
                source = serviceName,
                requestId = RequestIdContext.get(),
                detail = definition.safeDetail,
                numericCode = definition.numericCode.value,
                errorName = definition.errorName,
            ))
    }

    private fun legacyCode(value: String?): String = when (value) {
        "ERR-02" -> "VALIDATION_FAILED"
        "ERR-03" -> "UNAUTHENTICATED"
        "ERR-04" -> "FORBIDDEN"
        "ERR-05" -> "NOT_FOUND"
        "ERR-06" -> "CONFLICT"
        "ERR-11" -> "RATE_LIMITED"
        else -> "INTERNAL_ERROR"
    }

    @ExceptionHandler(Exception::class)
    fun unexpected(error: Exception): ResponseEntity<ProblemDetailsDto> {
        log.error("Unhandled unexpected exception [requestId={}]: {}", RequestIdContext.get(), error.message, error)
        return problem(
            ErrorCode.ERR_01,
            HttpStatus.INTERNAL_SERVER_ERROR
        )
    }

    private fun problem(
        code: ErrorCode,
        status: HttpStatusCode,
        title: String = "Internal server error",
        detail: String = "An unexpected error occurred",
        violations: List<FieldViolation> = emptyList(),
        retryAfterSeconds: Long? = null
    ): ResponseEntity<ProblemDetailsDto> =
        definitionFor(code).let { definition ->
        ResponseEntity.status(status)
            .headers(HttpHeaders().apply {
                retryAfterSeconds?.let { set(HttpHeaders.RETRY_AFTER, it.toString()) }
            })
            .contentType(MediaType.APPLICATION_PROBLEM_JSON)
            .body(problemDetails(
                type = "https://squarewise.example/problems/${code.name.lowercase()}",
                title = title,
                status = status.value(),
                code = mapErrorCode(code),
                source = serviceName,
                requestId = RequestIdContext.get(),
                detail = detail,
                numericCode = definition.numericCode.value,
                errorName = definition.errorName,
                violations = violations,
            ))
        }

    private fun definitionFor(code: ErrorCode): ErrorDefinition = when (code) {
        ErrorCode.ERR_02 -> PlatformErrors.REQUEST_VALIDATION_FAILED
        ErrorCode.ERR_03 -> PlatformErrors.AUTHENTICATION_REQUIRED
        ErrorCode.ERR_04 -> PlatformErrors.ACCESS_DENIED
        ErrorCode.ERR_05 -> PlatformErrors.RESOURCE_NOT_FOUND
        ErrorCode.ERR_06, ErrorCode.ERR_09 -> PlatformErrors.RESOURCE_CONFLICT
        ErrorCode.ERR_08 -> PlatformErrors.BROKER_UNAVAILABLE
        ErrorCode.ERR_11 -> PlatformErrors.SECURITY_RATE_LIMITED
        else -> PlatformErrors.UNEXPECTED_INTERNAL_ERROR
    }

    private fun problemDetails(
        type: String,
        title: String,
        status: Int,
        code: String,
        source: String,
        requestId: String,
        detail: String,
        numericCode: String? = null,
        errorName: String? = null,
        violations: List<FieldViolation> = emptyList(),
    ): ProblemDetailsDto = ProblemDetailsDto(
        type = URI(type),
        title = title,
        status = status,
        detail = detail,
        instance = "/errors/${type.substringAfterLast('/')}"
            .take(256),
        code = code,
        requestId = requestId,
        source = source,
        numericCode = numericCode,
        errorName = errorName,
        violations = violations.map { ViolationDto(it.field, it.message) },
    )

    private fun mapErrorCode(errorCode: ErrorCode): String =
        when (errorCode) {
            ErrorCode.ERR_01 -> "INTERNAL_ERROR"
            ErrorCode.ERR_02 -> "VALIDATION_FAILED"
            ErrorCode.ERR_03 -> "UNAUTHENTICATED"
            ErrorCode.ERR_04 -> "FORBIDDEN"
            ErrorCode.ERR_05 -> "NOT_FOUND"
            ErrorCode.ERR_06 -> "CONFLICT"
            ErrorCode.ERR_07 -> "INTERNAL_ERROR"
            ErrorCode.ERR_08 -> "INTERNAL_ERROR"
            ErrorCode.ERR_09 -> "CONFLICT"
            ErrorCode.ERR_10 -> "VALIDATION_FAILED"
            ErrorCode.ERR_11 -> "RATE_LIMITED"
            ErrorCode.ERR_12 -> "INTERNAL_ERROR"
        }

    companion object {
        private const val RATE_LIMIT_RETRY_AFTER_SECONDS = 60L
        private val log = LoggerFactory.getLogger(GlobalErrorHandler::class.java)
    }
}
