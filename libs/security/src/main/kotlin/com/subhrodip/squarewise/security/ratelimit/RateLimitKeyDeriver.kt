package com.subhrodip.squarewise.security.ratelimit

/** Derives opaque, deployment-scoped Redis key material from a canonical limiter key. */
fun interface RateLimitKeyDeriver {
    /** Returns key material that does not disclose the caller's raw identifier. */
    fun derive(key: String): String
}
