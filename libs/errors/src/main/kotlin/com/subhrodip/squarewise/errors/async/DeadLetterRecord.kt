package com.subhrodip.squarewise.errors.async

import java.time.Instant
import java.util.UUID

/** Immutable structured terminal failure record matching the dead-letter contract. */
data class DeadLetterRecord(
    val originalQueue: String,
    val eventId: UUID,
    val eventType: String,
    val schemaVersion: Int,
    val failedAt: Instant,
    val attemptCount: Int,
    val numericCode: String,
    val errorName: String,
    val diagnosticReason: String,
    val messagePayloadBase64: String,
)
