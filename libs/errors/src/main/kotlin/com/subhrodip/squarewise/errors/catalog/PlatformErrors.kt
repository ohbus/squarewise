package com.subhrodip.squarewise.errors.catalog

import com.subhrodip.squarewise.errors.code.DisclosurePolicy
import com.subhrodip.squarewise.errors.code.ErrorCode
import com.subhrodip.squarewise.errors.code.ErrorDefinition
import com.subhrodip.squarewise.errors.code.ErrorSeverity
import com.subhrodip.squarewise.errors.code.RetryPolicy

/** Governed, statically compiled error definitions for the Platform domain. */
object PlatformErrors {
    /** 911101: Validation failed */
    val REQUEST_VALIDATION_FAILED: ErrorDefinition = SimpleErrorDefinition(
    numericCode = ErrorCode("911101"),
    errorName = "REQUEST_VALIDATION_FAILED",
    title = "Validation failed",
    safeDetail = "One or more request parameters failed validation.",
    messageKey = "error.platform.validation_failed",
    httpStatus = 422,
    graphqlClassification = null,
    retryPolicy = RetryPolicy.NEVER,
    severity = ErrorSeverity.WARN,
    disclosure = DisclosurePolicy.PUBLIC
)

    /** 911102: Malformed request body */
    val REQUEST_BODY_MALFORMED: ErrorDefinition = SimpleErrorDefinition(
    numericCode = ErrorCode("911102"),
    errorName = "REQUEST_BODY_MALFORMED",
    title = "Malformed request body",
    safeDetail = "Request payload is not valid JSON or cannot be deserialized.",
    messageKey = "error.platform.body_malformed",
    httpStatus = 400,
    graphqlClassification = null,
    retryPolicy = RetryPolicy.NEVER,
    severity = ErrorSeverity.WARN,
    disclosure = DisclosurePolicy.PUBLIC
)

    /** 911103: Invalid request parameter */
    val REQUEST_VALUE_INVALID: ErrorDefinition = SimpleErrorDefinition(
    numericCode = ErrorCode("911103"),
    errorName = "REQUEST_VALUE_INVALID",
    title = "Invalid request parameter",
    safeDetail = "A request parameter value is of incorrect type or format.",
    messageKey = "error.platform.parameter_invalid",
    httpStatus = 422,
    graphqlClassification = null,
    retryPolicy = RetryPolicy.NEVER,
    severity = ErrorSeverity.WARN,
    disclosure = DisclosurePolicy.PUBLIC
)

    /** 911104: Request payload too large */
    val REQUEST_BODY_TOO_LARGE: ErrorDefinition = SimpleErrorDefinition(
    numericCode = ErrorCode("911104"),
    errorName = "REQUEST_BODY_TOO_LARGE",
    title = "Request payload too large",
    safeDetail = "The request body exceeds the maximum permitted size limit.",
    messageKey = "error.platform.payload_too_large",
    httpStatus = 413,
    graphqlClassification = null,
    retryPolicy = RetryPolicy.NEVER,
    severity = ErrorSeverity.WARN,
    disclosure = DisclosurePolicy.PUBLIC
)

    /** 911105: Unsupported media type */
    val MEDIA_TYPE_UNSUPPORTED: ErrorDefinition = SimpleErrorDefinition(
    numericCode = ErrorCode("911105"),
    errorName = "MEDIA_TYPE_UNSUPPORTED",
    title = "Unsupported media type",
    safeDetail = "The Content-Type header is missing or not supported for this endpoint.",
    messageKey = "error.platform.unsupported_media_type",
    httpStatus = 415,
    graphqlClassification = null,
    retryPolicy = RetryPolicy.NEVER,
    severity = ErrorSeverity.WARN,
    disclosure = DisclosurePolicy.PUBLIC
)

    /** 911106: Method not allowed */
    val METHOD_NOT_ALLOWED: ErrorDefinition = SimpleErrorDefinition(
    numericCode = ErrorCode("911106"),
    errorName = "METHOD_NOT_ALLOWED",
    title = "Method not allowed",
    safeDetail = "The requested HTTP method is not allowed on this resource.",
    messageKey = "error.platform.method_not_allowed",
    httpStatus = 405,
    graphqlClassification = null,
    retryPolicy = RetryPolicy.NEVER,
    severity = ErrorSeverity.WARN,
    disclosure = DisclosurePolicy.PUBLIC
)

