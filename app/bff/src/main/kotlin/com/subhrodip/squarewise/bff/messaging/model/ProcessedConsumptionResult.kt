package com.subhrodip.squarewise.bff.messaging.model


import com.subhrodip.squarewise.bff.realtime.GroupInvalidation

/** Successful event-processing result with the emitted reactive invalidation. */
data class ProcessedConsumptionResult(
    val invalidation: GroupInvalidation
) : ConsumptionResult
