package com.subhrodip.squarewise.security.ratelimit

import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import java.time.Duration
import java.util.Base64
import java.util.concurrent.TimeoutException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.anyList
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.data.redis.core.script.DefaultRedisScript

/** Verifies Redis limiter decision parsing, failure mapping, and bounded metrics. */
class RedisRateLimiterTest {
    private val policy = RateLimitPolicy("login", maximumPermits = 5, window = Duration.ofSeconds(60))
    private val deriver = HmacRateLimitKeyDeriver.fromBase64(Base64.getEncoder().encodeToString(ByteArray(32)))

    @Test
    fun `maps an allowed Redis decision and records metrics`() {
        val registry = SimpleMeterRegistry()
        val redis = redisReturning("1|4|60")

        val decision = RedisRateLimiter(redis, deriver, registry).consume("alice", policy)

        assertEquals(true, decision.allowed)
        assertEquals(4, decision.remaining)
        assertEquals(Duration.ofSeconds(60), decision.retryAfter)
        assertEquals(1.0, registry.counter("squarewise.rate_limit.decisions", "policy", "login", "outcome", "allowed").count())
    }

    @Test
    fun `maps a denied Redis decision and clamps malformed bounds safely`() {
        val registry = SimpleMeterRegistry()
        val redis = redisReturning("0|999|2")

        val decision = RedisRateLimiter(redis, deriver, registry).consume("alice", policy)

        assertEquals(false, decision.allowed)
        assertEquals(5, decision.remaining)
        assertEquals(Duration.ofSeconds(2), decision.retryAfter)
        assertEquals(1.0, registry.counter("squarewise.rate_limit.decisions", "policy", "login", "outcome", "denied").count())
    }

    @Test
    fun `wraps null and malformed Redis results as store failures`() {
        assertThrows(RateLimitStoreUnavailableException::class.java) {
            RedisRateLimiter(redisReturning(null), deriver).consume("alice", policy)
        }
        assertThrows(RateLimitStoreUnavailableException::class.java) {
            RedisRateLimiter(redisReturning("malformed"), deriver).consume("alice", policy)
        }
    }

    @Test
    fun `preserves store failures and records timeout telemetry`() {
        val registry = SimpleMeterRegistry()
        val redis = mock(StringRedisTemplate::class.java)
        `when`(redis.execute<String>(any<DefaultRedisScript<String>>(), anyList(), any(), any(), any()))
            .thenThrow(RuntimeException(TimeoutException("redis timed out")))

        assertThrows(RateLimitStoreUnavailableException::class.java) {
            RedisRateLimiter(redis, deriver, registry).consume("alice", policy)
        }

        assertEquals(1.0, registry.counter("squarewise.rate_limit.decisions", "policy", "login", "outcome", "store_error").count())
        assertEquals(1.0, registry.counter("squarewise.rate_limit.decisions", "policy", "login", "outcome", "fail_closed").count())
        assertEquals(1.0, registry.counter("squarewise.rate_limit.decisions", "policy", "login", "outcome", "timeout").count())
    }

    @Test
    fun `preserves an existing store-unavailable exception`() {
        val failure = RateLimitStoreUnavailableException(IllegalStateException("redis unavailable"))
        val redis = mock(StringRedisTemplate::class.java)
        `when`(redis.execute<String>(any<DefaultRedisScript<String>>(), anyList(), any(), any(), any()))
            .thenThrow(failure)

        val thrown = assertThrows(RateLimitStoreUnavailableException::class.java) {
            RedisRateLimiter(redis, deriver).consume("alice", policy)
        }

        assertEquals(failure, thrown)
    }

    @Test
    fun `detects timeout from name or cause and handles null simpleName and cycles`() {
        class CustomTimeoutError : RuntimeException()
        assertEquals(true, CustomTimeoutError().isRateLimitTimeout())

        val anonymous = object : RuntimeException() {}
        assertEquals(false, anonymous.isRateLimitTimeout())

        class CyclicException : RuntimeException() {
            var cyclicCause: Throwable? = null
            override val cause: Throwable? get() = cyclicCause
        }
        val cyclic = CyclicException()
        cyclic.cyclicCause = cyclic
        assertEquals(false, cyclic.isRateLimitTimeout())
    }

    @Test
    fun `rejects empty or overlong caller keys before Redis access`() {
        val redis = redisReturning("1|4|60")
        val limiter = RedisRateLimiter(redis, deriver)

        assertThrows(IllegalArgumentException::class.java) { limiter.consume("", policy) }
        assertThrows(IllegalArgumentException::class.java) { limiter.consume("x".repeat(257), policy) }
    }

    private fun redisReturning(result: String?): StringRedisTemplate {
        val redis = mock(StringRedisTemplate::class.java)
        `when`(redis.execute<String>(any<DefaultRedisScript<String>>(), anyList(), any(), any(), any()))
            .thenReturn(result)
        return redis
    }
}
