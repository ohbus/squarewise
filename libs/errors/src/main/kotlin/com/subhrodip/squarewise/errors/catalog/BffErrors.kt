package com.subhrodip.squarewise.errors.catalog

import com.subhrodip.squarewise.errors.code.DisclosurePolicy
import com.subhrodip.squarewise.errors.code.ErrorCode
import com.subhrodip.squarewise.errors.code.ErrorDefinition
import com.subhrodip.squarewise.errors.code.ErrorSeverity
import com.subhrodip.squarewise.errors.code.RetryPolicy

/** Governed, statically compiled error definitions for the BFF domain. */
object BffErrors {
    /** 411101: Invalid GraphQL input */
    val GRAPHQL_INPUT_INVALID: ErrorDefinition = SimpleErrorDefinition(
    numericCode = ErrorCode("411101"),
    errorName = "GRAPHQL_INPUT_INVALID",
    legacyCode = "VALIDATION_FAILED",
    title = "Invalid GraphQL input",
    safeDetail = "The provided GraphQL query arguments are invalid.",
    messageKey = "error.graphql.input_invalid",
    httpStatus = 400,
    graphqlClassification = "BAD_REQUEST",
    retryPolicy = RetryPolicy.NEVER,
    severity = ErrorSeverity.WARN,
    disclosure = DisclosurePolicy.PUBLIC
)

    /** 411102: Invalid GraphQL operation */
    val GRAPHQL_OPERATION_INVALID: ErrorDefinition = SimpleErrorDefinition(
    numericCode = ErrorCode("411102"),
    errorName = "GRAPHQL_OPERATION_INVALID",
    legacyCode = "VALIDATION_FAILED",
    title = "Invalid GraphQL operation",
    safeDetail = "The requested GraphQL operation is not supported.",
    messageKey = "error.graphql.operation_invalid",
    httpStatus = 400,
    graphqlClassification = "BAD_REQUEST",
    retryPolicy = RetryPolicy.NEVER,
    severity = ErrorSeverity.WARN,
    disclosure = DisclosurePolicy.PUBLIC
)

    /** 412901: GraphQL aggregation failed */
    val GRAPHQL_AGGREGATION_FAILED: ErrorDefinition = SimpleErrorDefinition(
    numericCode = ErrorCode("412901"),
    errorName = "GRAPHQL_AGGREGATION_FAILED",
    legacyCode = "INTERNAL_ERROR",
    title = "GraphQL aggregation failed",
    safeDetail = "Failed to assemble response from upstream services.",
    messageKey = "error.graphql.aggregation_failed",
    httpStatus = 500,
    graphqlClassification = "INTERNAL_ERROR",
    retryPolicy = RetryPolicy.NEVER,
    severity = ErrorSeverity.ERROR,
    disclosure = DisclosurePolicy.PUBLIC
)

    /** 426701: Invalid upstream response */
    val UPSTREAM_PROTOCOL_INVALID: ErrorDefinition = SimpleErrorDefinition(
    numericCode = ErrorCode("426701"),
    errorName = "UPSTREAM_PROTOCOL_INVALID",
    legacyCode = "INTERNAL_ERROR",
    title = "Invalid upstream response",
    safeDetail = "The upstream service returned an unparseable response.",
    messageKey = "error.upstream.protocol_invalid",
    httpStatus = 502,
    graphqlClassification = "INTERNAL_ERROR",
    retryPolicy = RetryPolicy.RETRY_AFTER,
    severity = ErrorSeverity.ERROR,
    disclosure = DisclosurePolicy.PUBLIC
)

    /** 426702: Failed to decode upstream response */
    val UPSTREAM_RESPONSE_DECODE_FAILED: ErrorDefinition = SimpleErrorDefinition(
    numericCode = ErrorCode("426702"),
    errorName = "UPSTREAM_RESPONSE_DECODE_FAILED",
    legacyCode = "INTERNAL_ERROR",
    title = "Failed to decode upstream response",
    safeDetail = "Could not decode data payload from upstream service.",
    messageKey = "error.upstream.decode_failed",
    httpStatus = 502,
    graphqlClassification = "INTERNAL_ERROR",
    retryPolicy = RetryPolicy.NEVER,
    severity = ErrorSeverity.ERROR,
    disclosure = DisclosurePolicy.PUBLIC
)

    /** 426801: Upstream service timeout */
    val UPSTREAM_TIMEOUT: ErrorDefinition = SimpleErrorDefinition(
    numericCode = ErrorCode("426801"),
    errorName = "UPSTREAM_TIMEOUT",
    legacyCode = "INTERNAL_ERROR",
    title = "Upstream service timeout",
    safeDetail = "The upstream service did not respond within the configured timeout.",
    messageKey = "error.upstream.timeout",
    httpStatus = 504,
    graphqlClassification = "INTERNAL_ERROR",
    retryPolicy = RetryPolicy.RETRY_AFTER,
    severity = ErrorSeverity.WARN,
    disclosure = DisclosurePolicy.PUBLIC
)

