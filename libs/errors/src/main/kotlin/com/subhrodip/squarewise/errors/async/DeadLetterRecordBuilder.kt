package com.subhrodip.squarewise.errors.async

import com.subhrodip.squarewise.errors.code.ErrorDefinition
import java.time.Instant
import java.util.Base64

/** Builds bounded dead-letter records without copying throwable messages or stacks. */
object DeadLetterRecordBuilder {
    /** Build a schema-shaped record from immutable message bytes and safe catalog detail. */
    fun build(context: AsyncContext, queue: String, payload: ByteArray, definition: ErrorDefinition): DeadLetterRecord {
        require(queue.isNotBlank() && queue.length <= 255)
        require(payload.isNotEmpty())
        return DeadLetterRecord(
            originalQueue = queue,
            eventId = context.eventId,
            eventType = context.eventType,
            schemaVersion = context.schemaVersion,
            failedAt = Instant.now(),
            attemptCount = context.attemptCount,
            numericCode = definition.numericCode.value,
            errorName = definition.errorName,
            diagnosticReason = definition.safeDetail.take(512),
            messagePayloadBase64 = Base64.getEncoder().encodeToString(payload),
        )
    }
}
