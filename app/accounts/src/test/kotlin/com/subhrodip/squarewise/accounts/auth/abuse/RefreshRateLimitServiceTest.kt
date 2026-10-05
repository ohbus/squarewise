package com.subhrodip.squarewise.accounts.auth.abuse

import com.subhrodip.squarewise.accounts.auth.credential.HmacCredentialDigest
import java.time.Instant
import java.time.Duration
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** Verifies fail-closed refresh admission and bounded server-derived partitions. */
class RefreshRateLimitServiceTest {
    private val digest = HmacCredentialDigest(ByteArray(32) { it.toByte() })

    @Test
    fun `denies after the configured maximum`() {
        val store = TestRateLimitBucketStore()
        val service = RefreshRateLimitService(digest, store as RateLimitBucketStore, maximumRequests = 1)
        val now = Instant.parse("2026-09-28T00:00:00Z")

        assertTrue(service.tryAcquire("10.44", now))
        assertFalse(service.tryAcquire("10.44", now.plusSeconds(1)))
        assertTrue(service.tryAcquire("10.45", now.plusSeconds(1)))
    }

    @Test
    fun `fails closed when the rate-limit store cannot decide`() {
        val unavailable = object : RateLimitBucketStore {
            override fun acquireAtomically(
                key: ByteArray,
                now: Instant,
                windowStart: Instant,
                cooldownCutoff: Instant,
                maximumRequests: Int
            ): Int = throw RateLimitStoreUnavailableException(IllegalStateException("redis down"))
        }
        val service = RefreshRateLimitService(digest, unavailable)

        assertThrows(RateLimitStoreUnavailableException::class.java) {
            service.tryAcquire("10.44", Instant.parse("2026-09-28T00:00:00Z"))
        }
    }

    @Test
    fun `rejects blank or oversized partitions before store access`() {
        val service = RefreshRateLimitService(digest, TestRateLimitBucketStore() as RateLimitBucketStore)

        assertThrows(IllegalArgumentException::class.java) {
            service.tryAcquire(" ", Instant.now())
        }
        assertThrows(IllegalArgumentException::class.java) {
            service.tryAcquire("x".repeat(129), Instant.now())
        }
        assertThrows(IllegalArgumentException::class.java) {
            service.tryAcquire("x\u0000y", Instant.now())
        }
        assertThrows(IllegalArgumentException::class.java) {
            service.tryAcquire("x y", Instant.now())
        }
    }

    @Test
    fun `rejects invalid rate-limit policy at construction`() {
        assertThrows(IllegalArgumentException::class.java) {
            RefreshRateLimitService(digest, TestRateLimitBucketStore() as RateLimitBucketStore, window = Duration.ZERO)
        }
        assertThrows(IllegalArgumentException::class.java) {
            RefreshRateLimitService(digest, TestRateLimitBucketStore() as RateLimitBucketStore, window = Duration.ofSeconds(-1))
        }
        assertThrows(IllegalArgumentException::class.java) {
            RefreshRateLimitService(digest, TestRateLimitBucketStore() as RateLimitBucketStore, maximumRequests = 0)
        }
    }
}
