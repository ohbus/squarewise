package com.subhrodip.squarewise.expensecore.messaging.outbox.model

import java.time.Instant
import java.util.UUID

/** Transactional outbox message awaiting broker publication. */
data class OutboxMessage(
    val eventId: UUID,
    val eventType: String,
    val aggregateId: UUID,
    val groupId: UUID,
    val groupRevision: Long,
    val occurredAt: Instant,
    val payload: Map<String, Any?>,
    var status: OutboxStatus = OutboxStatus.PENDING,
    var attempts: Int = 0,
    var leaseUntil: Instant? = null,
    var availableAt: Instant = occurredAt
)
