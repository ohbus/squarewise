package com.subhrodip.squarewise.security.ratelimit

import java.net.SocketTimeoutException
import java.util.concurrent.TimeoutException
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** Verifies bounded classification of Redis timeout failures for limiter telemetry. */
class RedisRateLimiterTelemetryTest {
    @Test
    fun `classifies direct and nested timeout failures`() {
        assertTrue(TimeoutException("redis command timed out").isRateLimitTimeout())
        assertTrue(RuntimeException(SocketTimeoutException("connect timed out")).isRateLimitTimeout())
    }

    @Test
    fun `does not classify ordinary store failures as timeout`() {
        assertFalse(IllegalStateException("redis authentication failed").isRateLimitTimeout())
    }
}
