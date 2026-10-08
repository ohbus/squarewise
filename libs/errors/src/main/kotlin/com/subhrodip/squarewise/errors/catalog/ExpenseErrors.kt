package com.subhrodip.squarewise.errors.catalog

import com.subhrodip.squarewise.errors.code.DisclosurePolicy
import com.subhrodip.squarewise.errors.code.ErrorCode
import com.subhrodip.squarewise.errors.code.ErrorDefinition
import com.subhrodip.squarewise.errors.code.ErrorSeverity
import com.subhrodip.squarewise.errors.code.RetryPolicy

/** Governed, statically compiled error definitions for the Expense Core domain. */
object ExpenseErrors {
    /** 211101: Invalid group request */
    val GROUP_REQUEST_INVALID: ErrorDefinition = SimpleErrorDefinition(
    numericCode = ErrorCode("211101"),
    errorName = "GROUP_REQUEST_INVALID",
    title = "Invalid group request",
    safeDetail = "Group parameters are invalid or missing required fields.",
    messageKey = "error.groups.request_invalid",
    httpStatus = 422,
    graphqlClassification = "BAD_REQUEST",
    retryPolicy = RetryPolicy.NEVER,
    severity = ErrorSeverity.WARN,
    disclosure = DisclosurePolicy.PUBLIC
)

    /** 213201: Group not found */
    val GROUP_NOT_FOUND: ErrorDefinition = SimpleErrorDefinition(
    numericCode = ErrorCode("213201"),
    errorName = "GROUP_NOT_FOUND",
    title = "Group not found",
    safeDetail = "The requested group does not exist.",
    messageKey = "error.groups.not_found",
    httpStatus = 404,
    graphqlClassification = "NOT_FOUND",
    retryPolicy = RetryPolicy.NEVER,
    severity = ErrorSeverity.WARN,
    disclosure = DisclosurePolicy.RESOURCE_HIDDEN_WHEN_UNAUTHORIZED
)

    /** 213301: Group name conflict */
    val GROUP_NAME_CONFLICT: ErrorDefinition = SimpleErrorDefinition(
    numericCode = ErrorCode("213301"),
    errorName = "GROUP_NAME_CONFLICT",
    title = "Group name conflict",
    safeDetail = "A group with this name already exists in your account.",
    messageKey = "error.groups.name_conflict",
    httpStatus = 409,
    graphqlClassification = "BAD_REQUEST",
    retryPolicy = RetryPolicy.NEVER,
    severity = ErrorSeverity.WARN,
    disclosure = DisclosurePolicy.PUBLIC
)

    /** 213401: Group is archived */
    val GROUP_ARCHIVED: ErrorDefinition = SimpleErrorDefinition(
    numericCode = ErrorCode("213401"),
    errorName = "GROUP_ARCHIVED",
    title = "Group is archived",
    safeDetail = "Operations cannot be performed on an archived group.",
    messageKey = "error.groups.archived",
    httpStatus = 409,
    graphqlClassification = "BAD_REQUEST",
    retryPolicy = RetryPolicy.NEVER,
    severity = ErrorSeverity.WARN,
    disclosure = DisclosurePolicy.PUBLIC
)

    /** 217201: Group not found */
    val GROUP_ACCESS_HIDDEN: ErrorDefinition = SimpleErrorDefinition(
    numericCode = ErrorCode("217201"),
    errorName = "GROUP_ACCESS_HIDDEN",
    title = "Group not found",
    safeDetail = "The requested group does not exist.",
    messageKey = "error.groups.not_found",
    httpStatus = 404,
    graphqlClassification = "NOT_FOUND",
    retryPolicy = RetryPolicy.NEVER,
    severity = ErrorSeverity.WARN,
    disclosure = DisclosurePolicy.RESOURCE_HIDDEN_WHEN_UNAUTHORIZED
)

    /** 221201: Invitation not found */
    val INVITATION_NOT_FOUND: ErrorDefinition = SimpleErrorDefinition(
    numericCode = ErrorCode("221201"),
    errorName = "INVITATION_NOT_FOUND",
    title = "Invitation not found",
    safeDetail = "The invitation token is invalid or does not exist.",
    messageKey = "error.membership.invitation_not_found",
    httpStatus = 404,
    graphqlClassification = "NOT_FOUND",
    retryPolicy = RetryPolicy.NEVER,
    severity = ErrorSeverity.WARN,
    disclosure = DisclosurePolicy.RESOURCE_HIDDEN_WHEN_UNAUTHORIZED
)

