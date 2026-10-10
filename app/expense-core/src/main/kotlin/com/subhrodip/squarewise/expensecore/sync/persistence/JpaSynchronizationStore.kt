package com.subhrodip.squarewise.expensecore.sync.persistence

import com.subhrodip.squarewise.expensecore.sync.domain.InvalidSyncCursorException
import com.subhrodip.squarewise.errors.catalog.ExpenseErrors
import com.subhrodip.squarewise.expensecore.sync.domain.SyncChange
import com.subhrodip.squarewise.expensecore.sync.domain.SyncCursor
import com.subhrodip.squarewise.expensecore.sync.domain.SyncPage

import com.subhrodip.squarewise.ids.generation.UuidGenerator
import java.time.Clock
import java.time.Duration
import org.springframework.context.annotation.Primary
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * Spring Data JPA implementation of [SynchronizationStore].
 *
 * Persists sync change entries into PostgreSQL, tracking group revision bumps
 * and supporting cursor-based delta pagination.
 */
@Primary
@Service
class JpaSynchronizationStore(
    private val repository: SyncChangeRepository,
    private val clock: Clock = Clock.systemUTC(),
    private val cursorLifetime: Duration = Duration.ofHours(24)
) : SynchronizationStore {

    /**
     * Records a change for an entity under the given group and returns the newly assigned revision.
     */
    @Transactional
    override fun append(groupId: String, entityId: String, payload: String?): Long {
        require(groupId.isNotBlank()) { "groupId must not be blank" }
        require(entityId.isNotBlank()) { "entityId must not be blank" }

        val nextRevision = repository.findMaxRevision(groupId) + 1
        val entity = SyncChangeEntity(
            changeId = UuidGenerator.next(),
            groupId = groupId,
            revision = nextRevision,
            entityId = entityId,
            deleted = false,
            payload = payload,
            createdAt = clock.instant()
        )
        repository.save(entity)
        return nextRevision
    }

    /**
     * Records a tombstone deletion for an entity under the given group.
     */
    @Transactional
    override fun delete(groupId: String, entityId: String): Long {
        require(groupId.isNotBlank()) { "groupId must not be blank" }
        require(entityId.isNotBlank()) { "entityId must not be blank" }

        val nextRevision = repository.findMaxRevision(groupId) + 1
        val entity = SyncChangeEntity(
            changeId = UuidGenerator.next(),
            groupId = groupId,
            revision = nextRevision,
            entityId = entityId,
            deleted = true,
            payload = null,
            createdAt = clock.instant()
        )
        repository.save(entity)
        return nextRevision
    }

    /**
     * Retrieves a page of sync changes after a given cursor for the specified group.
     */
    @Transactional(readOnly = true)
    override fun snapshot(groupId: String, after: String?, limit: Int): SyncPage {
        require(groupId.isNotBlank()) { "groupId must not be blank" }
        require(limit in 1..100) { "limit must be between 1 and 100" }

        val start = validate(groupId, after)
        val entities = repository.findChangesAfter(groupId, start, PageRequest.of(0, limit))
        val totalRemaining = repository.countChangesAfter(groupId, start)
        val hasMore = totalRemaining > entities.size

        val domainChanges = entities.map {
            SyncChange(
                revision = it.revision,
                entityId = it.entityId,
                deleted = it.deleted,
                payload = it.payload
            )
        }

        val nextCursor = domainChanges.lastOrNull()?.let {
            SyncCursor(groupId, it.revision, clock.instant().plus(cursorLifetime)).encode()
        }

        return SyncPage(
            changes = domainChanges,
            nextCursor = nextCursor,
            hasMore = hasMore
        )
    }

    private fun validate(groupId: String, cursor: String?): Long {
        if (cursor == null) return 0
        val decoded = SyncCursor.decode(cursor)
        if (decoded.groupId != groupId) throw InvalidSyncCursorException()
        if (!decoded.expiresAt.isAfter(clock.instant())) throw InvalidSyncCursorException(ExpenseErrors.SYNC_CURSOR_EXPIRED)
        return decoded.revision
    }
}
