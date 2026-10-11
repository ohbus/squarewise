package com.subhrodip.squarewise.bff.transport

import com.subhrodip.squarewise.errors.code.ErrorDefinition

/** Signals a non-success response from an upstream service. */
open class UpstreamServiceException(
    val status: Int,
    message: String = "Upstream service returned HTTP $status",
    val definition: ErrorDefinition? = null,
    cause: Throwable? = null,
) : RuntimeException(message, cause)
