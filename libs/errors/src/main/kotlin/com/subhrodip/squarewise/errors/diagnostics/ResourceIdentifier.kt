package com.subhrodip.squarewise.errors.diagnostics

/** Bounded opaque resource label safe for internal diagnostics. */
@JvmInline
value class ResourceIdentifier(val value: String) {
    init {
        require(value.isNotBlank() && value.length <= MAX_LENGTH) {
            "Resource identifiers must be non-blank and at most $MAX_LENGTH characters"
        }
    }

    private companion object {
        const val MAX_LENGTH: Int = 128
    }
}
