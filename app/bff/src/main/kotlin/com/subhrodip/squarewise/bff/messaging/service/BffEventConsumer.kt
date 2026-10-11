package com.subhrodip.squarewise.bff.messaging.service

import com.subhrodip.squarewise.bff.messaging.model.BffEventEnvelope
import com.subhrodip.squarewise.bff.messaging.model.ConsumptionResult
import com.subhrodip.squarewise.bff.messaging.model.DuplicateConsumptionResult
import com.subhrodip.squarewise.bff.messaging.model.ProcessedConsumptionResult
import com.subhrodip.squarewise.bff.messaging.persistence.BffEventDeduplicator

import com.subhrodip.squarewise.bff.realtime.LiveUpdate
import com.subhrodip.squarewise.bff.realtime.LiveUpdateFanout
import org.slf4j.LoggerFactory

/**
 * Processes incoming committed group events from Expense Core.
 *
 * Deduplicates events by [BffEventEnvelope.eventId], then emits an invalidation event
 * to reactive subscription sinks and publishes a live update to subscriber queues.
 */
class BffEventConsumer(
    private val fanout: LiveUpdateFanout,
    private val deduplicator: BffEventDeduplicator = BffEventDeduplicator()
) {
    private val log = LoggerFactory.getLogger(BffEventConsumer::class.java)

    /**
     * Consumes an event envelope.
     *
     * @param envelope the event envelope to process
     * @return [ConsumptionResult] indicating whether the event was processed, ignored as duplicate, or rejected
     */
    fun consume(envelope: BffEventEnvelope): ConsumptionResult {
        if (!deduplicator.tryClaim(envelope.eventId)) {
            log.debug("Ignoring duplicate event {}", envelope.eventId)
            return DuplicateConsumptionResult(envelope.eventId)
        }
        try {
            val groupIdStr = envelope.groupId.toString()
            val changeIdStr = envelope.eventId.toString()
            val revision = envelope.groupRevision

            if (envelope.eventType == "member.removed.v1") {
                val removedSubject = envelope.payload["targetSubject"]?.toString()?.trim()
                if (!removedSubject.isNullOrBlank()) {
                    fanout.revokeUserFromGroup(removedSubject, groupIdStr)
                }
            }

            val invalidation = fanout.emitInvalidation(groupIdStr, revision, changeIdStr)
            val deliveredQueues = fanout.publish(LiveUpdate(groupIdStr, revision))
            deduplicator.markProcessed(envelope.eventId)

            log.debug(
                "Processed group event {} for group {}, revision {}, delivered to {} queue(s)",
                envelope.eventId,
                groupIdStr,
                revision,
                deliveredQueues
            )

            return ProcessedConsumptionResult(invalidation, deliveredQueues)
        } catch (failure: Exception) {
            deduplicator.release(envelope.eventId)
            throw failure
        }
    }
}
