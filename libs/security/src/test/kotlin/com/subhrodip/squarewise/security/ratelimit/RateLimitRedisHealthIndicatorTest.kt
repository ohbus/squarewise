package com.subhrodip.squarewise.security.ratelimit

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock
import org.springframework.data.redis.core.RedisCallback
import org.springframework.data.redis.core.StringRedisTemplate

/** Verifies Redis health reports preserve fail-closed dependency status. */
class RateLimitRedisHealthIndicatorTest {
    @Test
    fun `reports up when Redis responds with PONG`() {
        val redis = mock(StringRedisTemplate::class.java)
        `when`(redis.execute(any<RedisCallback<String>>())).thenReturn("PONG")

        assertEquals("UP", RateLimitRedisHealthIndicator(redis).health(true)!!.status.code)
    }

    @Test
    fun `reports down when Redis returns a non-PONG response`() {
        val redis = mock(StringRedisTemplate::class.java)
        `when`(redis.execute(any<RedisCallback<String>>())).thenReturn("NOPE")

        val health = RateLimitRedisHealthIndicator(redis).health(true)!!

        assertEquals("DOWN", health.status.code)
        assertEquals("Redis did not return PONG", health.details["reason"])
    }

    @Test
    fun `reports down when Redis execution fails`() {
        val redis = mock(StringRedisTemplate::class.java)
        `when`(redis.execute(any<RedisCallback<String>>())).thenThrow(IllegalStateException("redis unavailable"))

        assertEquals("DOWN", RateLimitRedisHealthIndicator(redis).health(true)!!.status.code)
    }
}
