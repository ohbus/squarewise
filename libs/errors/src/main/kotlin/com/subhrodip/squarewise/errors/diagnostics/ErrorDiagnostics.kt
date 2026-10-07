package com.subhrodip.squarewise.errors.diagnostics

/** Bounded, response-safe diagnostic context for internal error handling. */
interface ErrorDiagnostics {
    /** Immutable key-value entries permitted for structured logging. */
    val entries: Map<String, String>

    public companion object {
        /** Allocation-free empty diagnostic context. */
        val EMPTY: ErrorDiagnostics = EmptyDiagnostics
    }
}
