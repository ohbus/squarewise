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
 *
 * Multi-instance staleness policy:
 * Local node caches hold group authorization and roster status for up to 30 seconds.
 * Under multi-instance deployments, membership changes (e.g. member removal) invalidate
 * local cache upon transaction commit; cross-node instances observe up to 30 seconds of
 * eventual consistency bounded by TTL, which is an accepted trade-off to protect the primary
 * database writer from high read-frequency authorization queries at 10M DAU scale.
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
    private val membersListCache = ConcurrentHashMap<Pair<UUID, Int>, CacheEntry<List<*>>>()
    private val balancesCache = ConcurrentHashMap<UUID, CacheEntry<List<*>>>()
    private val memberIdsCache = ConcurrentHashMap<UUID, CacheEntry<Set<UUID>>>()
    private val groupDetailsCache = ConcurrentHashMap<UUID, CacheEntry<Any?>>()
    private val invalidationTimestamps = ConcurrentHashMap<Pair<UUID, String>, Instant>()

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
        val loadStart = Instant.now()
        val computed = loader()
        val invalidatedAt = invalidationTimestamps[key]
        if (invalidatedAt == null || !invalidatedAt.isAfter(loadStart)) {
            evictIfOversized(membershipCache)
            membershipCache[key] = CacheEntry(computed, now.plus(ttl))
        }
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
     * Resolves active member list of a group, using cached list if valid.
     *
     * @param groupId unique group identifier.
     * @param limit maximum items requested.
     * @param loader fallback supplier to query the database on cache miss.
     * @return list of group members.
     */
    @Suppress("UNCHECKED_CAST")
    fun <T> members(groupId: UUID, limit: Int, loader: () -> List<T>): List<T> {
        val key = groupId to limit
        val now = Instant.now()
        val cached = membersListCache[key]
        if (cached != null && !cached.isExpired(now)) {
            return cached.value as List<T>
        }
        val computed = loader()
        evictIfOversized(membersListCache)
        membersListCache[key] = CacheEntry(computed, now.plus(ttl))
        return computed
    }

    /**
     * Resolves group balances, using cached list if valid.
     *
     * @param groupId unique group identifier.
     * @param loader fallback supplier to query the database on cache miss.
     * @return list of computed balance items.
     */
    @Suppress("UNCHECKED_CAST")
    fun <T> balances(groupId: UUID, loader: () -> List<T>): List<T> {
        val now = Instant.now()
        val cached = balancesCache[groupId]
        if (cached != null && !cached.isExpired(now)) {
            return cached.value as List<T>
        }
        val computed = loader()
        evictIfOversized(balancesCache)
        balancesCache[groupId] = CacheEntry(computed, now.plus(ttl))
        return computed
    }

    /**
     * Resolves active member UUIDs for a group, using cached set if valid.
     *
     * @param groupId unique group identifier.
     * @param loader fallback supplier to query the database on cache miss.
     * @return set of active member UUIDs.
     */
    fun activeMemberIds(groupId: UUID, loader: () -> Set<UUID>): Set<UUID> {
        val now = Instant.now()
        val cached = memberIdsCache[groupId]
        if (cached != null && !cached.isExpired(now)) {
            return cached.value
        }
        val computed = loader()
        evictIfOversized(memberIdsCache)
        memberIdsCache[groupId] = CacheEntry(computed, now.plus(ttl))
        return computed
    }

    /**
     * Resolves full group details response, using cached object if valid.
     *
     * @param groupId unique group identifier.
     * @param loader fallback supplier to query the database on cache miss.
     * @return resolved group details or null.
     */
    @Suppress("UNCHECKED_CAST")
    fun <T> groupDetails(groupId: UUID, loader: () -> T?): T? {
        val now = Instant.now()
        val cached = groupDetailsCache[groupId]
        if (cached != null && !cached.isExpired(now)) {
            return cached.value as T?
        }
        val computed = loader()
        evictIfOversized(groupDetailsCache)
        groupDetailsCache[groupId] = CacheEntry(computed, now.plus(ttl))
        return computed
    }

    /**
     * Invalidates all cached status, memberships, rosters, details, and balances for the given group.
     *
     * @param groupId unique group identifier to invalidate.
     */
    fun invalidateGroup(groupId: UUID) {
        val now = Instant.now()
        groupStatusCache.remove(groupId)
        groupDetailsCache.remove(groupId)
        membershipCache.keys.filter { it.first == groupId }.forEach { key ->
            invalidationTimestamps[key] = now
            membershipCache.remove(key)
        }
        invalidateMembers(groupId)
        invalidateBalances(groupId)
    }

    /**
     * Invalidates a specific membership entry and group member roster.
     *
     * @param groupId unique group identifier.
     * @param subject user subject identifier.
     */
    fun invalidateMember(groupId: UUID, subject: String) {
        invalidationTimestamps[groupId to subject] = Instant.now()
        membershipCache.remove(groupId to subject)
        invalidateMembers(groupId)
    }

    /**
     * Invalidates cached member list and member IDs for a group.
     *
     * @param groupId unique group identifier.
     */
    fun invalidateMembers(groupId: UUID) {
        membersListCache.keys.removeIf { it.first == groupId }
        memberIdsCache.remove(groupId)
    }

    /**
     * Invalidates cached balances for a group.
     *
     * @param groupId unique group identifier.
     */
    fun invalidateBalances(groupId: UUID) {
        balancesCache.remove(groupId)
    }

    /** Clears all caches completely. */
    fun clear() {
        membershipCache.clear()
        groupStatusCache.clear()
        groupDetailsCache.clear()
        membersListCache.clear()
        balancesCache.clear()
        memberIdsCache.clear()
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
