package com.subhrodip.squarewise.errors.code

/** Client or worker remediation policy for a governed error. */
enum class RetryPolicy {
    /** Retrying cannot change the outcome. */
    NEVER,

    /** Retry after the server-provided delay. */
    RETRY_AFTER,

    /** Acquire valid authentication again. */
    REAUTHENTICATE,

    /** Refresh an expired access credential. */
    REFRESH_TOKEN,

    /** Re-read the authoritative state before retrying. */
    RESYNC,

    /** Retry using the same idempotency identity. */
    IDEMPOTENT_RETRY,
}
