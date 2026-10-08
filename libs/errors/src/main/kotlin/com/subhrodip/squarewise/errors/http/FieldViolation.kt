package com.subhrodip.squarewise.errors.http

data class FieldViolation(
    val field: String,
    val message: String,
    val messageKey: String? = null,
    val rejectedValue: Any? = null,
)