    /** 911107: Not acceptable */
    val REPRESENTATION_NOT_ACCEPTABLE: ErrorDefinition = SimpleErrorDefinition(
    numericCode = ErrorCode("911107"),
    errorName = "REPRESENTATION_NOT_ACCEPTABLE",
    title = "Not acceptable",
    safeDetail = "The Accept header cannot be satisfied with available media representations.",
    messageKey = "error.platform.not_acceptable",
    httpStatus = 406,
    graphqlClassification = null,
    retryPolicy = RetryPolicy.NEVER,
    severity = ErrorSeverity.WARN,
    disclosure = DisclosurePolicy.PUBLIC
)

    /** 919201: Resource not found */
    val RESOURCE_NOT_FOUND: ErrorDefinition = SimpleErrorDefinition(
    numericCode = ErrorCode("919201"),
    errorName = "RESOURCE_NOT_FOUND",
    title = "Resource not found",
    safeDetail = "The requested API resource or route does not exist.",
    messageKey = "error.platform.not_found",
    httpStatus = 404,
    graphqlClassification = null,
    retryPolicy = RetryPolicy.NEVER,
    severity = ErrorSeverity.WARN,
    disclosure = DisclosurePolicy.PUBLIC
)

    /** 919301: Resource conflict */
    val RESOURCE_CONFLICT: ErrorDefinition = SimpleErrorDefinition(
    numericCode = ErrorCode("919301"),
    errorName = "RESOURCE_CONFLICT",
    title = "Resource conflict",
    safeDetail = "The requested operation conflicts with the current resource state.",
    messageKey = "error.platform.conflict",
    httpStatus = 409,
    graphqlClassification = null,
    retryPolicy = RetryPolicy.NEVER,
    severity = ErrorSeverity.WARN,
    disclosure = DisclosurePolicy.PUBLIC
)

    /** 919901: Internal server error */
    val UNEXPECTED_INTERNAL_ERROR: ErrorDefinition = SimpleErrorDefinition(
    numericCode = ErrorCode("919901"),
    errorName = "UNEXPECTED_INTERNAL_ERROR",
    title = "Internal server error",
    safeDetail = "An unexpected error occurred while processing the request.",
    messageKey = "error.platform.unexpected_internal",
    httpStatus = 500,
    graphqlClassification = null,
    retryPolicy = RetryPolicy.NEVER,
    severity = ErrorSeverity.CRITICAL,
    disclosure = DisclosurePolicy.PUBLIC
)

    /** 919902: Response serialization failed */
    val RESPONSE_SERIALIZATION_FAILED: ErrorDefinition = SimpleErrorDefinition(
    numericCode = ErrorCode("919902"),
    errorName = "RESPONSE_SERIALIZATION_FAILED",
    title = "Response serialization failed",
    safeDetail = "Could not format the response payload for transmission.",
    messageKey = "error.platform.serialization_failed",
    httpStatus = 500,
    graphqlClassification = null,
    retryPolicy = RetryPolicy.NEVER,
    severity = ErrorSeverity.ERROR,
    disclosure = DisclosurePolicy.PUBLIC
)

    /** 927101: Authentication required */
    val AUTHENTICATION_REQUIRED: ErrorDefinition = SimpleErrorDefinition(
    numericCode = ErrorCode("927101"),
    errorName = "AUTHENTICATION_REQUIRED",
    title = "Authentication required",
    safeDetail = "Missing, invalid, or expired credentials.",
    messageKey = "error.platform.authentication_required",
    httpStatus = 401,
    graphqlClassification = null,
    retryPolicy = RetryPolicy.REAUTHENTICATE,
    severity = ErrorSeverity.WARN,
    disclosure = DisclosurePolicy.PUBLIC
)

    /** 927501: Access denied */
    val ACCESS_DENIED: ErrorDefinition = SimpleErrorDefinition(
    numericCode = ErrorCode("927501"),
    errorName = "ACCESS_DENIED",
    title = "Access denied",
    safeDetail = "The authenticated caller does not have permission to perform this action.",
    messageKey = "error.platform.access_denied",
    httpStatus = 403,
    graphqlClassification = null,
    retryPolicy = RetryPolicy.NEVER,
    severity = ErrorSeverity.WARN,
    disclosure = DisclosurePolicy.PUBLIC
)

