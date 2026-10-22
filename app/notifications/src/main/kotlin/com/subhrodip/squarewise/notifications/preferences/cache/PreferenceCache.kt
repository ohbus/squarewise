package com.subhrodip.squarewise.notifications.preferences.cache

import com.subhrodip.squarewise.notifications.preferences.model.NotificationPreferences
import org.springframework.stereotype.Component
import java.time.Duration
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

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
    private val inFlightLoads = ConcurrentHashMap.newKeySet<String>()
    private val lastCleanupNanos = AtomicLong(0)

    /**
     * Resolves notification preferences for a user, using cached state if valid.
     *
     * @param subject user subject identifier.
     * @param loader fallback supplier to query the database on cache miss.
     * @return resolved [NotificationPreferences].
     */
    fun get(subject: String, loader: () -> NotificationPreferences): NotificationPreferences {
        val now = Instant.now()
        cleanupInvalidationTimestamps(now)
        val cached = cache[subject]
        if (cached != null && !cached.isExpired(now)) {
            return cached.preferences
        }
        val loadStart = Instant.now()
        inFlightLoads.add(subject)
        try {
            val computed = loader()
            evictIfOversized()
            cache.compute(subject) { _, existing ->
                val invalidatedAt = invalidationTimestamps[subject]
                if (invalidatedAt == null || !invalidatedAt.isAfter(loadStart)) {
                    Entry(computed, Instant.now().plus(ttl))
                } else {
                    existing
                }
            }
            return computed
        } finally {
            inFlightLoads.remove(subject)
        }
    }

    /**
     * Invalidates cached preferences for a user subject.
     *
     * @param subject user subject identifier.
     */
    fun invalidate(subject: String) {
        val now = Instant.now()
        invalidationTimestamps[subject] = now
        cache.remove(subject)
        cleanupInvalidationTimestamps(now)
    }

    /** Clears all cached preferences. */
    fun clear() {
        cache.clear()
        invalidationTimestamps.clear()
        lastCleanupNanos.set(0)
    }

    private fun evictIfOversized() {
        val now = Instant.now()
        cleanupInvalidationTimestamps(now)
        if (cache.size > maxEntries) {
            cache.entries.removeIf { it.value.isExpired(now) }
            if (cache.size > maxEntries) {
                val target = maxEntries / 4
                cache.keys.take(target).forEach { cache.remove(it) }
            }
        }
    }

    private fun cleanupInvalidationTimestamps(now: Instant) {
        val nowNanos = System.nanoTime()
        val lastCleanup = lastCleanupNanos.get()
        if (lastCleanup != 0L &&
            nowNanos - lastCleanup < CLEANUP_INTERVAL_NANOS
        ) {
            return
        }
        if (!lastCleanupNanos.compareAndSet(lastCleanup, nowNanos)) {
            return
        }
        val cutoff = now.minus(ttl)
        invalidationTimestamps.entries.removeIf { (subject, invalidatedAt) ->
            !inFlightLoads.contains(subject) && invalidatedAt.isBefore(cutoff)
        }
    }

    private companion object {
        /** Keeps cache hits O(1) while bounding stale-marker retention to a short interval. */
        const val CLEANUP_INTERVAL_NANOS = 1_000_000_000L
    }
}