    /** 223201: Group member not found */
    val MEMBER_NOT_FOUND: ErrorDefinition = SimpleErrorDefinition(
    numericCode = ErrorCode("223201"),
    errorName = "MEMBER_NOT_FOUND",
    title = "Group member not found",
    safeDetail = "The participant is not a member of this group.",
    messageKey = "error.membership.member_not_found",
    httpStatus = 404,
    graphqlClassification = "NOT_FOUND",
    retryPolicy = RetryPolicy.NEVER,
    severity = ErrorSeverity.WARN,
    disclosure = DisclosurePolicy.RESOURCE_HIDDEN_WHEN_UNAUTHORIZED
)

    /** 223202: Placeholder member not found */
    val PLACEHOLDER_NOT_FOUND: ErrorDefinition = SimpleErrorDefinition(
    numericCode = ErrorCode("223202"),
    errorName = "PLACEHOLDER_NOT_FOUND",
    title = "Placeholder member not found",
    safeDetail = "The placeholder participant was not found.",
    messageKey = "error.membership.placeholder_not_found",
    httpStatus = 404,
    graphqlClassification = "NOT_FOUND",
    retryPolicy = RetryPolicy.NEVER,
    severity = ErrorSeverity.WARN,
    disclosure = DisclosurePolicy.RESOURCE_HIDDEN_WHEN_UNAUTHORIZED
)

    /** 223401: Invitation expired */
    val INVITATION_EXPIRED: ErrorDefinition = SimpleErrorDefinition(
    numericCode = ErrorCode("223401"),
    errorName = "INVITATION_EXPIRED",
    title = "Invitation expired",
    safeDetail = "This group invitation has expired.",
    messageKey = "error.membership.invitation_expired",
    httpStatus = 410,
    graphqlClassification = "BAD_REQUEST",
    retryPolicy = RetryPolicy.NEVER,
    severity = ErrorSeverity.WARN,
    disclosure = DisclosurePolicy.PUBLIC
)

    /** 223402: Invitation already claimed */
    val INVITATION_ALREADY_CLAIMED: ErrorDefinition = SimpleErrorDefinition(
    numericCode = ErrorCode("223402"),
    errorName = "INVITATION_ALREADY_CLAIMED",
    title = "Invitation already claimed",
    safeDetail = "This invitation has already been accepted.",
    messageKey = "error.membership.invitation_claimed",
    httpStatus = 409,
    graphqlClassification = "BAD_REQUEST",
    retryPolicy = RetryPolicy.NEVER,
    severity = ErrorSeverity.WARN,
    disclosure = DisclosurePolicy.PUBLIC
)

    /** 223403: Invitation revoked */
    val INVITATION_REVOKED: ErrorDefinition = SimpleErrorDefinition(
    numericCode = ErrorCode("223403"),
    errorName = "INVITATION_REVOKED",
    title = "Invitation revoked",
    safeDetail = "This invitation has been revoked by a group administrator.",
    messageKey = "error.membership.invitation_revoked",
    httpStatus = 409,
    graphqlClassification = "BAD_REQUEST",
    retryPolicy = RetryPolicy.NEVER,
    severity = ErrorSeverity.WARN,
    disclosure = DisclosurePolicy.PUBLIC
)

    /** 223404: Member already removed */
    val MEMBER_ALREADY_REMOVED: ErrorDefinition = SimpleErrorDefinition(
    numericCode = ErrorCode("223404"),
    errorName = "MEMBER_ALREADY_REMOVED",
    title = "Member already removed",
    safeDetail = "The participant is no longer an active member of this group.",
    messageKey = "error.membership.already_removed",
    httpStatus = 409,
    graphqlClassification = "BAD_REQUEST",
    retryPolicy = RetryPolicy.NEVER,
    severity = ErrorSeverity.WARN,
    disclosure = DisclosurePolicy.PUBLIC
)

