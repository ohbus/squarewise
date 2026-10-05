package com.subhrodip.squarewise.accounts.auth.abuse

import com.subhrodip.squarewise.security.ratelimit.RateLimitDecision
import com.subhrodip.squarewise.security.ratelimit.RateLimitPolicy
import com.subhrodip.squarewise.security.ratelimit.RateLimiter
import java.time.Instant

/** Compatibility adapter retained only for direct unit-test constructors during migration. */
internal class LegacyRateLimitBucketAdapter(
    private val store: RateLimitBucketStore
) : RateLimiter {
    override fun consume(key: String, policy: RateLimitPolicy): RateLimitDecision {
        val now = Instant.now()
        val allowed = store.acquireAtomically(
            key = key.hexToBytes(),
            now = now,
            windowStart = now.minus(policy.window),
            cooldownCutoff = now.minus(policy.cooldown),
            maximumRequests = policy.maximumPermits
        ) == 1
        return RateLimitDecision(
            allowed = allowed,
            remaining = if (allowed) policy.maximumPermits - 1 else 0,
            retryAfter = if (allowed) java.time.Duration.ZERO else policy.window,
            policyId = policy.id
        )
    }

    private fun String.hexToBytes(): ByteArray = chunked(2).map { it.toInt(16).toByte() }.toByteArray()
}
