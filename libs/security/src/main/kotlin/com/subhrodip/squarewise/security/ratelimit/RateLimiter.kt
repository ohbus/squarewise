package com.subhrodip.squarewise.security.ratelimit

/**
 * Provider-neutral atomic admission boundary for distributed throttling.
 *
 * Implementations must consume at most one permit atomically and must fail
 * closed by throwing [RateLimitStoreUnavailableException] when a safe decision
 * cannot be made. Callers must pass a canonical, already privacy-safe key;
 * sensitive raw identifiers must never be used as metric labels or persistent
 * Redis key material.
 *
 * @param key canonical bounded key material, normally an HMAC-derived value
 * @param policy policy to apply to the key
 * @return bounded admission decision
 */
interface RateLimiter {
    fun consume(key: String, policy: RateLimitPolicy): RateLimitDecision
}
