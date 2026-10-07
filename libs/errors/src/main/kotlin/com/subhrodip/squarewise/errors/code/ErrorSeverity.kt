package com.subhrodip.squarewise.errors.code

/** Operational severity attached to a governed error definition. */
enum class ErrorSeverity {
    /** Informational terminal outcome. */
    INFO,

    /** Expected warning requiring client or operator attention. */
    WARN,

    /** Recoverable application error. */
    ERROR,

    /** Invariant or availability failure requiring urgent action. */
    CRITICAL,
}
