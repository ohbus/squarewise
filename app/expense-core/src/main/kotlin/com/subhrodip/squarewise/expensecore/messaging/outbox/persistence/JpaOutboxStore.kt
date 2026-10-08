package com.subhrodip.squarewise.expensecore.messaging.outbox.persistence

import com.subhrodip.squarewise.expensecore.messaging.outbox.model.OutboxMessage
import com.subhrodip.squarewise.expensecore.messaging.outbox.model.OutboxStatus
import jakarta.persistence.EntityManager
import java.time.Duration
import java.time.Instant
import java.util.UUID
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import tools.jackson.core.type.TypeReference
import tools.jackson.databind.ObjectMapper

/**
 * Spring Data JPA implementation of [OutboxStore].
 *
 * Implements durable message appending, native SQL pessimistic skip-locked batch claiming,
 * acknowledgement, retry scheduling, and dead-letter parking.
 */
@Service
class JpaOutboxStore(
    private val repository: OutboxRepository,
    private val entityManager: EntityManager,
    private val objectMapper: ObjectMapper
) : OutboxStore {

    /**
     * Durably stores an outbox event in the database within the calling local transaction.
     */
    @Transactional
    override fun append(message: OutboxMessage) {
        require(!repository.existsById(message.eventId)) { "eventId already exists" }
        repository.save(message.toEntity(objectMapper))
    }

    /**
     * Claims up to [limit] pending or expired-lease messages using `FOR UPDATE SKIP LOCKED`.
     */
    @Transactional
    override fun claim(limit: Int, lease: Duration): List<OutboxMessage> {
        require(limit > 0) { "limit must be positive" }
        require(!lease.isNegative && !lease.isZero) { "lease must be positive" }
        val now = Instant.now()
        val claimed = entityManager.createNativeQuery(CLAIM_QUERY, OutboxEntity::class.java)
            .setParameter("now", now)
            .setParameter("limit", limit)
            .resultList
            .map { it as OutboxEntity }
        claimed.forEach { message ->
            message.status = OutboxStatus.CLAIMED
            message.leaseUntil = now.plus(lease)
            message.attempts++
        }
        entityManager.flush()
        return claimed.map { it.toDomain(objectMapper) }
    }

    /**
     * Marks an outbox event as published upon successful dispatch to the message broker.
     */
    @Transactional
    override fun acknowledge(eventId: UUID) {
        repository.findForUpdate(eventId)?.apply {
            status = OutboxStatus.PUBLISHED
            leaseUntil = null
        }
    }

    /**
     * Rejects an outbox event after a failed publish attempt, either rescheduling or marking as PARKED.
     */
    @Transactional
    override fun reject(eventId: UUID, maxAttempts: Int, retryAfter: Duration) {
        require(maxAttempts > 0) { "maxAttempts must be positive" }
        require(!retryAfter.isNegative) { "retryAfter must not be negative" }
        repository.findForUpdate(eventId)?.apply {
            leaseUntil = null
            if (attempts >= maxAttempts) {
                status = OutboxStatus.PARKED
            } else {
                status = OutboxStatus.PENDING
                availableAt = Instant.now().plus(retryAfter)
            }
        }
    }

    /**
     * Returns a snapshot of all messages ordered chronologically.
     */
    @Transactional(readOnly = true)
    override fun snapshot(): List<OutboxMessage> =
        repository.findAllByOrderByOccurredAtAscEventIdAsc().map { it.toDomain(objectMapper) }

    private companion object {
        const val CLAIM_QUERY = """
            SELECT *
            FROM expense_outbox
            WHERE (status = 'PENDING' AND available_at <= :now)
               OR (status = 'CLAIMED' AND lease_until < :now)
            ORDER BY occurred_at, event_id
            LIMIT :limit
            FOR UPDATE SKIP LOCKED
        """
    }
}

/**
 * Maps domain [OutboxMessage] to persistence entity [OutboxEntity].
 */
private fun OutboxMessage.toEntity(objectMapper: ObjectMapper) = OutboxEntity(
    eventId = eventId,
    eventType = eventType,
    aggregateId = aggregateId,
    groupId = groupId,
    groupRevision = groupRevision,
    occurredAt = occurredAt,
    payload = objectMapper.writeValueAsString(payload),
    status = status,
    attempts = attempts,
    leaseUntil = leaseUntil,
    availableAt = availableAt
)

/**
 * Maps persistence entity [OutboxEntity] to domain [OutboxMessage].
 */
private fun OutboxEntity.toDomain(objectMapper: ObjectMapper) = OutboxMessage(
    eventId = eventId,
    eventType = eventType,
    aggregateId = aggregateId,
    groupId = groupId,
    groupRevision = groupRevision,
    occurredAt = occurredAt,
    payload = objectMapper.readValue(payload, object : TypeReference<Map<String, Any?>>() {}),
    status = status,
    attempts = attempts,
    leaseUntil = leaseUntil,
    availableAt = availableAt
)
