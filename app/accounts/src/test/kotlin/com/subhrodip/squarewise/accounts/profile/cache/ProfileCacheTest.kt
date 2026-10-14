package com.subhrodip.squarewise.accounts.profile.cache

import com.subhrodip.squarewise.accounts.profile.api.ProfileResponse
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
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

    @Test
    fun `invalidate using subject only evicts mapped accountId entry`() {
        val accountId = UUID.randomUUID()
        val subject = "internal:single-evict@example.com"
        val profile = ProfileResponse(accountId, "Single Evict User", "UTC", "EUR")

        cache.getBySubject(subject) { profile }
        // Verify accountId cache is also populated
        assertEquals(profile, cache.peekByAccountId(accountId))

        // Invalidate without providing accountId parameter
        cache.invalidate(subject)

        assertEquals(null, cache.peekByAccountId(accountId))
    }

    @Test
    fun `put and peekByAccountId store and retrieve profile without invocation`() {
        val accountId = UUID.randomUUID()
        val profile = ProfileResponse(accountId, "Direct Put", "UTC", "USD")

        cache.put(profile)
        val retrieved = cache.peekByAccountId(accountId)

        assertNotNull(retrieved)
        assertEquals(profile, retrieved)
    }

    @Test
    fun `clear purges all profiles from subject and accountId caches`() {
        val accountId = UUID.randomUUID()
        val subject = "internal:clear@example.com"
        val profile = ProfileResponse(accountId, "Clear User", "UTC", "USD")

        cache.getBySubject(subject) { profile }
        cache.clear()

        val calls = AtomicInteger(0)
        cache.getBySubject(subject) { calls.incrementAndGet(); profile }
        // After getBySubject, byAccountId is also populated with the entry
        assertEquals(1, calls.get())

        // Clearing again must purge byAccountId as well
        cache.clear()
        val accountCalls = AtomicInteger(0)
        cache.getByAccountId(accountId) { accountCalls.incrementAndGet(); profile }
        assertEquals(1, accountCalls.get())
    }

    @Test
    fun `getBySubject and getByAccountId return null when loader returns null`() {
        assertEquals(null, cache.getBySubject("nonexistent") { null })
        assertEquals(null, cache.getByAccountId(UUID.randomUUID()) { null })
    }

    @Test
    fun `peekByAccountId returns null for expired entry`() {
        val fastTtlCache = ProfileCache(ttl = Duration.ofMillis(1), maxEntries = 10)
        val accountId = UUID.randomUUID()
        fastTtlCache.put(ProfileResponse(accountId, "Fast Expire", "UTC", "USD"))
        Thread.sleep(10)
        assertNull(fastTtlCache.peekByAccountId(accountId))
    }

    @Test
    fun `getBySubject and getByAccountId reload expired entries`() {
        val fastTtlCache = ProfileCache(ttl = Duration.ofMillis(1), maxEntries = 10)
        val accountId1 = UUID.randomUUID()
        val subject = "fast:expire@example.com"
        val profile1 = ProfileResponse(accountId1, "User 1", "UTC", "USD")
        var subjectLoads = 0
        fastTtlCache.getBySubject(subject) { subjectLoads++; profile1 }
        Thread.sleep(10)
        fastTtlCache.getBySubject(subject) { subjectLoads++; profile1 }
        assertEquals(2, subjectLoads)

        val accountId2 = UUID.randomUUID()
        val profile2 = ProfileResponse(accountId2, "User 2", "UTC", "USD")
        var accountLoads = 0
        fastTtlCache.getByAccountId(accountId2) { accountLoads++; profile2 }
        Thread.sleep(10)
        fastTtlCache.getByAccountId(accountId2) { accountLoads++; profile2 }
        assertEquals(2, accountLoads)
    }

    @Test
    fun `does not cache stale result if invalidation occurred during load`() {
        val accountId1 = UUID.randomUUID()
        val subject1 = "race:user1@example.com"
        val profile1 = ProfileResponse(accountId1, "Race User 1", "UTC", "USD")

        val res = cache.getBySubject(subject1) {
            cache.invalidate(subject1, accountId1)
            profile1
        }
        assertEquals(profile1, res)
        var nextLoads = 0
        cache.getBySubject(subject1) { nextLoads++; profile1 }
        assertEquals(1, nextLoads)

        val accountId2 = UUID.randomUUID()
        val subject2 = "race:user2@example.com"
        val profile2 = ProfileResponse(accountId2, "Race User 2", "UTC", "USD")
        val resAcc = cache.getByAccountId(accountId2) {
            cache.invalidate(subject2, accountId2)
            profile2
        }
        assertEquals(profile2, resAcc)
        var nextAccLoads = 0
        cache.getByAccountId(accountId2) { nextAccLoads++; profile2 }
        assertEquals(1, nextAccLoads)
    }

    @Test
    fun `evicts oversized entries when limit is exceeded`() {
        val smallCache = ProfileCache(ttl = Duration.ofMillis(1), maxEntries = 4)
        for (i in 1..6) {
            smallCache.getBySubject("user-$i") { ProfileResponse(UUID.randomUUID(), "Name $i", "UTC", "EUR") }
        }
        Thread.sleep(10)
        smallCache.getBySubject("trigger") { ProfileResponse(UUID.randomUUID(), "Trigger", "UTC", "EUR") }

        var loaderCalls = 0
        smallCache.getBySubject("user-1") { loaderCalls++; ProfileResponse(UUID.randomUUID(), "Reloaded", "UTC", "EUR") }
        assertEquals(1, loaderCalls)

        // Also test non-expired overflow
        val smallLiveCache = ProfileCache(ttl = Duration.ofMinutes(10), maxEntries = 4)
        val initialIds = mutableListOf<UUID>()
        for (i in 1..8) {
            val id = UUID.randomUUID()
            initialIds.add(id)
            smallLiveCache.getBySubject("live-$i") { ProfileResponse(id, "Live $i", "UTC", "EUR") }
        }
        var reloads = 0
        for (i in 1..8) {
            var loaded = false
            smallLiveCache.getBySubject("live-$i") {
                loaded = true
                ProfileResponse(initialIds[i - 1], "Live $i Reloaded", "UTC", "EUR")
            }
            if (loaded) reloads++
        }
        val hits = 8 - reloads
        assertTrue(reloads >= 4)
        assertTrue(hits >= 4)
    }

    @Test
    fun `invalidate on non-cached subject with null accountId is safe no-op`() {
        val defaultCache = ProfileCache()
        defaultCache.invalidate("missing-subject", null)
        defaultCache.invalidate("missing-subject-2")

        var loaderCalls = 0
        val expected = ProfileResponse(UUID.randomUUID(), "Alice", "UTC", "EUR")
        val result = defaultCache.getBySubject("missing-subject") {
            loaderCalls++
            expected
        }
        assertEquals(expected, result)
        assertEquals(1, loaderCalls)

        // Verifies result is now cached and loader is not invoked again
        val cached = defaultCache.getBySubject("missing-subject") {
            loaderCalls++
            expected
        }
        assertEquals(expected, cached)
        assertEquals(1, loaderCalls)
    }

    @Test
    fun `put overflows byAccountId and triggers eviction of byAccountId entries`() {
        val smallCache = ProfileCache(ttl = Duration.ofMinutes(10), maxEntries = 2)
        val id1 = UUID.randomUUID()
        val id2 = UUID.randomUUID()
        val id3 = UUID.randomUUID()
        val id4 = UUID.randomUUID()
        smallCache.put(ProfileResponse(id1, "User 1", "UTC", "EUR"))
        smallCache.put(ProfileResponse(id2, "User 2", "UTC", "EUR"))
        smallCache.put(ProfileResponse(id3, "User 3", "UTC", "EUR"))
        smallCache.put(ProfileResponse(id4, "User 4", "UTC", "EUR"))
        assertEquals(ProfileResponse(id4, "User 4", "UTC", "EUR"), smallCache.peekByAccountId(id4))
    }

    @Test
    fun `evicts bySubject when multiple subjects map to same accountId`() {
        val smallCache = ProfileCache(ttl = Duration.ofMinutes(10), maxEntries = 2)
        val sharedAccountId = UUID.randomUUID()
        smallCache.getBySubject("sub-1") { ProfileResponse(sharedAccountId, "User", "UTC", "EUR") }
        smallCache.getBySubject("sub-2") { ProfileResponse(sharedAccountId, "User", "UTC", "EUR") }
        smallCache.getBySubject("sub-3") { ProfileResponse(sharedAccountId, "User", "UTC", "EUR") }
        smallCache.getBySubject("sub-4") { ProfileResponse(sharedAccountId, "User", "UTC", "EUR") }
        assertEquals(ProfileResponse(sharedAccountId, "User", "UTC", "EUR"), smallCache.getBySubject("sub-4") { null })
    }

    @Test
    fun `evicts from bySubject when byAccountId overflows with mapped subject entry`() {
        val smallCache = ProfileCache(ttl = Duration.ofMinutes(10), maxEntries = 2)
        val id1 = UUID.randomUUID()
        val id2 = UUID.randomUUID()
        val id3 = UUID.randomUUID()
        val id4 = UUID.randomUUID()
        val id5 = UUID.randomUUID()
        // Put two entries via subject
        smallCache.getBySubject("user-1") { ProfileResponse(id1, "User 1", "UTC", "EUR") }
        smallCache.getBySubject("user-2") { ProfileResponse(id2, "User 2", "UTC", "EUR") }
        // Now remove user-1 from bySubject directly via invalidate, but put id1 back into byAccountId only
        smallCache.invalidate("user-1")
        smallCache.put(ProfileResponse(id1, "User 1 direct", "UTC", "EUR"))
        // byAccountId now has id1 (subject null) and id2 (subject user-2)
        smallCache.put(ProfileResponse(id3, "User 3", "UTC", "EUR"))
        smallCache.put(ProfileResponse(id4, "User 4", "UTC", "EUR"))
        smallCache.put(ProfileResponse(id5, "User 5", "UTC", "EUR"))
        assertNotNull(smallCache.peekByAccountId(id5))
    }

    @Test
    fun `evictIfOversized prunes invalidation timestamps older than ttl`() {
        val fastCache = ProfileCache(ttl = Duration.ofMillis(1), maxEntries = 2)
        val id1 = UUID.randomUUID()
        fastCache.invalidate("old-sub", id1)
        Thread.sleep(10)
        // Trigger eviction pass with entries exceeding maxEntries
        val id2 = UUID.randomUUID()
        val id3 = UUID.randomUUID()
        val id4 = UUID.randomUUID()
        val id5 = UUID.randomUUID()
        fastCache.put(ProfileResponse(id2, "User 2", "UTC", "EUR"))
        fastCache.put(ProfileResponse(id3, "User 3", "UTC", "EUR"))
        fastCache.put(ProfileResponse(id4, "User 4", "UTC", "EUR"))
        fastCache.put(ProfileResponse(id5, "User 5", "UTC", "EUR"))
        assertNotNull(fastCache.peekByAccountId(id5))
    }

    @Test
    fun `default parameter combinations instantiate successfully`() {
        val cache1 = ProfileCache(ttl = Duration.ofMinutes(5))
        assertNull(cache1.peekByAccountId(UUID.randomUUID()))
        val cache2 = ProfileCache(maxEntries = 500)
        assertNull(cache2.peekByAccountId(UUID.randomUUID()))
    }
}
