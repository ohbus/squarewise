package com.subhrodip.squarewise.accounts.auth.abuse

import java.time.Duration
import java.time.Instant
import com.subhrodip.squarewise.security.ratelimit.RateLimitPolicy
import com.subhrodip.squarewise.security.ratelimit.RateLimiter
import org.springframework.transaction.annotation.Transactional

/** Application service for atomic passwordless login request throttling. */
open class LoginRateLimitService(
    private val keyDeriver: LoginRateLimitKeyDeriver,
    private val rateLimiter: RateLimiter,
    private val window: Duration = Duration.ofMinutes(15),
    private val maximumRequests: Int = 5,
    private val resendCooldown: Duration = Duration.ofSeconds(60)
) {
    init {
        RateLimitPolicy("auth-login", maximumPermits = maximumRequests, window = window, cooldown = resendCooldown)
    }

    /**
     * Atomically acquires one request slot for a canonical email/network pair.
     *
     * @return true when allowed; false is a generic denial.
     */
    @Transactional
    open fun tryAcquire(email: String, networkPartition: String, now: Instant): Boolean {
        val key = keyDeriver.derive(email, networkPartition)
        return rateLimiter.consume(
            key = key.toHex(),
            policy = RateLimitPolicy("auth-login", maximumPermits = maximumRequests, window = window, cooldown = resendCooldown)
        ).allowed
    }

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }
}
