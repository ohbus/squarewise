package com.subhrodip.squarewise.errors.catalog

import com.subhrodip.squarewise.errors.code.DisclosurePolicy
import com.subhrodip.squarewise.errors.code.ErrorCode
import com.subhrodip.squarewise.errors.code.ErrorDefinition
import com.subhrodip.squarewise.errors.code.ErrorSeverity
import com.subhrodip.squarewise.errors.code.RetryPolicy

/** Governed, statically compiled error definitions for the Notifications domain. */
object NotificationErrors {
    /** 311101: Invalid inbox cursor */
    val INBOX_CURSOR_INVALID: ErrorDefinition = SimpleErrorDefinition(
    numericCode = ErrorCode("311101"),
    errorName = "INBOX_CURSOR_INVALID",
    legacyCode = "ERR-02",
    title = "Invalid inbox cursor",
    safeDetail = "The provided inbox pagination cursor is invalid.",
    messageKey = "error.inbox.cursor_invalid",
    httpStatus = 400,
    graphqlClassification = null,
    retryPolicy = RetryPolicy.NEVER,
    severity = ErrorSeverity.WARN,
    disclosure = DisclosurePolicy.PUBLIC
)

    /** 311102: Inbox limit out of range */
    val INBOX_LIMIT_OUT_OF_RANGE: ErrorDefinition = SimpleErrorDefinition(
    numericCode = ErrorCode("311102"),
    errorName = "INBOX_LIMIT_OUT_OF_RANGE",
    legacyCode = "ERR-02",
    title = "Inbox limit out of range",
    safeDetail = "The requested pagination limit exceeds the allowed bound.",
    messageKey = "error.inbox.limit_invalid",
    httpStatus = 400,
    graphqlClassification = null,
    retryPolicy = RetryPolicy.NEVER,
    severity = ErrorSeverity.WARN,
    disclosure = DisclosurePolicy.PUBLIC
)

    /** 317201: Notification not found */
    val INBOX_NOTIFICATION_NOT_FOUND: ErrorDefinition = SimpleErrorDefinition(
    numericCode = ErrorCode("317201"),
    errorName = "INBOX_NOTIFICATION_NOT_FOUND",
    legacyCode = "ERR-05",
    title = "Notification not found",
    safeDetail = "The requested notification does not exist.",
    messageKey = "error.inbox.not_found",
    httpStatus = 404,
    graphqlClassification = null,
    retryPolicy = RetryPolicy.NEVER,
    severity = ErrorSeverity.WARN,
    disclosure = DisclosurePolicy.PUBLIC
)

    /** 317202: Notification not found */
    val INBOX_ACCESS_HIDDEN: ErrorDefinition = SimpleErrorDefinition(
    numericCode = ErrorCode("317202"),
    errorName = "INBOX_ACCESS_HIDDEN",
    legacyCode = "ERR-05",
    title = "Notification not found",
    safeDetail = "The requested notification does not exist.",
    messageKey = "error.inbox.not_found",
    httpStatus = 404,
    graphqlClassification = null,
    retryPolicy = RetryPolicy.NEVER,
    severity = ErrorSeverity.WARN,
    disclosure = DisclosurePolicy.PUBLIC
)

    /** 325701: Notification dispatch failed */
    val NOTIFICATION_DISPATCH_FAILED: ErrorDefinition = SimpleErrorDefinition(
    numericCode = ErrorCode("325701"),
    errorName = "NOTIFICATION_DISPATCH_FAILED",
    legacyCode = "ERR-07",
    title = "Notification dispatch failed",
    safeDetail = "Failed to deliver notification through the intended channel.",
    messageKey = "error.delivery.dispatch_failed",
    httpStatus = null,
    graphqlClassification = null,
    retryPolicy = RetryPolicy.RETRY_AFTER,
    severity = ErrorSeverity.ERROR,
    disclosure = DisclosurePolicy.PUBLIC
)

