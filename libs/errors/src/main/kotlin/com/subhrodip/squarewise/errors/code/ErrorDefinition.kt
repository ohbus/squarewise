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

    /** Existing v1 symbolic alias, when compatibility requires one. */
    val legacyCode: String?

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
