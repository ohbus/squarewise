package com.subhrodip.squarewise.security.ratelimit

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test

/** Verifies rate-limit store failures preserve their root cause and stable message. */
class RateLimitStoreUnavailableExceptionTest {
    @Test
    fun `retains cause and stable public message`() {
        val cause = IllegalStateException("redis unavailable")
        val exception = RateLimitStoreUnavailableException(cause)

        assertEquals("Rate-limit store unavailable", exception.message)
        assertSame(cause, exception.cause)
    }
}
