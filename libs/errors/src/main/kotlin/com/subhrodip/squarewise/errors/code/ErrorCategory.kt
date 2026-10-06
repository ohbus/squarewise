package com.subhrodip.squarewise.errors.code

/** Remediation category represented by the fourth error-code digit. */
enum class ErrorCategory(val digit: Int) {
    /** Malformed or invalid input. */
    VALIDATION(1),

    /** Missing resource or value. */
    MISSING(2),

    /** Duplicate or competing operation. */
    CONFLICT(3),

    /** Invalid lifecycle state. */
    STATE(4),

    /** Domain policy rejection. */
    BUSINESS_RULE(5),

    /** Data integrity or consistency failure. */
    DATA_CONSISTENCY(6),

    /** Communication or protocol failure. */
    COMMUNICATION(7),

    /** Availability or timeout failure. */
    AVAILABILITY(8),

    /** Unexpected internal failure. */
    INTERNAL(9),
}
