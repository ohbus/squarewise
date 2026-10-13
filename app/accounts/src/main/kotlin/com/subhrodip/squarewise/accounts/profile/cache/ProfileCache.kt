package com.subhrodip.squarewise.accounts.profile.cache

import com.subhrodip.squarewise.accounts.profile.api.ProfileResponse
import org.springframework.stereotype.Component
import java.time.Duration
import java.time.Instant
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Thread-safe bounded in-memory cache for resolved user profiles.
 *
 * Caches [ProfileResponse] records by subject and by accountId with short TTL,
 * eliminating database roundtrips on high-frequency authenticated requests.
 * In multi-instance deployments, local node caches accept up to [ttl] of eventual consistency
 * unless invalidated via transaction hooks or distributed channels.
 */
@Component
class ProfileCache(
    private val ttl: Duration = Duration.ofSeconds(60),
    private val maxEntries: Int = 10_000
) {
    private data class Entry(val profile: ProfileResponse, val subject: String?, val expiresAt: Instant) {
        fun isExpired(now: Instant): Boolean = now.isAfter(expiresAt)
    }

    private val bySubject = ConcurrentHashMap<String, Entry>()
    private val byAccountId = ConcurrentHashMap<UUID, Entry>()
    private val subjectInvalidationTimes = ConcurrentHashMap<String, Instant>()
    private val accountIdInvalidationTimes = ConcurrentHashMap<UUID, Instant>()

    /**
     * Resolves a profile by user subject, serving from cache if fresh or falling back to loader.
     *
     * @param subject user subject identifier.
     * @param loader fallback supplier to query the database on cache miss.
     * @return resolved [ProfileResponse] or null.
     */
    fun getBySubject(subject: String, loader: () -> ProfileResponse?): ProfileResponse? {
        val now = Instant.now()
        val cached = bySubject[subject]
        if (cached != null && !cached.isExpired(now)) {
            return cached.profile
        }
        val loadStartTime = Instant.now()
        val profile = loader() ?: return null
        val lastInvalidated = subjectInvalidationTimes[subject]
        if (lastInvalidated != null && !loadStartTime.isAfter(lastInvalidated)) {
            // Invalidation occurred during or after this load started; do not cache stale result
            return profile
        }
        evictIfOversized()
        val loadedAt = Instant.now()
        val entry = Entry(profile, subject, loadedAt.plus(ttl))
        bySubject[subject] = entry
        byAccountId[profile.accountId] = entry
        return profile
    }

    /**
     * Resolves a profile by account identifier, serving from cache if fresh or falling back to loader.
     *
     * @param accountId account UUID.
     * @param loader fallback supplier to query the database on cache miss.
     * @return resolved [ProfileResponse] or null.
     */
    fun getByAccountId(accountId: UUID, loader: () -> ProfileResponse?): ProfileResponse? {
        val now = Instant.now()
        val cached = byAccountId[accountId]
        if (cached != null && !cached.isExpired(now)) {
            return cached.profile
        }
        val loadStartTime = Instant.now()
        val profile = loader() ?: return null
        val lastInvalidated = accountIdInvalidationTimes[accountId]
        if (lastInvalidated != null && !loadStartTime.isAfter(lastInvalidated)) {
            return profile
        }
        evictIfOversized()
        val loadedAt = Instant.now()
        val entry = Entry(profile, null, loadedAt.plus(ttl))
        byAccountId[accountId] = entry
        return profile
    }

    /**
     * Looks up an existing non-expired profile by account ID without invoking a loader.
     *
     * @param accountId account UUID.
     * @return cached [ProfileResponse] or null if missing or expired.
     */
    fun peekByAccountId(accountId: UUID): ProfileResponse? {
        val now = Instant.now()
        val cached = byAccountId[accountId]
        return if (cached != null && !cached.isExpired(now)) cached.profile else null
    }

    /**
     * Stores a resolved profile into the accountId cache.
     *
     * @param profile resolved [ProfileResponse] to cache.
     */
    fun put(profile: ProfileResponse) {
        val now = Instant.now()
        evictIfOversized()
        val entry = Entry(profile, null, now.plus(ttl))
        byAccountId[profile.accountId] = entry
    }

    /**
     * Invalidates cached profile entries by subject and optionally accountId.
     *
     * @param subject user subject identifier.
     * @param accountId optional account UUID to invalidate from the accountId map.
     */
    fun invalidate(subject: String, accountId: UUID? = null) {
        val now = Instant.now()
        subjectInvalidationTimes[subject] = now
        val removed = bySubject.remove(subject)
        val targetAccountId = accountId ?: removed?.profile?.accountId
        if (targetAccountId != null) {
            accountIdInvalidationTimes[targetAccountId] = now
            byAccountId.remove(targetAccountId)
        }
    }

    /** Clears all cached profile entries. */
    fun clear() {
        bySubject.clear()
        byAccountId.clear()
        subjectInvalidationTimes.clear()
        accountIdInvalidationTimes.clear()
    }

    private fun evictIfOversized() {
        if (bySubject.size > maxEntries || byAccountId.size > maxEntries) {
            val now = Instant.now()
            val cutoff = now.minus(ttl)
            subjectInvalidationTimes.entries.removeIf { it.value.isBefore(cutoff) }
            accountIdInvalidationTimes.entries.removeIf { it.value.isBefore(cutoff) }
            bySubject.entries.removeIf { it.value.isExpired(now) }
            byAccountId.entries.removeIf { it.value.isExpired(now) }
            while (bySubject.size > maxEntries) {
                val key = bySubject.keys().nextElement()
                val removed = bySubject.remove(key)
                if (removed != null) {
                    byAccountId.remove(removed.profile.accountId)
                }
            }
            while (byAccountId.size > maxEntries) {
                val key = byAccountId.keys().nextElement()
                val removed = byAccountId.remove(key)
                val subject = removed?.subject
                if (subject != null) {
                    bySubject.remove(subject)
                }
            }
        }
    }
}
