package com.subhrodip.squarewise.errors.code

/** Public disclosure boundary for error details and resource existence. */
enum class DisclosurePolicy {
    /** Safe public detail may be returned. */
    PUBLIC,

    /** Hide resource existence when the caller is unauthorized. */
    RESOURCE_HIDDEN_WHEN_UNAUTHORIZED,

    /** Expose only redacted diagnostics to internal operators. */
    INTERNAL_REDACTED,
}