    /** 223405: Placeholder already bound */
    val PLACEHOLDER_ALREADY_BOUND: ErrorDefinition = SimpleErrorDefinition(
    numericCode = ErrorCode("223405"),
    errorName = "PLACEHOLDER_ALREADY_BOUND",
    title = "Placeholder already bound",
    safeDetail = "The placeholder participant is already bound to a user account.",
    messageKey = "error.membership.placeholder_bound",
    httpStatus = 409,
    graphqlClassification = "BAD_REQUEST",
    retryPolicy = RetryPolicy.NEVER,
    severity = ErrorSeverity.WARN,
    disclosure = DisclosurePolicy.PUBLIC
)

    /** 231101: Invalid expense request */
    val EXPENSE_REQUEST_INVALID: ErrorDefinition = SimpleErrorDefinition(
    numericCode = ErrorCode("231101"),
    errorName = "EXPENSE_REQUEST_INVALID",
    title = "Invalid expense request",
    safeDetail = "Expense parameters, currency, amount, or dates are invalid.",
    messageKey = "error.expenses.request_invalid",
    httpStatus = 422,
    graphqlClassification = "BAD_REQUEST",
    retryPolicy = RetryPolicy.NEVER,
    severity = ErrorSeverity.WARN,
    disclosure = DisclosurePolicy.PUBLIC
)

    /** 233201: Expense not found */
    val EXPENSE_NOT_FOUND: ErrorDefinition = SimpleErrorDefinition(
    numericCode = ErrorCode("233201"),
    errorName = "EXPENSE_NOT_FOUND",
    title = "Expense not found",
    safeDetail = "The requested expense does not exist.",
    messageKey = "error.expenses.not_found",
    httpStatus = 404,
    graphqlClassification = "NOT_FOUND",
    retryPolicy = RetryPolicy.NEVER,
    severity = ErrorSeverity.WARN,
    disclosure = DisclosurePolicy.RESOURCE_HIDDEN_WHEN_UNAUTHORIZED
)

    /** 233301: Expense version conflict */
    val EXPENSE_VERSION_CONFLICT: ErrorDefinition = SimpleErrorDefinition(
    numericCode = ErrorCode("233301"),
    errorName = "EXPENSE_VERSION_CONFLICT",
    title = "Expense version conflict",
    safeDetail = "The expense was modified concurrently. Please refresh and retry.",
    messageKey = "error.expenses.version_conflict",
    httpStatus = 409,
    graphqlClassification = "BAD_REQUEST",
    retryPolicy = RetryPolicy.REFRESH_TOKEN,
    severity = ErrorSeverity.WARN,
    disclosure = DisclosurePolicy.PUBLIC
)

    /** 233302: Idempotency key conflict */
    val EXPENSE_IDEMPOTENCY_CONFLICT: ErrorDefinition = SimpleErrorDefinition(
    numericCode = ErrorCode("233302"),
    errorName = "EXPENSE_IDEMPOTENCY_CONFLICT",
    title = "Idempotency key conflict",
    safeDetail = "An identical idempotency key was previously used with different parameters.",
    messageKey = "error.expenses.idempotency_conflict",
    httpStatus = 409,
    graphqlClassification = "BAD_REQUEST",
    retryPolicy = RetryPolicy.IDEMPOTENT_RETRY,
    severity = ErrorSeverity.WARN,
    disclosure = DisclosurePolicy.PUBLIC
)

    /** 233501: Allocation sum mismatch */
    val ALLOCATION_SUM_MISMATCH: ErrorDefinition = SimpleErrorDefinition(
    numericCode = ErrorCode("233501"),
    errorName = "ALLOCATION_SUM_MISMATCH",
    title = "Allocation sum mismatch",
    safeDetail = "The sum of participant allocations does not match the total expense amount.",
    messageKey = "error.expenses.allocation_sum_mismatch",
    httpStatus = 422,
    graphqlClassification = "BAD_REQUEST",
    retryPolicy = RetryPolicy.NEVER,
    severity = ErrorSeverity.WARN,
    disclosure = DisclosurePolicy.PUBLIC
)

    /** 233502: Invalid participant set */
    val PARTICIPANT_SET_INVALID: ErrorDefinition = SimpleErrorDefinition(
    numericCode = ErrorCode("233502"),
    errorName = "PARTICIPANT_SET_INVALID",
    title = "Invalid participant set",
    safeDetail = "Payer or split participants are not valid active members of this group.",
    messageKey = "error.expenses.participant_set_invalid",
    httpStatus = 422,
    graphqlClassification = "BAD_REQUEST",
    retryPolicy = RetryPolicy.NEVER,
    severity = ErrorSeverity.WARN,
    disclosure = DisclosurePolicy.PUBLIC
)

