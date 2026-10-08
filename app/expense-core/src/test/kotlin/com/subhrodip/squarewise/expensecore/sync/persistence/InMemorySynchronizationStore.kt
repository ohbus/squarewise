package com.subhrodip.squarewise.expensecore.sync.persistence

import com.subhrodip.squarewise.expensecore.sync.domain.InvalidSyncCursorException
import com.subhrodip.squarewise.expensecore.sync.domain.SyncChange
import com.subhrodip.squarewise.expensecore.sync.domain.SyncCursor
import com.subhrodip.squarewise.expensecore.sync.domain.SyncPage

import java.time.Clock
import java.time.Duration

/** In-memory synchronization adapter for local and test deployments. */
class InMemorySynchronizationStore(
    private val clock: Clock = Clock.systemUTC(),
    private val cursorLifetime: Duration = Duration.ofHours(24)
) : SynchronizationStore {
    private val changes = mutableMapOf<String, MutableList<SyncChange>>()

    @Synchronized
    override fun append(entityId: String, payload: String?): Long = append("default", entityId, payload)

    @Synchronized
    override fun append(groupId: String, entityId: String, payload: String?): Long {
        require(groupId.isNotBlank())
        require(entityId.isNotBlank())
        return record(groupId, SyncChange(nextRevision(groupId), entityId, false, payload))
    }

    @Synchronized
    override fun delete(entityId: String): Long = delete("default", entityId)

    @Synchronized
    override fun delete(groupId: String, entityId: String): Long {
        require(groupId.isNotBlank())
        require(entityId.isNotBlank())
        return record(groupId, SyncChange(nextRevision(groupId), entityId, true, null))
    }

    @Synchronized
    override fun snapshot(after: String?, limit: Int): SyncPage = snapshot("default", after, limit)

    @Synchronized
    override fun snapshot(groupId: String, after: String?, limit: Int): SyncPage {
        require(groupId.isNotBlank())
        require(limit in 1..100)
        val start = validate(groupId, after)
        val groupChanges = changes[groupId].orEmpty()
        val page = groupChanges.filter { it.revision > start }.take(limit)
        val hasMore = groupChanges.any { it.revision > start + page.size }
        val next = page.lastOrNull()?.let {
            SyncCursor(groupId, it.revision, clock.instant().plus(cursorLifetime)).encode()
        }
        return SyncPage(page, next, hasMore)
    }

    private fun validate(groupId: String, cursor: String?): Long {
        if (cursor == null) return 0
        val decoded = SyncCursor.decode(cursor)
        if (decoded.groupId != groupId || !decoded.expiresAt.isAfter(clock.instant())) {
            throw InvalidSyncCursorException()
        }
        return decoded.revision
    }

    private fun nextRevision(groupId: String): Long = (changes[groupId]?.lastOrNull()?.revision ?: 0) + 1

    private fun record(groupId: String, change: SyncChange): Long {
        changes.getOrPut(groupId) { mutableListOf() }.add(change)
        return change.revision
    }
}