    /** 927502: CSRF token rejected */
    val CSRF_REJECTED: ErrorDefinition = SimpleErrorDefinition(
    numericCode = ErrorCode("927502"),
    errorName = "CSRF_REJECTED",
    title = "CSRF token rejected",
    safeDetail = "Missing or invalid Cross-Site Request Forgery validation token.",
    messageKey = "error.platform.csrf_rejected",
    httpStatus = 403,
    graphqlClassification = null,
    retryPolicy = RetryPolicy.NEVER,
    severity = ErrorSeverity.WARN,
    disclosure = DisclosurePolicy.PUBLIC
)

    /** 927503: Origin rejected */
    val ORIGIN_REJECTED: ErrorDefinition = SimpleErrorDefinition(
    numericCode = ErrorCode("927503"),
    errorName = "ORIGIN_REJECTED",
    title = "Origin rejected",
    safeDetail = "The request Origin header is not allowed by security policy.",
    messageKey = "error.platform.origin_rejected",
    httpStatus = 403,
    graphqlClassification = null,
    retryPolicy = RetryPolicy.NEVER,
    severity = ErrorSeverity.WARN,
    disclosure = DisclosurePolicy.PUBLIC
)

    /** 927801: Security rate limit exceeded */
    val SECURITY_RATE_LIMITED: ErrorDefinition = SimpleErrorDefinition(
    numericCode = ErrorCode("927801"),
    errorName = "SECURITY_RATE_LIMITED",
    title = "Security rate limit exceeded",
    safeDetail = "Too many requests to protected endpoint. Access temporarily throttled.",
    messageKey = "error.platform.rate_limited",
    httpStatus = 429,
    graphqlClassification = null,
    retryPolicy = RetryPolicy.RETRY_AFTER,
    severity = ErrorSeverity.WARN,
    disclosure = DisclosurePolicy.PUBLIC
)

    /** 934801: Database service unavailable */
    val DATABASE_UNAVAILABLE: ErrorDefinition = SimpleErrorDefinition(
    numericCode = ErrorCode("934801"),
    errorName = "DATABASE_UNAVAILABLE",
    title = "Database service unavailable",
    safeDetail = "The persistence layer is temporarily unavailable or pool is exhausted.",
    messageKey = "error.platform.database_unavailable",
    httpStatus = 503,
    graphqlClassification = null,
    retryPolicy = RetryPolicy.RETRY_AFTER,
    severity = ErrorSeverity.CRITICAL,
    disclosure = DisclosurePolicy.PUBLIC
)

    /** 934802: Database operation timeout */
    val DATABASE_OPERATION_TIMEOUT: ErrorDefinition = SimpleErrorDefinition(
    numericCode = ErrorCode("934802"),
    errorName = "DATABASE_OPERATION_TIMEOUT",
    title = "Database operation timeout",
    safeDetail = "Database query timed out before completing.",
    messageKey = "error.platform.database_timeout",
    httpStatus = 503,
    graphqlClassification = null,
    retryPolicy = RetryPolicy.RETRY_AFTER,
    severity = ErrorSeverity.ERROR,
    disclosure = DisclosurePolicy.PUBLIC
)

    /** 934601: Database integrity violation */
    val DATABASE_DATA_INCONSISTENT: ErrorDefinition = SimpleErrorDefinition(
    numericCode = ErrorCode("934601"),
    errorName = "DATABASE_DATA_INCONSISTENT",
    title = "Database integrity violation",
    safeDetail = "A data integrity violation or unexpected constraint failure occurred.",
    messageKey = "error.platform.database_integrity",
    httpStatus = 500,
    graphqlClassification = null,
    retryPolicy = RetryPolicy.NEVER,
    severity = ErrorSeverity.CRITICAL,
    disclosure = DisclosurePolicy.PUBLIC
)

    /** 945701: Message publish failed */
    val MESSAGE_PUBLISH_FAILED: ErrorDefinition = SimpleErrorDefinition(
    numericCode = ErrorCode("945701"),
    errorName = "MESSAGE_PUBLISH_FAILED",
    title = "Message publish failed",
    safeDetail = "Broker returned nack or message publish timed out.",
    messageKey = "error.platform.message_publish_failed",
    httpStatus = null,
    graphqlClassification = null,
    retryPolicy = RetryPolicy.RETRY_AFTER,
    severity = ErrorSeverity.CRITICAL,
    disclosure = DisclosurePolicy.PUBLIC
)

