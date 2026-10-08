package com.subhrodip.squarewise.expensecore.messaging.outbox.persistence

import com.subhrodip.squarewise.expensecore.messaging.outbox.model.OutboxMessage
import java.time.Duration
import java.util.UUID

/** Writer-side transactional outbox command port. */
interface OutboxCommandStore {
    fun append(message: OutboxMessage)
    fun claim(limit: Int, lease: Duration): List<OutboxMessage>
    fun acknowledge(eventId: UUID)
    fun reject(eventId: UUID, maxAttempts: Int, retryAfter: Duration)
}
