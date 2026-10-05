package com.subhrodip.squarewise.notifications.delivery.rate

import java.time.Duration
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import com.subhrodip.squarewise.security.ratelimit.RateLimitDecision
import com.subhrodip.squarewise.security.ratelimit.RateLimitPolicy
import com.subhrodip.squarewise.security.ratelimit.RateLimiter

/** Verifies that notification delivery delegates to the shared rate-limit port. */
class RedisDeliveryRateLimiterTest {
    @Test
    fun `passes bounded delivery policy to shared limiter`() {
        var capturedKey: String? = null
        var capturedPolicy: RateLimitPolicy? = null
        val limiter = object : RateLimiter {
            override fun consume(key: String, policy: RateLimitPolicy): RateLimitDecision {
                capturedKey = key
                capturedPolicy = policy
                return RateLimitDecision(true, 2, Duration.ZERO, "notification-delivery")
            }
        }

        val result = RedisDeliveryRateLimiter(limiter, limit = 3, window = Duration.ofSeconds(45))
            .allow("alice@example.com")

        assertEquals(true, result)
        assertEquals("alice@example.com", capturedKey)
        assertEquals("notification-delivery", capturedPolicy?.id)
        assertEquals(3, capturedPolicy?.maximumPermits)
        assertEquals(Duration.ofSeconds(45), capturedPolicy?.window)
    }

    @Test
    fun `maps atomic deny decision to false`() {
        val limiter = object : RateLimiter {
            override fun consume(key: String, policy: RateLimitPolicy): RateLimitDecision =
                RateLimitDecision(false, 0, Duration.ofMinutes(1), "notification-delivery")
        }

        assertEquals(false, RedisDeliveryRateLimiter(limiter, limit = 1).allow("alice@example.com"))
    }
}
