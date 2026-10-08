package com.subhrodip.squarewise.errors.catalog

import com.subhrodip.squarewise.errors.code.DisclosurePolicy
import com.subhrodip.squarewise.errors.code.ErrorCode
import com.subhrodip.squarewise.errors.code.ErrorDefinition
import com.subhrodip.squarewise.errors.code.ErrorSeverity
import com.subhrodip.squarewise.errors.code.RetryPolicy

/** Governed, statically compiled error definitions for the Accounts domain. */
object AccountsErrors {
    /** 111101: Invalid profile request */
    val PROFILE_REQUEST_INVALID: ErrorDefinition = SimpleErrorDefinition(
    numericCode = ErrorCode("111101"),
    errorName = "PROFILE_REQUEST_INVALID",
    title = "Invalid profile request",
    safeDetail = "The provided profile parameters are invalid.",
    messageKey = "error.profile.request_invalid",
    httpStatus = 422,
    graphqlClassification = "BAD_REQUEST",
    retryPolicy = RetryPolicy.NEVER,
    severity = ErrorSeverity.WARN,
    disclosure = DisclosurePolicy.PUBLIC
)

    /** 111102: Invalid timezone */
    val TIMEZONE_INVALID: ErrorDefinition = SimpleErrorDefinition(
    numericCode = ErrorCode("111102"),
    errorName = "TIMEZONE_INVALID",
    title = "Invalid timezone",
    safeDetail = "The specified timezone identifier is not recognized.",
    messageKey = "error.profile.timezone_invalid",
    httpStatus = 422,
    graphqlClassification = "BAD_REQUEST",
    retryPolicy = RetryPolicy.NEVER,
    severity = ErrorSeverity.WARN,
    disclosure = DisclosurePolicy.PUBLIC
)

    /** 117101: Invalid authenticated subject */
    val PROFILE_SUBJECT_INVALID: ErrorDefinition = SimpleErrorDefinition(
    numericCode = ErrorCode("117101"),
    errorName = "PROFILE_SUBJECT_INVALID",
    title = "Invalid authenticated subject",
    safeDetail = "The authenticated subject is missing or malformed.",
    messageKey = "error.profile.subject_invalid",
    httpStatus = 401,
    graphqlClassification = "UNAUTHENTICATED",
    retryPolicy = RetryPolicy.REAUTHENTICATE,
    severity = ErrorSeverity.WARN,
    disclosure = DisclosurePolicy.PUBLIC
)

    /** 117201: Profile not found */
    val AUTHENTICATED_PROFILE_NOT_FOUND: ErrorDefinition = SimpleErrorDefinition(
    numericCode = ErrorCode("117201"),
    errorName = "AUTHENTICATED_PROFILE_NOT_FOUND",
    title = "Profile not found",
    safeDetail = "The profile associated with the authenticated subject was not found.",
    messageKey = "error.profile.not_found",
    httpStatus = 404,
    graphqlClassification = "NOT_FOUND",
    retryPolicy = RetryPolicy.NEVER,
    severity = ErrorSeverity.WARN,
    disclosure = DisclosurePolicy.RESOURCE_HIDDEN_WHEN_UNAUTHORIZED
)

    /** 117202: Access denied */
    val FOREIGN_PROFILE_ACCESS_DENIED: ErrorDefinition = SimpleErrorDefinition(
    numericCode = ErrorCode("117202"),
    errorName = "FOREIGN_PROFILE_ACCESS_DENIED",
    title = "Access denied",
    safeDetail = "Access denied to foreign profile",
    messageKey = "error.profile.access_denied",
    httpStatus = 403,
    graphqlClassification = "FORBIDDEN",
    retryPolicy = RetryPolicy.NEVER,
    severity = ErrorSeverity.WARN,
    disclosure = DisclosurePolicy.PUBLIC
)

    /** 117203: Unauthorized batch lookup */
    val BATCH_LOOKUP_UNAUTHORIZED: ErrorDefinition = SimpleErrorDefinition(
    numericCode = ErrorCode("117203"),
    errorName = "BATCH_LOOKUP_UNAUTHORIZED",
    title = "Unauthorized batch lookup",
    safeDetail = "Batch profile lookup requires internal workload authority",
    messageKey = "error.profile.batch_unauthorized",
    httpStatus = 403,
    graphqlClassification = null,
    retryPolicy = RetryPolicy.NEVER,
    severity = ErrorSeverity.WARN,
    disclosure = DisclosurePolicy.PUBLIC
)

