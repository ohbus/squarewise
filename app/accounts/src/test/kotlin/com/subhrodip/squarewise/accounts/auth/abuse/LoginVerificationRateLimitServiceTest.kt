package com.subhrodip.squarewise.accounts.auth.abuse

import com.subhrodip.squarewise.accounts.auth.credential.HmacCredentialDigest
import com.subhrodip.squarewise.security.ratelimit.RateLimitDecision
import com.subhrodip.squarewise.security.ratelimit.RateLimitPolicy
import com.subhrodip.squarewise.security.ratelimit.RateLimiter
import java.time.Duration
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test

/** Verifies credential-verification admission uses the shared opaque limiter contract. */
class LoginVerificationRateLimitServiceTest {
    private val digest = HmacCredentialDigest(ByteArray(32) { it.toByte() })

    @Test
    fun `derives opaque key and forwards verification policy`() {
        var capturedKey = ""
        var capturedPolicy: RateLimitPolicy? = null
        val limiter = object : RateLimiter {
            override fun consume(key: String, policy: RateLimitPolicy): RateLimitDecision {
                capturedKey = key
                capturedPolicy = policy
                return RateLimitDecision(true, 4, Duration.ZERO, policy.id)
            }
        }

        val allowed = LoginVerificationRateLimitService(digest, limiter).tryAcquire(
            credential = "secret-credential",
            networkPartition = "203.0.113",
            now = Instant.EPOCH
        )

        assertTrue(allowed)
        assertEquals("auth-login-verify", capturedPolicy?.id)
        assertEquals(64, capturedKey.length)
        assertFalse(capturedKey.contains("secret-credential"))
        assertFalse(capturedKey.contains("203.0.113"))
    }

    @Test
    fun `propagates denial without redeeming a credential`() {
        val limiter = object : RateLimiter {
            override fun consume(key: String, policy: RateLimitPolicy): RateLimitDecision =
                RateLimitDecision(false, 0, policy.window, policy.id)
        }

        assertFalse(
            LoginVerificationRateLimitService(digest, limiter).tryAcquire(
                credential = "credential",
                networkPartition = "unknown",
                now = Instant.EPOCH
            )
        )
    }

    @Test
    fun `rejects whitespace and empty network partitions`() {
        val service = LoginVerificationRateLimitService(
            digest,
            object : RateLimiter {
                override fun consume(key: String, policy: RateLimitPolicy): RateLimitDecision =
                    error("not reached")
            }
        )

        assertEquals("Network partition is invalid", runCatching {
            service.tryAcquire("credential", "", Instant.EPOCH)
        }.exceptionOrNull()?.message)
        assertEquals("Network partition contains invalid characters", runCatching {
            service.tryAcquire("credential", "edge\nnode", Instant.EPOCH)
        }.exceptionOrNull()?.message)
    }

    @Test
    fun `rejects empty or oversized credential and oversized network partition`() {
        val service = LoginVerificationRateLimitService(
            digest,
            object : RateLimiter {
                override fun consume(key: String, policy: RateLimitPolicy): RateLimitDecision =
                    error("not reached")
            }
        )

        assertEquals("Credential length is invalid", runCatching {
            service.tryAcquire("", "valid-partition", Instant.EPOCH)
        }.exceptionOrNull()?.message)
        assertEquals("Credential length is invalid", runCatching {
            service.tryAcquire("a".repeat(4097), "valid-partition", Instant.EPOCH)
        }.exceptionOrNull()?.message)
        assertEquals("Network partition is invalid", runCatching {
            service.tryAcquire("valid-credential", "a".repeat(129), Instant.EPOCH)
        }.exceptionOrNull()?.message)
        assertEquals("Network partition contains invalid characters", runCatching {
            service.tryAcquire("valid-credential", "partition with space", Instant.EPOCH)
        }.exceptionOrNull()?.message)
        assertEquals("Network partition contains invalid characters", runCatching {
            service.tryAcquire("valid-credential", "partition\u0000null", Instant.EPOCH)
        }.exceptionOrNull()?.message)
    }
}
