package com.subhrodip.squarewise.notifications.preferences.cache

import com.subhrodip.squarewise.notifications.preferences.model.NotificationPreferences
import org.springframework.stereotype.Component
import java.time.Duration
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

/**
 * Thread-safe bounded in-memory cache for user notification preferences.
 *
 * Eliminates repeated database queries when background workers process event streams.
 */
@Component
class PreferenceCache(
    private val ttl: Duration = Duration.ofSeconds(60),
    private val maxEntries: Int = 10_000
) {
    private data class Entry(val preferences: NotificationPreferences, val expiresAt: Instant) {
        fun isExpired(now: Instant): Boolean = now.isAfter(expiresAt)
    }

    private val cache = ConcurrentHashMap<String, Entry>()
    private val invalidationTimestamps = ConcurrentHashMap<String, Instant>()

    /**
     * Resolves notification preferences for a user, using cached state if valid.
     *
     * @param subject user subject identifier.
     * @param loader fallback supplier to query the database on cache miss.
     * @return resolved [NotificationPreferences].
     */
    fun get(subject: String, loader: () -> NotificationPreferences): NotificationPreferences {
        val now = Instant.now()
        val cached = cache[subject]
        if (cached != null && !cached.isExpired(now)) {
            return cached.preferences
        }
        val loadStart = Instant.now()
        val computed = loader()
        val invalidatedAt = invalidationTimestamps[subject]
        if (invalidatedAt == null || !invalidatedAt.isAfter(loadStart)) {
            evictIfOversized()
            cache[subject] = Entry(computed, now.plus(ttl))
        }
        return computed
    }

    /**
     * Invalidates cached preferences for a user subject.
     *
     * @param subject user subject identifier.
     */
    fun invalidate(subject: String) {
        invalidationTimestamps[subject] = Instant.now()
        cache.remove(subject)
    }

    /** Clears all cached preferences. */
    fun clear() {
        cache.clear()
        invalidationTimestamps.clear()
    }

    private fun evictIfOversized() {
        if (cache.size > maxEntries) {
            val now = Instant.now()
            cache.entries.removeIf { it.value.isExpired(now) }
            if (cache.size > maxEntries) {
                val iter = cache.keys.iterator()
                var removed = 0
                val target = maxEntries / 4
                while (iter.hasNext() && removed < target) {
                    iter.next()
                    iter.remove()
                    removed++
                }
            }
        }
    }
}
