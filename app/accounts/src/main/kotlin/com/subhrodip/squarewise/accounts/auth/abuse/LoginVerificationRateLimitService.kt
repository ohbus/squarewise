package com.subhrodip.squarewise.accounts.auth.abuse

import com.subhrodip.squarewise.accounts.auth.credential.CredentialDigest
import com.subhrodip.squarewise.security.ratelimit.RateLimitPolicy
import com.subhrodip.squarewise.security.ratelimit.RateLimitPolicyIds
import com.subhrodip.squarewise.security.ratelimit.RateLimiter
import java.time.Duration
import java.time.Instant

/** Applies the distributed admission policy before one-time credential redemption. */
open class LoginVerificationRateLimitService(
    private val digest: CredentialDigest,
    private val rateLimiter: RateLimiter,
    private val window: Duration = Duration.ofMinutes(5),
    private val maximumRequests: Int = 5
) {
    init {
        RateLimitPolicy(
            id = RateLimitPolicyIds.AUTH_LOGIN_VERIFY,
            maximumPermits = maximumRequests,
            window = window
        )
    }

    /**
     * Atomically admits a credential/network pair without exposing the credential
     * or placing it in Redis, metrics, or logs.
     *
     * @param credential raw one-time credential; it is used only as HMAC input.
     * @param networkPartition trusted server-derived coarse partition.
     * @param now request timestamp retained for a stable service boundary.
     * @return true when redemption may proceed, false when the policy is exhausted.
     * @throws RateLimitStoreUnavailableException when a safe decision is unavailable.
     */
    open fun tryAcquire(credential: String, networkPartition: String, now: Instant): Boolean {
        require(credential.length in 1..MAX_CREDENTIAL_LENGTH) { "Credential length is invalid" }
        require(networkPartition.length in 1..MAX_PARTITION_LENGTH) { "Network partition is invalid" }
        require(networkPartition.none { it.isWhitespace() || it.isISOControl() }) {
            "Network partition contains invalid characters"
        }
        val material = "v1|login-verify|$credential|$networkPartition"
        return rateLimiter.consume(
            key = digest.digest(material).toHex(),
            policy = RateLimitPolicy(
                id = RateLimitPolicyIds.AUTH_LOGIN_VERIFY,
                maximumPermits = maximumRequests,
                window = window
            )
        ).allowed
    }

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }

    private companion object {
        const val MAX_CREDENTIAL_LENGTH: Int = 4096
        const val MAX_PARTITION_LENGTH: Int = 128
    }
}