    /** 945801: Message broker unavailable */
    val BROKER_UNAVAILABLE: ErrorDefinition = SimpleErrorDefinition(
    numericCode = ErrorCode("945801"),
    errorName = "BROKER_UNAVAILABLE",
    title = "Message broker unavailable",
    safeDetail = "RabbitMQ broker connection cannot be established or is refused.",
    messageKey = "error.platform.broker_unavailable",
    httpStatus = null,
    graphqlClassification = null,
    retryPolicy = RetryPolicy.RETRY_AFTER,
    severity = ErrorSeverity.CRITICAL,
    disclosure = DisclosurePolicy.PUBLIC
)

    /** 945702: Invalid message envelope */
    val MESSAGE_ENVELOPE_INVALID: ErrorDefinition = SimpleErrorDefinition(
    numericCode = ErrorCode("945702"),
    errorName = "MESSAGE_ENVELOPE_INVALID",
    title = "Invalid message envelope",
    safeDetail = "Message envelope is missing required metadata or has an unknown schema version.",
    messageKey = "error.platform.envelope_invalid",
    httpStatus = null,
    graphqlClassification = null,
    retryPolicy = RetryPolicy.NEVER,
    severity = ErrorSeverity.ERROR,
    disclosure = DisclosurePolicy.PUBLIC
)

    /** 948901: Invalid platform configuration */
    val PLATFORM_CONFIGURATION_INVALID: ErrorDefinition = SimpleErrorDefinition(
    numericCode = ErrorCode("948901"),
    errorName = "PLATFORM_CONFIGURATION_INVALID",
    title = "Invalid platform configuration",
    safeDetail = "Application startup halted due to missing or invalid required configuration.",
    messageKey = "error.platform.config_invalid",
    httpStatus = null,
    graphqlClassification = null,
    retryPolicy = RetryPolicy.NEVER,
    severity = ErrorSeverity.CRITICAL,
    disclosure = DisclosurePolicy.PUBLIC
)

    /** 958901: Observability pipeline degraded */
    val OBSERVABILITY_PIPELINE_FAILED: ErrorDefinition = SimpleErrorDefinition(
    numericCode = ErrorCode("958901"),
    errorName = "OBSERVABILITY_PIPELINE_FAILED",
    title = "Observability pipeline degraded",
    safeDetail = "Metrics or tracing export encountered a non-fatal failure.",
    messageKey = "error.platform.observability_failed",
    httpStatus = null,
    graphqlClassification = null,
    retryPolicy = RetryPolicy.NEVER,
    severity = ErrorSeverity.WARN,
    disclosure = DisclosurePolicy.PUBLIC
)

    /** 968901: Identifier generation failed */
    val IDENTIFIER_GENERATION_FAILED: ErrorDefinition = SimpleErrorDefinition(
    numericCode = ErrorCode("968901"),
    errorName = "IDENTIFIER_GENERATION_FAILED",
    title = "Identifier generation failed",
    safeDetail = "Secure random source or UUID generator encountered an internal failure.",
    messageKey = "error.platform.id_generation_failed",
    httpStatus = 500,
    graphqlClassification = null,
    retryPolicy = RetryPolicy.NEVER,
    severity = ErrorSeverity.CRITICAL,
    disclosure = DisclosurePolicy.PUBLIC
)

    /** All definitions in authoritative catalog order. */
    val all: List<ErrorDefinition> = listOf(
        REQUEST_VALIDATION_FAILED,
        REQUEST_BODY_MALFORMED,
        REQUEST_VALUE_INVALID,
        REQUEST_BODY_TOO_LARGE,
        MEDIA_TYPE_UNSUPPORTED,
        METHOD_NOT_ALLOWED,
        REPRESENTATION_NOT_ACCEPTABLE,
        RESOURCE_NOT_FOUND,
        RESOURCE_CONFLICT,
        UNEXPECTED_INTERNAL_ERROR,
        RESPONSE_SERIALIZATION_FAILED,
        AUTHENTICATION_REQUIRED,
        ACCESS_DENIED,
        CSRF_REJECTED,
        ORIGIN_REJECTED,
        SECURITY_RATE_LIMITED,
        DATABASE_UNAVAILABLE,
        DATABASE_OPERATION_TIMEOUT,
        DATABASE_DATA_INCONSISTENT,
        MESSAGE_PUBLISH_FAILED,
        BROKER_UNAVAILABLE,
        MESSAGE_ENVELOPE_INVALID,
        PLATFORM_CONFIGURATION_INVALID,
        OBSERVABILITY_PIPELINE_FAILED,
        IDENTIFIER_GENERATION_FAILED,
    )
}
