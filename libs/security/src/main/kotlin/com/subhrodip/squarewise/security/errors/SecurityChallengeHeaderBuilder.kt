package com.subhrodip.squarewise.security.errors

/** Builds the stable RFC 6750 bearer challenge used by all security adapters. */
object SecurityChallengeHeaderBuilder {
    /** Return a challenge that does not disclose token validation internals. */
    fun invalidBearerToken(): String = "Bearer error=\"invalid_token\""
}
