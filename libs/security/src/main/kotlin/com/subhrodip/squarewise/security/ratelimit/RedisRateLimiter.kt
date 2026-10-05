package com.subhrodip.squarewise.security.ratelimit

import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.time.Duration
import io.micrometer.core.instrument.MeterRegistry
import org.springframework.context.annotation.Profile
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.data.redis.core.script.DefaultRedisScript
import org.springframework.stereotype.Component

/**
 * Redis implementation of [RateLimiter] using one atomic fixed-window script.
 *
 * The caller key is hashed before it becomes Redis key material. Every key is
 * namespaced, bounded by the policy TTL, and contains no raw identifier. Redis
 * failures are translated to the shared fail-closed exception.
 */
@Component
@Profile("!test")
class RedisRateLimiter(
    private val redis: StringRedisTemplate,
    private val meterRegistry: MeterRegistry? = null
) : RateLimiter {
    private val script = DefaultRedisScript<String>(SCRIPT, String::class.java)

    override fun consume(key: String, policy: RateLimitPolicy): RateLimitDecision {
        require(key.length in 1..256) { "Rate-limit key material must be 1-256 characters" }
        val redisKey = "squarewise:rl:v1:${key.sha256()}"
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
            record(policy.id, "store_error")
            throw exception
        } catch (exception: Exception) {
            record(policy.id, "store_error")
            throw RateLimitStoreUnavailableException(exception)
        }
    }

    private fun record(policyId: String, outcome: String) {
        meterRegistry?.counter("squarewise.rate_limit.decisions", "policy", policyId, "outcome", outcome)?.increment()
    }

    private fun String.sha256(): String = MessageDigest.getInstance("SHA-256")
        .digest(toByteArray(StandardCharsets.UTF_8))
        .joinToString("") { byte -> "%02x".format(byte) }

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
