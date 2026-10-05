package com.subhrodip.squarewise.security.ratelimit

import java.time.Duration

/**
 * Immutable policy supplied to one atomic rate-limit decision.
 *
 * The policy is configuration, not runtime state. Bounds prevent accidental
 * unbounded Redis values and make a disabled protection policy impossible.
 *
 * @property id stable bounded policy identifier used for metrics and diagnostics
 * @property algorithm counter algorithm implemented by the shared adapter
 * @property maximumPermits maximum admitted operations in one window
 * @property window duration after which the ephemeral counter expires
 */
data class RateLimitPolicy(
    val id: String,
    val algorithm: RateLimitAlgorithm = RateLimitAlgorithm.FIXED_WINDOW,
    val maximumPermits: Int,
    val window: Duration
) {
    init {
        require(id.length in 1..64 && id.all { it.isLetterOrDigit() || it == '-' || it == '_' }) {
            "Rate-limit policy id must be 1-64 ASCII identifier characters"
        }
        require(maximumPermits in 1..1_000_000) {
            "Rate-limit maximum permits must be between 1 and 1000000"
        }
        require(!window.isZero && !window.isNegative && window <= MAXIMUM_WINDOW) {
            "Rate-limit window must be positive and no longer than 24 hours"
        }
    }

    private companion object {
        val MAXIMUM_WINDOW: Duration = Duration.ofHours(24)
    }
}
