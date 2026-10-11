package com.subhrodip.squarewise.accounts.auth.abuse

import com.subhrodip.squarewise.security.ratelimit.RateLimitStoreUnavailableException as SecurityRateLimitStoreUnavailableException

/** Compatibility alias for the shared fail-closed rate-limit exception. */
typealias RateLimitStoreUnavailableException =
    SecurityRateLimitStoreUnavailableException
