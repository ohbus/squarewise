package com.subhrodip.squarewise.expensecore.messaging.outbox.service

import com.subhrodip.squarewise.expensecore.messaging.outbox.model.OutboxMessage
import com.subhrodip.squarewise.expensecore.messaging.outbox.model.OutboxStatus
import com.subhrodip.squarewise.expensecore.messaging.outbox.persistence.OutboxStore
import java.time.Duration
import java.time.Instant
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/** Deterministic in-memory outbox adapter used by domain tests. */
class OutboxRelay(private val clock: () -> Instant = Instant::now) : OutboxStore {
    private val messages = ConcurrentHashMap<UUID, OutboxMessage>()
    override fun append(message: OutboxMessage) { require(messages.putIfAbsent(message.eventId, message) == null) { "eventId already exists" } }
    @Synchronized override fun claim(limit: Int, lease: Duration): List<OutboxMessage> {
        require(limit > 0)
        val now = clock()
        return messages.values.asSequence()
            .filter { (it.status == OutboxStatus.PENDING && !it.availableAt.isAfter(now)) || (it.status == OutboxStatus.CLAIMED && it.leaseUntil?.isBefore(now) == true) }
            .sortedBy { it.occurredAt }.take(limit).onEach { it.status = OutboxStatus.CLAIMED; it.leaseUntil = now.plus(lease); it.attempts++ }.toList()
    }
    @Synchronized override fun acknowledge(eventId: UUID) { messages[eventId]?.apply { status = OutboxStatus.PUBLISHED; leaseUntil = null } }
    @Synchronized override fun reject(eventId: UUID, maxAttempts: Int, retryAfter: Duration) {
        require(maxAttempts > 0) { "maxAttempts must be positive" }
        require(!retryAfter.isNegative) { "retryAfter must not be negative" }
        messages[eventId]?.apply { leaseUntil = null; if (attempts >= maxAttempts) status = OutboxStatus.PARKED else { status = OutboxStatus.PENDING; availableAt = clock().plus(retryAfter) } }
    }
    override fun snapshot(): List<OutboxMessage> = messages.values.sortedBy { it.occurredAt }
}
