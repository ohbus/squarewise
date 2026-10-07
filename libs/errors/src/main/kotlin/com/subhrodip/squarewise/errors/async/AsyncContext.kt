package com.subhrodip.squarewise.errors.async

import java.util.UUID

/** Bounded correlation and delivery metadata for one asynchronous operation. */
data class AsyncContext(
    val eventId: UUID,
    val eventType: String,
    val schemaVersion: Int,
    val attemptCount: Int,
    val source: String,
) {
    init {
        require(eventType.isNotBlank() && eventType.length <= 128)
        require(schemaVersion >= 1)
        require(attemptCount in 1..4)
        require(source.isNotBlank() && source.length <= 80)
    }
}