    /** 127101: Invalid login code */
    val LOGIN_CODE_INVALID: ErrorDefinition = SimpleErrorDefinition(
    numericCode = ErrorCode("127101"),
    errorName = "LOGIN_CODE_INVALID",
    title = "Invalid login code",
    safeDetail = "The provided authentication code is invalid or has expired.",
    messageKey = "error.auth.login_code_invalid",
    httpStatus = 401,
    graphqlClassification = null,
    retryPolicy = RetryPolicy.REAUTHENTICATE,
    severity = ErrorSeverity.WARN,
    disclosure = DisclosurePolicy.PUBLIC
)

    /** 127801: Login rate limit exceeded */
    val LOGIN_RATE_LIMITED: ErrorDefinition = SimpleErrorDefinition(
    numericCode = ErrorCode("127801"),
    errorName = "LOGIN_RATE_LIMITED",
    title = "Login rate limit exceeded",
    safeDetail = "Too many login attempts. Please retry after the indicated window.",
    messageKey = "error.auth.login_rate_limited",
    httpStatus = 429,
    graphqlClassification = null,
    retryPolicy = RetryPolicy.RETRY_AFTER,
    severity = ErrorSeverity.WARN,
    disclosure = DisclosurePolicy.PUBLIC
)

    /** 127802: Rate limiter unavailable */
    val LOGIN_LIMITER_UNAVAILABLE: ErrorDefinition = SimpleErrorDefinition(
    numericCode = ErrorCode("127802"),
    errorName = "LOGIN_LIMITER_UNAVAILABLE",
    title = "Rate limiter unavailable",
    safeDetail = "Authentication rate limiter is temporarily unavailable.",
    messageKey = "error.auth.limiter_unavailable",
    httpStatus = 429,
    graphqlClassification = null,
    retryPolicy = RetryPolicy.RETRY_AFTER,
    severity = ErrorSeverity.ERROR,
    disclosure = DisclosurePolicy.PUBLIC
)

    /** 137101: Invalid refresh token */
    val REFRESH_TOKEN_INVALID: ErrorDefinition = SimpleErrorDefinition(
    numericCode = ErrorCode("137101"),
    errorName = "REFRESH_TOKEN_INVALID",
    title = "Invalid refresh token",
    safeDetail = "The provided refresh token is malformed or invalid.",
    messageKey = "error.session.refresh_token_invalid",
    httpStatus = 401,
    graphqlClassification = null,
    retryPolicy = RetryPolicy.REAUTHENTICATE,
    severity = ErrorSeverity.WARN,
    disclosure = DisclosurePolicy.PUBLIC
)

    /** 137401: Session expired */
    val SESSION_EXPIRED: ErrorDefinition = SimpleErrorDefinition(
    numericCode = ErrorCode("137401"),
    errorName = "SESSION_EXPIRED",
    title = "Session expired",
    safeDetail = "The current session has expired. Please reauthenticate.",
    messageKey = "error.session.expired",
    httpStatus = 401,
    graphqlClassification = null,
    retryPolicy = RetryPolicy.REAUTHENTICATE,
    severity = ErrorSeverity.WARN,
    disclosure = DisclosurePolicy.PUBLIC
)

    /** 137402: Session revoked */
    val SESSION_REVOKED: ErrorDefinition = SimpleErrorDefinition(
    numericCode = ErrorCode("137402"),
    errorName = "SESSION_REVOKED",
    title = "Session revoked",
    safeDetail = "The session has been revoked.",
    messageKey = "error.session.revoked",
    httpStatus = 401,
    graphqlClassification = null,
    retryPolicy = RetryPolicy.REAUTHENTICATE,
    severity = ErrorSeverity.WARN,
    disclosure = DisclosurePolicy.PUBLIC
)

    /** 137403: Token replay detected */
    val REFRESH_REPLAY_DETECTED: ErrorDefinition = SimpleErrorDefinition(
    numericCode = ErrorCode("137403"),
    errorName = "REFRESH_REPLAY_DETECTED",
    title = "Token replay detected",
    safeDetail = "Refresh token replay detected. Session family revoked for security.",
    messageKey = "error.session.replay_detected",
    httpStatus = 401,
    graphqlClassification = null,
    retryPolicy = RetryPolicy.REAUTHENTICATE,
    severity = ErrorSeverity.ERROR,
    disclosure = DisclosurePolicy.PUBLIC
)