    /** 233503: Duplicate expense rejected */
    val DUPLICATE_EXPENSE_REJECTED: ErrorDefinition = SimpleErrorDefinition(
    numericCode = ErrorCode("233503"),
    errorName = "DUPLICATE_EXPENSE_REJECTED",
    title = "Duplicate expense rejected",
    safeDetail = "A duplicate expense with identical amount, payer, and timestamp was rejected.",
    messageKey = "error.expenses.duplicate_rejected",
    httpStatus = 409,
    graphqlClassification = "BAD_REQUEST",
    retryPolicy = RetryPolicy.NEVER,
    severity = ErrorSeverity.WARN,
    disclosure = DisclosurePolicy.PUBLIC
)

    /** 243201: Settlement not found */
    val SETTLEMENT_NOT_FOUND: ErrorDefinition = SimpleErrorDefinition(
    numericCode = ErrorCode("243201"),
    errorName = "SETTLEMENT_NOT_FOUND",
    title = "Settlement not found",
    safeDetail = "The requested settlement does not exist.",
    messageKey = "error.settlements.not_found",
    httpStatus = 404,
    graphqlClassification = "NOT_FOUND",
    retryPolicy = RetryPolicy.NEVER,
    severity = ErrorSeverity.WARN,
    disclosure = DisclosurePolicy.RESOURCE_HIDDEN_WHEN_UNAUTHORIZED
)

    /** 243301: Settlement version conflict */
    val SETTLEMENT_VERSION_CONFLICT: ErrorDefinition = SimpleErrorDefinition(
    numericCode = ErrorCode("243301"),
    errorName = "SETTLEMENT_VERSION_CONFLICT",
    title = "Settlement version conflict",
    safeDetail = "The settlement was modified concurrently. Please refresh and retry.",
    messageKey = "error.settlements.version_conflict",
    httpStatus = 409,
    graphqlClassification = "BAD_REQUEST",
    retryPolicy = RetryPolicy.REFRESH_TOKEN,
    severity = ErrorSeverity.WARN,
    disclosure = DisclosurePolicy.PUBLIC
)

    /** 243401: Settlement already reversed */
    val SETTLEMENT_ALREADY_REVERSED: ErrorDefinition = SimpleErrorDefinition(
    numericCode = ErrorCode("243401"),
    errorName = "SETTLEMENT_ALREADY_REVERSED",
    title = "Settlement already reversed",
    safeDetail = "This settlement has already been reversed.",
    messageKey = "error.settlements.already_reversed",
    httpStatus = 409,
    graphqlClassification = "BAD_REQUEST",
    retryPolicy = RetryPolicy.NEVER,
    severity = ErrorSeverity.WARN,
    disclosure = DisclosurePolicy.PUBLIC
)

    /** 243501: Insufficient balance for settlement */
    val INSUFFICIENT_BALANCE: ErrorDefinition = SimpleErrorDefinition(
    numericCode = ErrorCode("243501"),
    errorName = "INSUFFICIENT_BALANCE",
    title = "Insufficient balance for settlement",
    safeDetail = "Settlement amount exceeds the outstanding balance.",
    messageKey = "error.settlements.insufficient_balance",
    httpStatus = 422,
    graphqlClassification = "BAD_REQUEST",
    retryPolicy = RetryPolicy.NEVER,
    severity = ErrorSeverity.WARN,
    disclosure = DisclosurePolicy.PUBLIC
)

    /** 253201: Recurring schedule not found */
    val SCHEDULE_NOT_FOUND: ErrorDefinition = SimpleErrorDefinition(
    numericCode = ErrorCode("253201"),
    errorName = "SCHEDULE_NOT_FOUND",
    title = "Recurring schedule not found",
    safeDetail = "The requested recurring schedule does not exist.",
    messageKey = "error.recurrence.schedule_not_found",
    httpStatus = 404,
    graphqlClassification = "NOT_FOUND",
    retryPolicy = RetryPolicy.NEVER,
    severity = ErrorSeverity.WARN,
    disclosure = DisclosurePolicy.RESOURCE_HIDDEN_WHEN_UNAUTHORIZED
)

