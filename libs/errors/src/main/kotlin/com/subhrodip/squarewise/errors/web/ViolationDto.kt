package com.subhrodip.squarewise.errors.web

/** Bounded validation violation serialized inside a Problem Details response. */
data class ViolationDto(
    val field: String,
    val message: String,
)
