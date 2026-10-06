package com.subhrodip.squarewise.errors.async

import com.subhrodip.squarewise.errors.code.ErrorDefinition
import com.subhrodip.squarewise.errors.request.RequestIdContext

/** Executes asynchronous work with correlation scope, bounded disposition, and cleanup. */
class AsyncExecutionTemplate(
    private val metricsRecorder: AsyncMetricsRecorder,
) {
    /** Execute a message operation and route only non-fatal failures to a disposition. */
    fun <T> execute(
        context: AsyncContext,
        definition: ErrorDefinition,
        queue: String,
        payload: ByteArray,
        block: () -> T,
    ): AsyncExecutionResult<T> = RequestIdContext.with(context.eventId.toString()) {
        try {
            AsyncExecutionResult.Completed(block())
        } catch (exception: Exception) {
            val disposition = MessageDispositionStrategy.decide(definition, context.attemptCount, exception)
            val deadLetter = if (disposition == MessageDisposition.DEAD_LETTERED) {
                DeadLetterRecordBuilder.build(context, queue, payload, definition)
            } else {
                null
            }
            metricsRecorder.record(definition, disposition, context.attemptCount)
            AsyncExecutionResult.Failed(disposition, deadLetter)
        }
    }
}
