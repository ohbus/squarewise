package com.subhrodip.squarewise.security.ratelimit

import java.time.Duration
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

/** Verifies bounded shared rate-limit policy configuration. */
class RateLimitPolicyTest {
    @Test
    fun `rejects disabled and oversized policies`() {
        assertThrows(IllegalArgumentException::class.java) {
            RateLimitPolicy("login", maximumPermits = 0, window = Duration.ofMinutes(1))
        }
        assertThrows(IllegalArgumentException::class.java) {
            RateLimitPolicy("login", maximumPermits = 1, window = Duration.ZERO)
        }
        assertThrows(IllegalArgumentException::class.java) {
            RateLimitPolicy("login", maximumPermits = 1, window = Duration.ofDays(2))
        }
        assertThrows(IllegalArgumentException::class.java) {
            RateLimitPolicy("login", maximumPermits = 1, window = Duration.ofMinutes(1), cooldown = Duration.ofMinutes(2))
        }
        assertThrows(IllegalArgumentException::class.java) {
            RateLimitPolicy("login", maximumPermits = 1, window = Duration.ofMillis(1_500))
        }
        assertThrows(IllegalArgumentException::class.java) {
            RateLimitPolicy(
                "login",
                maximumPermits = 1,
                window = Duration.ofSeconds(2),
                cooldown = Duration.ofMillis(1_500)
            )
        }
    }

    @Test
    fun `accepts bounded policy identifiers and values`() {
        RateLimitPolicy("auth-login", maximumPermits = 5, window = Duration.ofMinutes(1))
    }
}
