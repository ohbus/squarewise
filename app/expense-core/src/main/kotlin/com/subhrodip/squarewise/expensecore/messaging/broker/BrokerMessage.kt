package com.subhrodip.squarewise.expensecore.messaging.broker

import java.time.Instant
import java.util.UUID

/** Immutable event payload passed through the broker boundary. */
data class BrokerMessage(
    val eventId: UUID,
    val eventType: String,
    val payload: ByteArray,
    val occurredAt: Instant,
    val headers: Map<String, String> = emptyMap()
) {
    override fun equals(other: Any?): Boolean = other is BrokerMessage && eventId == other.eventId && eventType == other.eventType && payload.contentEquals(other.payload) && occurredAt == other.occurredAt && headers == other.headers
    override fun hashCode(): Int = listOf(eventId, eventType, payload.contentHashCode(), occurredAt, headers).hashCode()
    override fun toString(): String = "BrokerMessage(eventId=$eventId, eventType='$eventType', payload=${payload.contentToString()}, occurredAt=$occurredAt, headers=$headers)"
}
