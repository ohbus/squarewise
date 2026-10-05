package com.subhrodip.squarewise.security.ratelimit

/** Indicates that throttling could not be decided safely and must fail closed. */
class RateLimitStoreUnavailableException(cause: Throwable) :
    RuntimeException("Rate-limit store unavailable", cause)
