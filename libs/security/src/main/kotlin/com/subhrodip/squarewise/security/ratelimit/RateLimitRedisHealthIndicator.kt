package com.subhrodip.squarewise.security.ratelimit

import org.springframework.boot.health.contributor.AbstractHealthIndicator
import org.springframework.boot.health.contributor.Health
import org.springframework.context.annotation.Profile
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Component

/** Reports the mandatory Redis dependency used by protected admission paths. */
@Component("rateLimitRedis")
@Profile("!test")
class RateLimitRedisHealthIndicator(
    private val redis: StringRedisTemplate
) : AbstractHealthIndicator() {
    override fun doHealthCheck(builder: Health.Builder) {
        try {
            if (redis.execute { connection -> connection.ping() } == "PONG") {
                builder.up()
            } else {
                builder.down().withDetail("reason", "Redis did not return PONG")
            }
        } catch (failure: Exception) {
            builder.down(failure)
        }
    }
}
