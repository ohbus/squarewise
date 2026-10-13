package com.subhrodip.squarewise.accounts.profile.cache

import com.subhrodip.squarewise.accounts.profile.api.ProfileResponse
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Duration
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger

/**
 * Unit tests verifying [ProfileCache] hit/miss behavior, two-way key lookup, and invalidation.
 */
class ProfileCacheTest {

    private lateinit var cache: ProfileCache

    @BeforeEach
    fun setUp() {
        cache = ProfileCache(ttl = Duration.ofSeconds(10), maxEntries = 100)
    }

    @Test
    fun `getBySubject caches profile and resolves subsequent lookups without invoking loader`() {
        val accountId = UUID.randomUUID()
        val subject = "internal:cached@example.com"
        val profile = ProfileResponse(accountId, "Cached User", "UTC", "EUR")
        val calls = AtomicInteger(0)

        val first = cache.getBySubject(subject) {
            calls.incrementAndGet()
            profile
        }
        val second = cache.getBySubject(subject) {
            calls.incrementAndGet()
            profile
        }

        assertNotNull(first)
        assertEquals(first, second)
        assertEquals(1, calls.get())
    }

    @Test
    fun `getByAccountId resolves profile populated via subject query`() {
        val accountId = UUID.randomUUID()
        val subject = "internal:cross@example.com"
        val profile = ProfileResponse(accountId, "Cross User", "UTC", "EUR")
        val calls = AtomicInteger(0)

        cache.getBySubject(subject) { profile }
        val result = cache.getByAccountId(accountId) {
            calls.incrementAndGet()
            profile
        }

        assertNotNull(result)
        assertEquals(profile, result)
        assertEquals(0, calls.get())
    }

    @Test
    fun `invalidate evicts profile from both subject and accountId caches`() {
        val accountId = UUID.randomUUID()
        val subject = "internal:evict@example.com"
        val profile = ProfileResponse(accountId, "Evict User", "UTC", "EUR")
        val calls = AtomicInteger(0)

        cache.getBySubject(subject) { profile }
        cache.invalidate(subject, accountId)

        cache.getBySubject(subject) {
            calls.incrementAndGet()
            profile
        }

        assertEquals(1, calls.get())
    }
}
