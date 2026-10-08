package com.subhrodip.squarewise.errors.code

/**
 * Immutable metadata for one governed Squarewise failure or terminal outcome.
 *
 * Implementations are compiled static definitions. Callers must use direct
 * properties rather than reflection, message parsing, or runtime catalog lookup.
 */
interface ErrorDefinition {
    /** Canonical six-digit machine identity. */
    val numericCode: ErrorCode

    /** Globally unique immutable symbolic identity. */
    val errorName: String

    /** High-level coarse error category and family for client routing. */
    val category: CategoryCode
        get() = when {
            errorName in setOf("REQUEST_BODY_MALFORMED", "GRAPHQL_OPERATION_INVALID") -> CategoryCode.MALFORMED_REQUEST
            errorName in setOf("SYNC_CURSOR_EXPIRED", "INVITATION_EXPIRED") || httpStatus == 410 -> CategoryCode.RESOURCE_GONE
            httpStatus == 429 || "RATE_LIMITED" in errorName || "LIMITER" in errorName || ("LIMIT" in errorName && "EXCEEDED" in errorName) -> CategoryCode.RATE_LIMIT_EXCEEDED
            httpStatus == 401 || "UNAUTHENTICATED" in errorName || "TOKEN" in errorName || "SESSION" in errorName || ("LOGIN" in errorName && !errorName.contains("RATE") && !errorName.contains("LIMIT")) -> CategoryCode.AUTHENTICATION_ERROR
            httpStatus == 403 || "ACCESS_DENIED" in errorName || "FORBIDDEN" in errorName || "CSRF" in errorName || "ORIGIN" in errorName -> CategoryCode.AUTHORIZATION_ERROR
            httpStatus == 404 || "NOT_FOUND" in errorName || "HIDDEN" in errorName -> CategoryCode.NOT_FOUND
            httpStatus == 409 || "CONFLICT" in errorName || "ALREADY" in errorName || "ARCHIVED" in errorName || "PAUSED" in errorName || "REVERSED" in errorName || "CLAIMED" in errorName || "REVOKED" in errorName || "REMOVED" in errorName || "BOUND" in errorName || "DUPLICATE" in errorName -> CategoryCode.STATE_CONFLICT
            errorName in setOf("ALLOCATION_SUM_MISMATCH", "PARTICIPANT_SET_INVALID", "INSUFFICIENT_BALANCE") -> CategoryCode.BUSINESS_RULE_VIOLATION
            httpStatus == 422 -> CategoryCode.VALIDATION_ERROR
            else -> CategoryCode.INTERNAL_ERROR
        }

    /** Stable, locale-neutral public title. */
    val title: String

    /** Redacted public detail. */
    val safeDetail: String

    /** Client localization key. */
    val messageKey: String

    /** Default HTTP status when this definition crosses REST. */
    val httpStatus: Int?

    /** GraphQL classification when this definition crosses GraphQL. */
    val graphqlClassification: String?

    /** Client or worker remediation policy. */
    val retryPolicy: RetryPolicy

    /** Operational severity. */
    val severity: ErrorSeverity

    /** Public disclosure boundary. */
    val disclosure: DisclosurePolicy
}
