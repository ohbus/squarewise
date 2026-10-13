package com.subhrodip.squarewise.notifications.preferences.cache

import com.subhrodip.squarewise.notifications.preferences.model.NotificationPreferences
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Duration
import java.util.concurrent.atomic.AtomicInteger

/**
 * Unit tests verifying [PreferenceCache] caching, hit/miss, and invalidation.
 */
class PreferenceCacheTest {

    private lateinit var cache: PreferenceCache

    @BeforeEach
    fun setUp() {
        cache = PreferenceCache(ttl = Duration.ofSeconds(10), maxEntries = 100)
    }

    @Test
    fun `get serves cached preferences without invoking loader repeatedly`() {
        val subject = "user-pref-1"
        val prefs = NotificationPreferences(emailEnabled = true)
        val calls = AtomicInteger(0)

        val first = cache.get(subject) {
            calls.incrementAndGet()
            prefs
        }
        val second = cache.get(subject) {
            calls.incrementAndGet()
            prefs
        }

        assertNotNull(first)
        assertEquals(first, second)
        assertEquals(1, calls.get())
    }

    @Test
    fun `invalidate evicts cached preferences forcing loader on next get`() {
        val subject = "user-pref-2"
        val prefs1 = NotificationPreferences(emailEnabled = true)
        val prefs2 = NotificationPreferences(emailEnabled = false)
        val calls = AtomicInteger(0)

        cache.get(subject) { calls.incrementAndGet(); prefs1 }
        cache.invalidate(subject)
        val result = cache.get(subject) { calls.incrementAndGet(); prefs2 }

        assertEquals(prefs2, result)
        assertEquals(2, calls.get())
    }

    @Test
    fun `clear empties the cache completely`() {
        val subject = "user-pref-3"
        val prefs = NotificationPreferences(emailEnabled = true)
        val calls = AtomicInteger(0)

        cache.get(subject) { calls.incrementAndGet(); prefs }
        cache.clear()
        cache.get(subject) { calls.incrementAndGet(); prefs }

        assertEquals(2, calls.get())
    }

    @Test
    fun `evicts unexpired entries when size still exceeds maxEntries after expired eviction`() {
        val smallCache = PreferenceCache(ttl = Duration.ofMinutes(10), maxEntries = 4)
        val prefs = NotificationPreferences(emailEnabled = true)
        val calls = AtomicInteger(0)

        for (i in 1..8) {
            smallCache.get("user-$i") { calls.incrementAndGet(); prefs }
        }
        assertEquals(8, calls.get())

        // Re-requesting all items should invoke loader for entries evicted when capacity was exceeded
        for (i in 1..8) {
            smallCache.get("user-$i") { calls.incrementAndGet(); prefs }
        }
        org.junit.jupiter.api.Assertions.assertTrue(calls.get() > 8)
    }

    @Test
    fun `does not cache entry if invalidated while loader was executing`() {
        val subject = "user-race"
        val prefs = NotificationPreferences(emailEnabled = true)
        val calls = AtomicInteger(0)

        cache.get(subject) {
            calls.incrementAndGet()
            cache.invalidate(subject)
            prefs
        }

        // Because it was invalidated during load, it should not be cached
        cache.get(subject) {
            calls.incrementAndGet()
            prefs
        }

        assertEquals(2, calls.get())
    }

    @Test
    fun `reloads preferences when cached entry has expired`() {
        val shortCache = PreferenceCache(ttl = Duration.ofMillis(5), maxEntries = 100)
        val prefs = NotificationPreferences(emailEnabled = true)
        val calls = AtomicInteger(0)

        shortCache.get("user-exp") { calls.incrementAndGet(); prefs }
        Thread.sleep(10)
        shortCache.get("user-exp") { calls.incrementAndGet(); prefs }

        assertEquals(2, calls.get())
    }

    @Test
    fun `eviction of expired items drops size below maxEntries skipping lru pass`() {
        val expiringCache = PreferenceCache(ttl = Duration.ofMillis(5), maxEntries = 2)
        val prefs = NotificationPreferences(emailEnabled = true)

        expiringCache.get("exp-1") { prefs }
        expiringCache.get("exp-2") { prefs }
        Thread.sleep(10)
        expiringCache.get("exp-3") { prefs }
        expiringCache.get("exp-4") { prefs }
    }
}
