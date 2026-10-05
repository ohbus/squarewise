package com.subhrodip.squarewise.notifications.delivery.rate

import java.time.Duration
import com.subhrodip.squarewise.security.ratelimit.RateLimitPolicy
import com.subhrodip.squarewise.security.ratelimit.RateLimitPolicyIds
import com.subhrodip.squarewise.security.ratelimit.RateLimiter
import com.subhrodip.squarewise.security.ratelimit.RedisRateLimiter
import org.springframework.data.redis.core.StringRedisTemplate

/** Contract for distributed notification delivery admission decisions. */
interface DeliveryRateLimiter {
    /** Returns whether the subject remains within the configured delivery limit. */
    fun allow(subject: String): Boolean
}

/** Redis-backed notification delivery limiter using the shared atomic port. */
class RedisDeliveryRateLimiter(
    private val rateLimiter: RateLimiter,
    private val limit: Int = 10,
    private val window: Duration = Duration.ofMinutes(1)
) : DeliveryRateLimiter {
    /** Compatibility constructor for direct adapter tests and local wiring. */
    constructor(redis: StringRedisTemplate, limit: Int = 10, window: Duration = Duration.ofMinutes(1)) :
        this(RedisRateLimiter(redis), limit, window)

    override fun allow(subject: String): Boolean {
        return rateLimiter.consume(
            key = subject,
            policy = RateLimitPolicy(RateLimitPolicyIds.NOTIFICATION_DELIVERY, maximumPermits = limit, window = window)
        ).allowed
    }
}