    /** 137404: Session subject mismatch */
    val SESSION_SUBJECT_MISMATCH: ErrorDefinition = SimpleErrorDefinition(
    numericCode = ErrorCode("137404"),
    errorName = "SESSION_SUBJECT_MISMATCH",
    title = "Session subject mismatch",
    safeDetail = "The session token subject does not match the requested profile.",
    messageKey = "error.session.subject_mismatch",
    httpStatus = 401,
    graphqlClassification = null,
    retryPolicy = RetryPolicy.REAUTHENTICATE,
    severity = ErrorSeverity.WARN,
    disclosure = DisclosurePolicy.PUBLIC
)

    /** 137801: Refresh rate limit exceeded */
    val REFRESH_RATE_LIMITED: ErrorDefinition = SimpleErrorDefinition(
    numericCode = ErrorCode("137801"),
    errorName = "REFRESH_RATE_LIMITED",
    title = "Refresh rate limit exceeded",
    safeDetail = "Too many token refresh attempts. Please retry later.",
    messageKey = "error.session.refresh_rate_limited",
    httpStatus = 429,
    graphqlClassification = null,
    retryPolicy = RetryPolicy.RETRY_AFTER,
    severity = ErrorSeverity.WARN,
    disclosure = DisclosurePolicy.PUBLIC
)

    /** 147401: Account deletion in progress */
    val ACCOUNT_DELETION_ALREADY_REQUESTED: ErrorDefinition = SimpleErrorDefinition(
    numericCode = ErrorCode("147401"),
    errorName = "ACCOUNT_DELETION_ALREADY_REQUESTED",
    title = "Account deletion in progress",
    safeDetail = "A deletion request for this account is already pending.",
    messageKey = "error.lifecycle.deletion_pending",
    httpStatus = 409,
    graphqlClassification = null,
    retryPolicy = RetryPolicy.NEVER,
    severity = ErrorSeverity.WARN,
    disclosure = DisclosurePolicy.PUBLIC
)

    /** 147402: Account export in progress */
    val ACCOUNT_EXPORT_ALREADY_PENDING: ErrorDefinition = SimpleErrorDefinition(
    numericCode = ErrorCode("147402"),
    errorName = "ACCOUNT_EXPORT_ALREADY_PENDING",
    title = "Account export in progress",
    safeDetail = "A data export request for this account is already processing.",
    messageKey = "error.lifecycle.export_pending",
    httpStatus = 409,
    graphqlClassification = null,
    retryPolicy = RetryPolicy.NEVER,
    severity = ErrorSeverity.INFO,
    disclosure = DisclosurePolicy.PUBLIC
)

    /** 157301: Identity already linked */
    val IDENTITY_ALREADY_LINKED: ErrorDefinition = SimpleErrorDefinition(
    numericCode = ErrorCode("157301"),
    errorName = "IDENTITY_ALREADY_LINKED",
    title = "Identity already linked",
    safeDetail = "This external identity is already linked to another account.",
    messageKey = "error.identity.already_linked",
    httpStatus = 409,
    graphqlClassification = null,
    retryPolicy = RetryPolicy.NEVER,
    severity = ErrorSeverity.WARN,
    disclosure = DisclosurePolicy.PUBLIC
)

    /** All definitions in authoritative catalog order. */
    val all: List<ErrorDefinition> = listOf(
        PROFILE_REQUEST_INVALID,
        TIMEZONE_INVALID,
        PROFILE_SUBJECT_INVALID,
        AUTHENTICATED_PROFILE_NOT_FOUND,
        FOREIGN_PROFILE_ACCESS_DENIED,
        BATCH_LOOKUP_UNAUTHORIZED,
        LOGIN_CODE_INVALID,
        LOGIN_RATE_LIMITED,
        LOGIN_LIMITER_UNAVAILABLE,
        REFRESH_TOKEN_INVALID,
        SESSION_EXPIRED,
        SESSION_REVOKED,
        REFRESH_REPLAY_DETECTED,
        SESSION_SUBJECT_MISMATCH,
        REFRESH_RATE_LIMITED,
        ACCOUNT_DELETION_ALREADY_REQUESTED,
        ACCOUNT_EXPORT_ALREADY_PENDING,
        IDENTITY_ALREADY_LINKED,
    )
}
