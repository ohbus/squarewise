package com.subhrodip.squarewise.security.ratelimit

/** Algorithms supported by the shared distributed admission port. */
enum class RateLimitAlgorithm {
    /** A bounded counter whose state expires at the end of its configured window. */
    FIXED_WINDOW
}
