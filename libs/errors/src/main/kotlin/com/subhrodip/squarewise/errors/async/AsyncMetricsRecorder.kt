package com.subhrodip.squarewise.errors.async

import com.subhrodip.squarewise.errors.code.ErrorDefinition

/** Narrow port for bounded asynchronous failure metrics. */
fun interface AsyncMetricsRecorder {
    /** Record a disposition using catalog identity and delivery attempt only. */
    fun record(definition: ErrorDefinition, disposition: MessageDisposition, attemptCount: Int)
}