    /** 325601: Corrupt notification payload */
    val NOTIFICATION_PAYLOAD_CORRUPT: ErrorDefinition = SimpleErrorDefinition(
    numericCode = ErrorCode("325601"),
    errorName = "NOTIFICATION_PAYLOAD_CORRUPT",
    legacyCode = "ERR-07",
    title = "Corrupt notification payload",
    safeDetail = "Notification event payload could not be parsed or validated.",
    messageKey = "error.delivery.payload_corrupt",
    httpStatus = null,
    graphqlClassification = null,
    retryPolicy = RetryPolicy.NEVER,
    severity = ErrorSeverity.CRITICAL,
    disclosure = DisclosurePolicy.PUBLIC
)

    /** 331101: Invalid notification preference */
    val NOTIFICATION_PREFERENCE_INVALID: ErrorDefinition = SimpleErrorDefinition(
    numericCode = ErrorCode("331101"),
    errorName = "NOTIFICATION_PREFERENCE_INVALID",
    legacyCode = "ERR-02",
    title = "Invalid notification preference",
    safeDetail = "The specified notification channel or preference setting is invalid.",
    messageKey = "error.preferences.invalid",
    httpStatus = 400,
    graphqlClassification = null,
    retryPolicy = RetryPolicy.NEVER,
    severity = ErrorSeverity.WARN,
    disclosure = DisclosurePolicy.PUBLIC
)

    /** 341101: Email recipient required */
    val EMAIL_RECIPIENT_REQUIRED: ErrorDefinition = SimpleErrorDefinition(
    numericCode = ErrorCode("341101"),
    errorName = "EMAIL_RECIPIENT_REQUIRED",
    legacyCode = "ERR-02",
    title = "Email recipient required",
    safeDetail = "A valid recipient email address is required.",
    messageKey = "error.email.recipient_required",
    httpStatus = 400,
    graphqlClassification = null,
    retryPolicy = RetryPolicy.NEVER,
    severity = ErrorSeverity.WARN,
    disclosure = DisclosurePolicy.PUBLIC
)

    /** 341102: Invalid email template input */
    val EMAIL_TEMPLATE_INPUT_INVALID: ErrorDefinition = SimpleErrorDefinition(
    numericCode = ErrorCode("341102"),
    errorName = "EMAIL_TEMPLATE_INPUT_INVALID",
    legacyCode = "ERR-02",
    title = "Invalid email template input",
    safeDetail = "Required template parameters for email rendering are missing or invalid.",
    messageKey = "error.email.template_invalid",
    httpStatus = 400,
    graphqlClassification = null,
    retryPolicy = RetryPolicy.NEVER,
    severity = ErrorSeverity.WARN,
    disclosure = DisclosurePolicy.PUBLIC
)

    /** 345701: Email dispatch failed */
    val EMAIL_DISPATCH_FAILED: ErrorDefinition = SimpleErrorDefinition(
    numericCode = ErrorCode("345701"),
    errorName = "EMAIL_DISPATCH_FAILED",
    legacyCode = "ERR-07",
    title = "Email dispatch failed",
    safeDetail = "SMTP server rejected the email dispatch or connection timed out.",
    messageKey = "error.email.dispatch_failed",
    httpStatus = null,
    graphqlClassification = null,
    retryPolicy = RetryPolicy.RETRY_AFTER,
    severity = ErrorSeverity.ERROR,
    disclosure = DisclosurePolicy.PUBLIC
)

    /** All definitions in authoritative catalog order. */
    val all: List<ErrorDefinition> = listOf(
        INBOX_CURSOR_INVALID,
        INBOX_LIMIT_OUT_OF_RANGE,
        INBOX_NOTIFICATION_NOT_FOUND,
        INBOX_ACCESS_HIDDEN,
        NOTIFICATION_DISPATCH_FAILED,
        NOTIFICATION_PAYLOAD_CORRUPT,
        NOTIFICATION_PREFERENCE_INVALID,
        EMAIL_RECIPIENT_REQUIRED,
        EMAIL_TEMPLATE_INPUT_INVALID,
        EMAIL_DISPATCH_FAILED,
    )
}
