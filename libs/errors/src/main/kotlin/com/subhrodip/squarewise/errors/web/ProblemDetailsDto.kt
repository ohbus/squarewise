package com.subhrodip.squarewise.errors.web

import java.net.URI
import java.time.Instant

/** Additive RFC 9457 response model preserving the legacy symbolic error code. */
data class ProblemDetailsDto(
    val type: URI,
    val title: String,
    val status: Int,
    val detail: String,
    val instance: String,
    val code: String,
    val requestId: String,
    val source: String,
    val timestamp: Instant = Instant.now(),
    val numericCode: String,
    val errorName: String,
    val violations: List<ViolationDto> = emptyList(),
)
