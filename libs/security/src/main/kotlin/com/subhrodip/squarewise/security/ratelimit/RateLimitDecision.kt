package com.subhrodip.squarewise.security.ratelimit

import java.time.Duration

/**
 * Safe result of one rate-limit admission decision.
 *
 * The result contains only bounded operational metadata. It never exposes the
 * key, Redis key, request payload, or implementation-specific state.
 *
 * @property allowed whether the operation may continue
 * @property remaining bounded number of permits available after this decision
 * @property retryAfter duration after which retrying may be useful when denied
 * @property policyId policy that produced this decision
 */
data class RateLimitDecision(
    val allowed: Boolean,
    val remaining: Int,
    val retryAfter: Duration,
    val policyId: String
) {
    init {
        require(remaining >= 0) { "Remaining permits cannot be negative" }
        require(!retryAfter.isNegative) { "Retry-after cannot be negative" }
    }
}
