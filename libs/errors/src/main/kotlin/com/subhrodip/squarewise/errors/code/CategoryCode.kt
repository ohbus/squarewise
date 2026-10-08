package com.subhrodip.squarewise.errors.code

/**
 * Coarse-grained high-level error category and family for fast client-side switching.
 */
enum class CategoryCode {
    /** Strictly syntax or transport-level malformed messages (HTTP 400). */
    MALFORMED_REQUEST,

    /** Missing, malformed, expired, or invalid authentication credentials (HTTP 401). */
    AUTHENTICATION_ERROR,

    /** Authenticated caller is not authorized or security policy rejected request (HTTP 403). */
    AUTHORIZATION_ERROR,

    /** Target resource was not found or is hidden by anti-enumeration policy (HTTP 404). */
    NOT_FOUND,

    /** State collision, optimistic lock version conflict, or duplicate operation (HTTP 409). */
    STATE_CONFLICT,

    /** Resource, token, or sync cursor has permanently expired (HTTP 410). */
    RESOURCE_GONE,

    /** Syntactically valid request failing Bean Validation or field constraints (HTTP 422). */
    VALIDATION_ERROR,

    /** Syntactically valid request violating business logic or domain invariants (HTTP 422). */
    BUSINESS_RULE_VIOLATION,

    /** Rate limit or admission quota exceeded (HTTP 429). */
    RATE_LIMIT_EXCEEDED,

    /** Unexpected internal server or upstream dependency failure (HTTP 500 / 5xx). */
    INTERNAL_ERROR,
}
