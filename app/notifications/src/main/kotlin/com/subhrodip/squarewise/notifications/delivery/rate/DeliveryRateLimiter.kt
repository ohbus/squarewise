package com.subhrodip.squarewise.notifications.delivery.rate

import java.time.Duration
import com.subhrodip.squarewise.security.ratelimit.RateLimitPolicy
import com.subhrodip.squarewise.security.ratelimit.RateLimitPolicyIds
import com.subhrodip.squarewise.security.ratelimit.RateLimiter

/** Contract for distributed notification delivery admission decisions. */
fun interface DeliveryRateLimiter {
    /** Returns whether the subject remains within the configured delivery limit. */
    fun allow(subject: String): Boolean
}

/** Redis-backed notification delivery limiter using the shared atomic port. */
class RedisDeliveryRateLimiter(
    private val rateLimiter: RateLimiter,
    private val limit: Int = 10,
    private val window: Duration = Duration.ofMinutes(1)
) : DeliveryRateLimiter {
    override fun allow(subject: String): Boolean {
        return rateLimiter.consume(
            key = subject,
            policy = RateLimitPolicy(RateLimitPolicyIds.NOTIFICATION_DELIVERY, maximumPermits = limit, window = window)
        ).allowed
    }
}
