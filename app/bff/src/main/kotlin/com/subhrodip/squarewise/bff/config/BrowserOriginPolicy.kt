package com.subhrodip.squarewise.bff.config

import java.net.URI
import com.subhrodip.squarewise.bff.errors.BffInputException

/**
 * Exact-origin policy for browser GraphQL requests.
 *
 * A missing Origin header is accepted for native clients and non-browser tools.
 * A supplied origin must be an exact configured scheme/host/port value; wildcard
 * origins, paths, credentials, and malformed values are never accepted.
 */
class BrowserOriginPolicy(allowedOrigins: List<String>) {
    private val allowed: Set<String> = allowedOrigins.map(::canonicalOrigin).toSet()

    init {
        require(allowedOrigins.none { it.trim() == "*" }) {
            "Browser origins must be explicit; wildcard origins are forbidden"
        }
    }

    /** Returns whether the request origin is absent or exactly allow-listed. */
    fun allows(origin: String?): Boolean = origin == null || canonicalOriginOrNull(origin)?.let(allowed::contains) == true

    /** Returns the canonical configured origin values for response headers. */
    fun configuredOrigins(): Set<String> = allowed

    private fun canonicalOrigin(value: String): String = canonicalOriginOrNull(value)
        ?: throw BffInputException("Browser origin must be an absolute origin without a path")

    private fun canonicalOriginOrNull(value: String): String? {
        val uri = runCatching { URI(value.trim()) }.getOrNull() ?: return null
        val scheme = uri.scheme?.lowercase() ?: return null
        val host = uri.host?.lowercase() ?: return null
        if (scheme != "http" && scheme != "https") return null
        if (uri.userInfo != null || uri.rawPath.isNotEmpty() || uri.rawQuery != null || uri.rawFragment != null) return null
        val port = when {
            uri.port < 0 -> ""
            scheme == "http" && uri.port == 80 -> ""
            scheme == "https" && uri.port == 443 -> ""
            else -> ":${uri.port}"
        }
        return "$scheme://$host$port"
    }
}
