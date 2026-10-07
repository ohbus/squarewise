package com.subhrodip.squarewise.security.ratelimit

import java.time.Duration
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import com.subhrodip.squarewise.security.errors.PlatformDomainException

/** Verifies fail-closed bounds on rate-limit decision metadata. */
class RateLimitDecisionTest {
    @Test
    fun `rejects negative remaining permits and retry duration`() {
        assertThrows(PlatformDomainException::class.java) {
            RateLimitDecision(true, -1, Duration.ZERO, "login")
        }
        assertThrows(PlatformDomainException::class.java) {
            RateLimitDecision(false, 0, Duration.ofSeconds(-1), "login")
        }
    }
}
