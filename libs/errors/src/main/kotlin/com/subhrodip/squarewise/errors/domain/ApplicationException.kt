package com.subhrodip.squarewise.errors.domain

/**
 * Custom exception representing application-level errors with an associated [ErrorCode].
 * It is intended to be handled by GlobalErrorHandler to produce standardized API problem responses.
 */
open class ApplicationException(
    val errorCode: ErrorCode,
    message: String? = null,
    cause: Throwable? = null
) : RuntimeException(message ?: errorCode.safeDetail, cause)
