package com.subhrodip.squarewise.expensecore.messaging.outbox.persistence

import com.subhrodip.squarewise.expensecore.messaging.outbox.model.OutboxStatus
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

/**
 * JPA entity representing an outbox event message.
 *
 * Implements the Transactional Outbox pattern, storing domain events atomically
 * within the local transaction before asynchronous relay.
 */
@Entity
@Table(name = "expense_outbox")
class OutboxEntity(
    @Id
    @Column(name = "event_id", nullable = false)
    var eventId: UUID,

    @Column(name = "event_type", nullable = false, length = 120)
    var eventType: String,

    @Column(name = "aggregate_id", nullable = false)
    var aggregateId: UUID,

    @Column(name = "group_id", nullable = false)
    var groupId: UUID,

    @Column(name = "group_revision", nullable = false)
    var groupRevision: Long,

    @Column(name = "occurred_at", nullable = false)
    var occurredAt: Instant,

    @Column(name = "payload", nullable = false, columnDefinition = "TEXT")
    var payload: String,

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    var status: OutboxStatus = OutboxStatus.PENDING,

    @Column(name = "attempts", nullable = false)
    var attempts: Int = 0,

    @Column(name = "lease_until")
    var leaseUntil: Instant? = null,

    @Column(name = "available_at", nullable = false)
    var availableAt: Instant = occurredAt
)
