package com.subhrodip.squarewise.expensecore.groups.cache

import org.springframework.stereotype.Component
import java.time.Duration
import java.time.Instant
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Thread-safe bounded in-memory cache for hot group access validation paths.
 *
 * Caches active membership status and group archival status with short TTL,
 * eliminating high-frequency repeated database lookups on expense and balance endpoints.
 */
@Component
class GroupAccessCache(
    private val ttl: Duration = Duration.ofSeconds(30),
    private val maxEntries: Int = 10_000
) {
    private data class CacheEntry<T>(val value: T, val expiresAt: Instant) {
        fun isExpired(now: Instant): Boolean = now.isAfter(expiresAt)
    }

    private val membershipCache = ConcurrentHashMap<Pair<UUID, String>, CacheEntry<Boolean>>()
    private val groupStatusCache = ConcurrentHashMap<UUID, CacheEntry<String?>>()

    /**
     * Checks if the caller subject is an active member of the group, using cached state if valid.
     *
     * @param groupId unique group identifier.
     * @param subject user subject identifier.
     * @param loader fallback supplier to query the database on cache miss.
     * @return true if member is active, false otherwise.
     */
    fun isMember(groupId: UUID, subject: String, loader: () -> Boolean): Boolean {
        val key = groupId to subject
        val now = Instant.now()
        val cached = membershipCache[key]
        if (cached != null && !cached.isExpired(now)) {
            return cached.value
        }
        val computed = loader()
        evictIfOversized(membershipCache)
        membershipCache[key] = CacheEntry(computed, now.plus(ttl))
        return computed
    }

    /**
     * Resolves the current status of the group, using cached status if valid.
     *
     * @param groupId unique group identifier.
     * @param loader fallback supplier to query the database on cache miss.
     * @return group status string, or null if group does not exist.
     */
    fun groupStatus(groupId: UUID, loader: () -> String?): String? {
        val now = Instant.now()
        val cached = groupStatusCache[groupId]
        if (cached != null && !cached.isExpired(now)) {
            return cached.value
        }
        val computed = loader()
        evictIfOversized(groupStatusCache)
        groupStatusCache[groupId] = CacheEntry(computed, now.plus(ttl))
        return computed
    }

    /**
     * Invalidates all cached status and membership entries for the given group.
     *
     * @param groupId unique group identifier to invalidate.
     */
    fun invalidateGroup(groupId: UUID) {
        groupStatusCache.remove(groupId)
        membershipCache.keys.removeIf { it.first == groupId }
    }

    /**
     * Invalidates a specific membership entry.
     *
     * @param groupId unique group identifier.
     * @param subject user subject identifier.
     */
    fun invalidateMember(groupId: UUID, subject: String) {
        membershipCache.remove(groupId to subject)
    }

    /** Clears all caches completely. */
    fun clear() {
        membershipCache.clear()
        groupStatusCache.clear()
    }

    private fun <K, V> evictIfOversized(map: ConcurrentHashMap<K, CacheEntry<V>>) {
        if (map.size > maxEntries) {
            val now = Instant.now()
            map.entries.removeIf { it.value.isExpired(now) }
            if (map.size > maxEntries) {
                val iter = map.keys.iterator()
                var removed = 0
                while (iter.hasNext() && removed < maxEntries / 4) {
                    iter.next()
                    iter.remove()
                    removed++
                }
            }
        }
    }
}
