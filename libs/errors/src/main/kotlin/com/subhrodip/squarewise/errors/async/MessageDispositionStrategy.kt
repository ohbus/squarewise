package com.subhrodip.squarewise.errors.async

import com.subhrodip.squarewise.errors.code.ErrorDefinition
import com.subhrodip.squarewise.errors.code.RetryPolicy
import com.subhrodip.squarewise.errors.exceptions.FatalErrorClassifier

/** Applies bounded retry policy and routes poison messages to dead letters. */
object MessageDispositionStrategy {
    /** Decide without swallowing fatal JVM or cancellation conditions. */
    fun decide(definition: ErrorDefinition, attemptCount: Int, throwable: Throwable): MessageDisposition {
        if (FatalErrorClassifier.isFatal(throwable)) throw throwable
        return if (throwable is IllegalArgumentException) {
            MessageDisposition.DEAD_LETTERED
        } else if (definition.retryPolicy == RetryPolicy.RETRY_AFTER && attemptCount < MAX_RETRIES) {
            MessageDisposition.NACK_REQUEUE
        } else {
            MessageDisposition.DEAD_LETTERED
        }
    }

    private const val MAX_RETRIES: Int = 3
}
