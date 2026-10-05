package com.subhrodip.squarewise.accounts.auth.abuse

import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import org.springframework.context.annotation.Profile
import org.springframework.context.annotation.Primary
import org.springframework.stereotype.Component
import com.subhrodip.squarewise.security.ratelimit.RateLimitDecision
import com.subhrodip.squarewise.security.ratelimit.RateLimitPolicy
import com.subhrodip.squarewise.security.ratelimit.RateLimiter

/** Test-profile limiter; never registered in staging or production. */
@Component
@Profile("test")
@Primary
class TestRateLimitBucketStore : RateLimitBucketStore, RateLimiter {
    private data class Bucket(var started: Instant, var count: Int, var last: Instant)

    private val buckets = ConcurrentHashMap<String, Bucket>()

    override fun acquireAtomically(
        key: ByteArray,
        now: Instant,
        windowStart: Instant,
        cooldownCutoff: Instant,
        maximumRequests: Int
    ): Int {
        val bucketKey = key.joinToString("") { "%02x".format(it) }
        var allowed = 0
        buckets.compute(bucketKey) { _, current ->
            if (current == null || current.started <= windowStart) {
                allowed = 1
                Bucket(now, 1, now)
            } else if (current.count < maximumRequests && current.last <= cooldownCutoff) {
                allowed = 1
                current.copy(count = current.count + 1, last = now)
            } else {
                current
            }
        }
        return allowed
    }

    override fun consume(key: String, policy: RateLimitPolicy): RateLimitDecision {
        val now = Instant.now()
        val allowed = acquireAtomically(
            key.hexToBytes(), now, now.minus(policy.window), now.minus(policy.cooldown), policy.maximumPermits
        ) == 1
        return RateLimitDecision(
            allowed,
            if (allowed) policy.maximumPermits - 1 else 0,
            if (allowed) java.time.Duration.ZERO else policy.window,
            policy.id
        )
    }

    private fun String.hexToBytes(): ByteArray = chunked(2).map { it.toInt(16).toByte() }.toByteArray()
}
