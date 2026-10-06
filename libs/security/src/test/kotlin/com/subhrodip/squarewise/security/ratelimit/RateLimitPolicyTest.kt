package com.subhrodip.squarewise.security.ratelimit

import java.time.Duration
import org.junit.jupiter.api.Assertions.assertEquals
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
            RateLimitPolicy("auth-é", maximumPermits = 1, window = Duration.ofMinutes(1))
        }
        assertThrows(IllegalArgumentException::class.java) {
            RateLimitPolicy(
                "login",
                maximumPermits = 1,
                window = Duration.ofSeconds(2),
                cooldown = Duration.ofSeconds(3)
            )
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
        val policy = RateLimitPolicy("auth-login_123AZ", maximumPermits = 5, window = Duration.ofMinutes(1))
        assertEquals("auth-login_123AZ", policy.id)
        assertEquals(RateLimitAlgorithm.FIXED_WINDOW, policy.algorithm)
        assertEquals(5, policy.maximumPermits)
        assertEquals(Duration.ofMinutes(1), policy.window)
        assertEquals(Duration.ZERO, policy.cooldown)
        RateLimitPolicy("a".repeat(64), maximumPermits = 1_000_000, window = Duration.ofHours(24))
    }

    @Test
    fun `rejects invalid identifiers and cooldown values`() {
        listOf(
            "",
            "a".repeat(65),
            "auth login",
            "auth.é",
            "auth@test",
            "auth[test]",
            "auth/test",
            "auth:test",
            "auth`test",
            "auth{test}"
        ).forEach { identifier ->
            assertThrows(IllegalArgumentException::class.java) {
                RateLimitPolicy(identifier, maximumPermits = 1, window = Duration.ofMinutes(1))
            }
        }
        assertThrows(IllegalArgumentException::class.java) {
            RateLimitPolicy("login", maximumPermits = 1_000_001, window = Duration.ofMinutes(1))
        }
        assertThrows(IllegalArgumentException::class.java) {
            RateLimitPolicy("login", maximumPermits = 1, window = Duration.ofSeconds(10), cooldown = Duration.ofSeconds(-1))
        }
        assertThrows(IllegalArgumentException::class.java) {
            RateLimitPolicy("login", maximumPermits = 1, window = Duration.ofMillis(1_000), cooldown = Duration.ofMillis(-1))
        }
        assertThrows(IllegalArgumentException::class.java) {
            RateLimitPolicy("login", maximumPermits = 1, window = Duration.ofNanos(1_000_000_001))
        }
    }
}