    /** 253301: Schedule version conflict */
    val SCHEDULE_VERSION_CONFLICT: ErrorDefinition = SimpleErrorDefinition(
    numericCode = ErrorCode("253301"),
    errorName = "SCHEDULE_VERSION_CONFLICT",
    title = "Schedule version conflict",
    safeDetail = "The recurring schedule was modified concurrently.",
    messageKey = "error.recurrence.version_conflict",
    httpStatus = 409,
    graphqlClassification = "BAD_REQUEST",
    retryPolicy = RetryPolicy.REFRESH_TOKEN,
    severity = ErrorSeverity.WARN,
    disclosure = DisclosurePolicy.PUBLIC
)

    /** 253401: Schedule is paused */
    val SCHEDULE_PAUSED: ErrorDefinition = SimpleErrorDefinition(
    numericCode = ErrorCode("253401"),
    errorName = "SCHEDULE_PAUSED",
    title = "Schedule is paused",
    safeDetail = "The recurring schedule is paused and cannot trigger occurrences.",
    messageKey = "error.recurrence.paused",
    httpStatus = 409,
    graphqlClassification = "BAD_REQUEST",
    retryPolicy = RetryPolicy.NEVER,
    severity = ErrorSeverity.WARN,
    disclosure = DisclosurePolicy.PUBLIC
)

    /** 255601: Occurrence generation failed */
    val OCCURRENCE_GENERATION_FAILED: ErrorDefinition = SimpleErrorDefinition(
    numericCode = ErrorCode("255601"),
    errorName = "OCCURRENCE_GENERATION_FAILED",
    title = "Occurrence generation failed",
    safeDetail = "Failed to create scheduled expense occurrence due to internal state.",
    messageKey = "error.recurrence.occurrence_failed",
    httpStatus = null,
    graphqlClassification = null,
    retryPolicy = RetryPolicy.NEVER,
    severity = ErrorSeverity.CRITICAL,
    disclosure = DisclosurePolicy.PUBLIC
)

    /** 261101: Invalid sync cursor */
    val SYNC_CURSOR_INVALID: ErrorDefinition = SimpleErrorDefinition(
    numericCode = ErrorCode("261101"),
    errorName = "SYNC_CURSOR_INVALID",
    title = "Invalid sync cursor",
    safeDetail = "The provided sync cursor is malformed or invalid.",
    messageKey = "error.sync.cursor_invalid",
    httpStatus = 422,
    graphqlClassification = "BAD_REQUEST",
    retryPolicy = RetryPolicy.NEVER,
    severity = ErrorSeverity.WARN,
    disclosure = DisclosurePolicy.PUBLIC
)

    /** 261102: Sync cursor expired */
    val SYNC_CURSOR_EXPIRED: ErrorDefinition = SimpleErrorDefinition(
    numericCode = ErrorCode("261102"),
    errorName = "SYNC_CURSOR_EXPIRED",
    title = "Sync cursor expired",
    safeDetail = "The sync cursor has expired. Full sync is required.",
    messageKey = "error.sync.cursor_expired",
    httpStatus = 410,
    graphqlClassification = "BAD_REQUEST",
    retryPolicy = RetryPolicy.RESYNC,
    severity = ErrorSeverity.WARN,
    disclosure = DisclosurePolicy.PUBLIC
)

    /** 263301: Sync revision conflict */
    val SYNC_REVISION_CONFLICT: ErrorDefinition = SimpleErrorDefinition(
    numericCode = ErrorCode("263301"),
    errorName = "SYNC_REVISION_CONFLICT",
    title = "Sync revision conflict",
    safeDetail = "The offline change conflicts with current server revision.",
    messageKey = "error.sync.revision_conflict",
    httpStatus = 409,
    graphqlClassification = "BAD_REQUEST",
    retryPolicy = RetryPolicy.RESYNC,
    severity = ErrorSeverity.ERROR,
    disclosure = DisclosurePolicy.PUBLIC
)