    /** 426802: Upstream service unavailable */
    val UPSTREAM_UNAVAILABLE: ErrorDefinition = SimpleErrorDefinition(
    numericCode = ErrorCode("426802"),
    errorName = "UPSTREAM_UNAVAILABLE",
    legacyCode = "INTERNAL_ERROR",
    title = "Upstream service unavailable",
    safeDetail = "The upstream service is currently unreachable or circuit is open.",
    messageKey = "error.upstream.unavailable",
    httpStatus = 503,
    graphqlClassification = "INTERNAL_ERROR",
    retryPolicy = RetryPolicy.RETRY_AFTER,
    severity = ErrorSeverity.ERROR,
    disclosure = DisclosurePolicy.PUBLIC
)

    /** 426803: Upstream TLS handshake failed */
    val UPSTREAM_TLS_FAILED: ErrorDefinition = SimpleErrorDefinition(
    numericCode = ErrorCode("426803"),
    errorName = "UPSTREAM_TLS_FAILED",
    legacyCode = "INTERNAL_ERROR",
    title = "Upstream TLS handshake failed",
    safeDetail = "Secure TLS connection to the upstream service could not be established.",
    messageKey = "error.upstream.tls_failed",
    httpStatus = 502,
    graphqlClassification = "INTERNAL_ERROR",
    retryPolicy = RetryPolicy.NEVER,
    severity = ErrorSeverity.ERROR,
    disclosure = DisclosurePolicy.PUBLIC
)

    /** 437801: Subscription limit exceeded */
    val SUBSCRIPTION_LIMIT_EXCEEDED: ErrorDefinition = SimpleErrorDefinition(
    numericCode = ErrorCode("437801"),
    errorName = "SUBSCRIPTION_LIMIT_EXCEEDED",
    legacyCode = "RATE_LIMITED",
    title = "Subscription limit exceeded",
    safeDetail = "Maximum concurrent WebSocket subscriptions exceeded for this connection.",
    messageKey = "error.live_update.limit_exceeded",
    httpStatus = 429,
    graphqlClassification = "EXECUTION_ERROR",
    retryPolicy = RetryPolicy.RETRY_AFTER,
    severity = ErrorSeverity.WARN,
    disclosure = DisclosurePolicy.PUBLIC
)

    /** 437802: Live update service unavailable */
    val LIVE_UPDATE_UPSTREAM_UNAVAILABLE: ErrorDefinition = SimpleErrorDefinition(
    numericCode = ErrorCode("437802"),
    errorName = "LIVE_UPDATE_UPSTREAM_UNAVAILABLE",
    legacyCode = "INTERNAL_ERROR",
    title = "Live update service unavailable",
    safeDetail = "Subscription event stream is temporarily disconnected from upstream.",
    messageKey = "error.live_update.unavailable",
    httpStatus = 503,
    graphqlClassification = "INTERNAL_ERROR",
    retryPolicy = RetryPolicy.RETRY_AFTER,
    severity = ErrorSeverity.ERROR,
    disclosure = DisclosurePolicy.PUBLIC
)

    /** 435701: Invalid live update event */
    val LIVE_UPDATE_EVENT_INVALID: ErrorDefinition = SimpleErrorDefinition(
    numericCode = ErrorCode("435701"),
    errorName = "LIVE_UPDATE_EVENT_INVALID",
    legacyCode = "INTERNAL_ERROR",
    title = "Invalid live update event",
    safeDetail = "The received live update event could not be processed for subscribers.",
    messageKey = "error.live_update.event_invalid",
    httpStatus = 500,
    graphqlClassification = "INTERNAL_ERROR",
    retryPolicy = RetryPolicy.NEVER,
    severity = ErrorSeverity.ERROR,
    disclosure = DisclosurePolicy.PUBLIC
)

    /** All definitions in authoritative catalog order. */
    val all: List<ErrorDefinition> = listOf(
        GRAPHQL_INPUT_INVALID,
        GRAPHQL_OPERATION_INVALID,
        GRAPHQL_AGGREGATION_FAILED,
        UPSTREAM_PROTOCOL_INVALID,
        UPSTREAM_RESPONSE_DECODE_FAILED,
        UPSTREAM_TIMEOUT,
        UPSTREAM_UNAVAILABLE,
        UPSTREAM_TLS_FAILED,
        SUBSCRIPTION_LIMIT_EXCEEDED,
        LIVE_UPDATE_UPSTREAM_UNAVAILABLE,
        LIVE_UPDATE_EVENT_INVALID,
    )
}
