package com.subhrodip.squarewise.accounts.auth.abuse

import com.subhrodip.squarewise.accounts.auth.credential.CredentialDigest
import com.subhrodip.squarewise.security.ratelimit.RateLimitPolicy
import com.subhrodip.squarewise.security.ratelimit.RateLimitPolicyIds
import com.subhrodip.squarewise.security.ratelimit.RateLimiter
import java.time.Duration
import java.time.Instant
import org.springframework.transaction.annotation.Transactional

/** Applies a fail-closed, network-partition limit to refresh-token rotation. */
open class RefreshRateLimitService(
    private val digest: CredentialDigest,
    private val rateLimiter: RateLimiter,
    private val window: Duration = Duration.ofMinutes(1),
    private val maximumRequests: Int = 10
) {
    init {
        require(!window.isZero && !window.isNegative) { "Refresh rate-limit window must be positive" }
        require(maximumRequests > 0) { "Refresh maximum requests must be positive" }
    }

    /**
     * Atomically consumes one refresh admission slot for the server-derived network partition.
     *
     * @param networkPartition coarse server-derived client partition
     * @param now current timestamp
     * @return true when rotation may proceed, false when the limit is reached
     * @throws RateLimitStoreUnavailableException when the store cannot decide safely
     */
    @Transactional
    open fun tryAcquire(networkPartition: String, now: Instant): Boolean {
        val partition = networkPartition.trim()
        require(partition.isNotEmpty() && partition.length <= MAX_PARTITION_LENGTH) {
            "Refresh network partition is invalid"
        }
        require(partition.none { it.isWhitespace() || it.isISOControl() }) {
            "Refresh network partition contains invalid characters"
        }
        val key = digest.digest("v1|refresh|$partition")
        return rateLimiter.consume(
            key = key.toHex(),
            policy = RateLimitPolicy(RateLimitPolicyIds.AUTH_REFRESH, maximumPermits = maximumRequests, window = window)
        ).allowed
    }

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }

    private companion object {
        const val MAX_PARTITION_LENGTH = 128
    }
}