    /** 271101: Invalid search query */
    val SEARCH_QUERY_INVALID: ErrorDefinition = SimpleErrorDefinition(
    numericCode = ErrorCode("271101"),
    errorName = "SEARCH_QUERY_INVALID",
    title = "Invalid search query",
    safeDetail = "Search filters, sort parameters, or date ranges are invalid.",
    messageKey = "error.search.query_invalid",
    httpStatus = 422,
    graphqlClassification = "BAD_REQUEST",
    retryPolicy = RetryPolicy.NEVER,
    severity = ErrorSeverity.WARN,
    disclosure = DisclosurePolicy.PUBLIC
)

    /** 271102: Invalid export request */
    val EXPORT_REQUEST_INVALID: ErrorDefinition = SimpleErrorDefinition(
    numericCode = ErrorCode("271102"),
    errorName = "EXPORT_REQUEST_INVALID",
    title = "Invalid export request",
    safeDetail = "Export row bounds or export format parameters are invalid.",
    messageKey = "error.search.export_invalid",
    httpStatus = 422,
    graphqlClassification = null,
    retryPolicy = RetryPolicy.NEVER,
    severity = ErrorSeverity.WARN,
    disclosure = DisclosurePolicy.PUBLIC
)

    /** 285701: Outbox relay publish failed */
    val OUTBOX_RELAY_PUBLISH_FAILED: ErrorDefinition = SimpleErrorDefinition(
    numericCode = ErrorCode("285701"),
    errorName = "OUTBOX_RELAY_PUBLISH_FAILED",
    title = "Outbox relay publish failed",
    safeDetail = "Failed to publish message from transactional outbox to broker.",
    messageKey = "error.outbox.publish_failed",
    httpStatus = null,
    graphqlClassification = null,
    retryPolicy = RetryPolicy.RETRY_AFTER,
    severity = ErrorSeverity.CRITICAL,
    disclosure = DisclosurePolicy.PUBLIC
)

    /** 285601: Corrupt outbox record */
    val OUTBOX_RECORD_CORRUPT: ErrorDefinition = SimpleErrorDefinition(
    numericCode = ErrorCode("285601"),
    errorName = "OUTBOX_RECORD_CORRUPT",
    title = "Corrupt outbox record",
    safeDetail = "Outbox payload could not be deserialized or validated.",
    messageKey = "error.outbox.record_corrupt",
    httpStatus = null,
    graphqlClassification = null,
    retryPolicy = RetryPolicy.NEVER,
    severity = ErrorSeverity.CRITICAL,
    disclosure = DisclosurePolicy.PUBLIC
)

    /** All definitions in authoritative catalog order. */
    val all: List<ErrorDefinition> = listOf(
        GROUP_REQUEST_INVALID,
        GROUP_NOT_FOUND,
        GROUP_NAME_CONFLICT,
        GROUP_ARCHIVED,
        GROUP_ACCESS_HIDDEN,
        INVITATION_NOT_FOUND,
        MEMBER_NOT_FOUND,
        PLACEHOLDER_NOT_FOUND,
        INVITATION_EXPIRED,
        INVITATION_ALREADY_CLAIMED,
        INVITATION_REVOKED,
        MEMBER_ALREADY_REMOVED,
        PLACEHOLDER_ALREADY_BOUND,
        EXPENSE_REQUEST_INVALID,
        EXPENSE_NOT_FOUND,
        EXPENSE_VERSION_CONFLICT,
        EXPENSE_IDEMPOTENCY_CONFLICT,
        ALLOCATION_SUM_MISMATCH,
        PARTICIPANT_SET_INVALID,
        DUPLICATE_EXPENSE_REJECTED,
        SETTLEMENT_NOT_FOUND,
        SETTLEMENT_VERSION_CONFLICT,
        SETTLEMENT_ALREADY_REVERSED,
        INSUFFICIENT_BALANCE,
        SCHEDULE_NOT_FOUND,
        SCHEDULE_VERSION_CONFLICT,
        SCHEDULE_PAUSED,
        OCCURRENCE_GENERATION_FAILED,
        SYNC_CURSOR_INVALID,
        SYNC_CURSOR_EXPIRED,
        SYNC_REVISION_CONFLICT,
        SEARCH_QUERY_INVALID,
        EXPORT_REQUEST_INVALID,
        OUTBOX_RELAY_PUBLISH_FAILED,
        OUTBOX_RECORD_CORRUPT,
    )
}
