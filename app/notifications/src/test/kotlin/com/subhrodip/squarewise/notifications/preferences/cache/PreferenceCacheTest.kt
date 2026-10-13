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
}
