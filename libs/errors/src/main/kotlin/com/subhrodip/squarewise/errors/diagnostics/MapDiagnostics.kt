package com.subhrodip.squarewise.errors.diagnostics

/**
 * Immutable diagnostics map with strict cardinality, size, and sensitive-key bounds.
 *
 * Keys containing `password`, `token`, or `secret` are rejected rather than
 * relying on a later redaction step that could be bypassed by a new sink.
 */
class MapDiagnostics private constructor(
    override val entries: Map<String, String>,
) : ErrorDiagnostics {
    public companion object {
        private const val MAX_ENTRIES: Int = 10
        private const val MAX_KEY_LENGTH: Int = 64
        private const val MAX_VALUE_LENGTH: Int = 256
        private val SENSITIVE_KEY_PARTS: Set<String> = setOf("password", "token", "secret")

        /** Create validated immutable diagnostics from the supplied entries. */
        fun of(entries: Map<String, String>): MapDiagnostics {
            require(entries.size <= MAX_ENTRIES) { "Diagnostics may contain at most $MAX_ENTRIES entries" }
            entries.forEach { (key, value) ->
                require(key.isNotBlank() && key.length <= MAX_KEY_LENGTH) {
                    "Diagnostic keys must be non-blank and at most $MAX_KEY_LENGTH characters"
                }
                require(key.lowercase().split('.', '_', '-').none(SENSITIVE_KEY_PARTS::contains)) {
                    "Sensitive diagnostic keys are not permitted"
                }
                require(value.length <= MAX_VALUE_LENGTH) {
                    "Diagnostic values must be at most $MAX_VALUE_LENGTH characters"
                }
            }
            return MapDiagnostics(entries.toMap())
        }
    }
}
