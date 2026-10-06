package com.subhrodip.squarewise.security.ratelimit

import java.time.Duration
import java.util.concurrent.TimeoutException
import io.micrometer.core.instrument.MeterRegistry
import org.springframework.context.annotation.Profile
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.data.redis.core.script.DefaultRedisScript
import org.springframework.stereotype.Component

/**
 * Redis implementation of [RateLimiter] using one atomic fixed-window script.
 *
 * The caller key is HMAC-derived before it becomes Redis key material. Every key is
 * namespaced, bounded by the policy TTL, and contains no raw identifier. Redis
 * failures are translated to the shared fail-closed exception.
 */
@Component
@Profile("!test")
class RedisRateLimiter(
    private val redis: StringRedisTemplate,
    private val keyDeriver: RateLimitKeyDeriver,
    private val meterRegistry: MeterRegistry? = null
) : RateLimiter {
    private val script = DefaultRedisScript<String>(SCRIPT, String::class.java)

    override fun consume(key: String, policy: RateLimitPolicy): RateLimitDecision {
        require(key.length in 1..256) { "Rate-limit key material must be 1-256 characters" }
        val redisKey = "squarewise:rl:v1:${keyDeriver.derive(key)}"
        return try {
            val result = redis.execute(
                script,
                listOf(redisKey),
                policy.window.seconds.toString(),
                policy.maximumPermits.toString(),
                policy.cooldown.seconds.toString()
            ) ?: throw IllegalStateException("Redis returned no rate-limit decision")
            val fields = result.split('|')
            require(fields.size == 3) { "Redis returned malformed rate-limit decision" }
            val allowed = fields[0] == "1"
            val remaining = fields[1].toInt().coerceIn(0, policy.maximumPermits)
            val retryAfterSeconds = fields[2].toLong().coerceAtLeast(0)
            record(policy.id, if (allowed) "allowed" else "denied")
            RateLimitDecision(allowed, remaining, Duration.ofSeconds(retryAfterSeconds), policy.id)
        } catch (exception: RateLimitStoreUnavailableException) {
            recordFailure(policy.id, exception)
            throw exception
        } catch (exception: Exception) {
            recordFailure(policy.id, exception)
            throw RateLimitStoreUnavailableException(exception)
        }
    }

    private fun recordFailure(policyId: String, exception: Throwable) {
        record(policyId, "store_error")
        record(policyId, "fail_closed")
        if (exception.isRateLimitTimeout()) {
            record(policyId, "timeout")
        }
    }

    private fun record(policyId: String, outcome: String) {
        meterRegistry?.counter("squarewise.rate_limit.decisions", "policy", policyId, "outcome", outcome)?.increment()
    }

    private companion object {
        const val SCRIPT = """
            local now = tonumber(redis.call('TIME')[1])
            local count = tonumber(redis.call('HGET', KEYS[1], 'count') or '0')
            local last = tonumber(redis.call('HGET', KEYS[1], 'last') or '0')
            local maximum = tonumber(ARGV[2])
            local ttl = tonumber(ARGV[1])
            local cooldown = tonumber(ARGV[3])
            local started = tonumber(redis.call('HGET', KEYS[1], 'started') or '0')
            if started == 0 or now - started >= ttl then
              count = 0
              started = now
              last = 0
            end
            if count >= maximum then
              local remaining = 0
              local current_ttl = redis.call('TTL', KEYS[1])
              if current_ttl < 0 then current_ttl = ttl end
              return '0|' .. remaining .. '|' .. current_ttl
            end
            if cooldown > 0 and last > 0 and now - last < cooldown then
              return '0|' .. (maximum - count) .. '|' .. (cooldown - (now - last))
            end
            count = count + 1
            redis.call('HSET', KEYS[1], 'count', count, 'started', started, 'last', now)
            redis.call('EXPIRE', KEYS[1], ttl)
            local remaining = maximum - count
            local current_ttl = redis.call('TTL', KEYS[1])
            return '1|' .. remaining .. '|' .. current_ttl
        """
    }
}

/** Returns true when a Redis failure or one of its causes represents a timeout. */
internal fun Throwable.isRateLimitTimeout(): Boolean {
    val visited = mutableSetOf<Throwable>()
    var current: Throwable? = this
    while (current != null && visited.add(current)) {
        if (current is TimeoutException || current::class.simpleName?.contains("Timeout", ignoreCase = true) == true) {
            return true
        }
        current = current.cause
    }
    return false
}
