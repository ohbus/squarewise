package com.subhrodip.squarewise.accounts.auth.abuse

import jakarta.servlet.http.HttpServletRequest
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress

/**
 * Resolves the canonical client network address for rate-limit partitioning.
 *
 * SEC-007: Replaces the previous two-octet IPv4 truncation heuristic with a
 * trusted-proxy-aware resolver that:
 * - Only consults forwarded headers (`X-Forwarded-For`, `X-Real-IP`) when the
 *   immediate remote socket address matches a configured trusted proxy.
 * - Falls back to the raw socket `remoteAddr` for direct or untrusted connections,
 *   preventing header spoofing from arbitrary clients.
 * - Normalizes IPv4 addresses to /24 subnets and IPv6 addresses to /48 prefixes,
 *   providing coarse abuse partitioning without accidental collisions across unrelated
 *   address blocks.
 *
 * The returned partition string is always non-empty and safe to pass to
 * [LoginRateLimitKeyDeriver] and [RefreshRateLimitService].
 *
 * @param trustedProxies the set of trusted proxy [InetAddress] values derived from
 *   [TrustedProxyProperties]; may be empty (all connections treated as direct).
 */
class ClientAddressResolver(
    private val trustedProxies: Set<InetAddress> = emptySet()
) {
    /**
     * Derives a coarse, normalized network partition string from the incoming request.
     *
     * @param request the HTTP servlet request
     * @return a non-empty string suitable for rate-limit key derivation
     */
    fun resolvePartition(request: HttpServletRequest): String {
        val remoteAddr = request.remoteAddr?.trim() ?: return FALLBACK_PARTITION
        val clientAddress = resolveClientAddress(remoteAddr, request)
        return normalizeToPartition(clientAddress)
    }

    /**
     * Determines the true client address.
     *
     * If [remoteAddr] belongs to a trusted proxy, the leftmost non-trusted IP in
     * `X-Forwarded-For` (or the value of `X-Real-IP`) is used as the client address.
     * Otherwise, [remoteAddr] is used directly.
     *
     * @param remoteAddr the raw socket remote address
     * @param request the HTTP servlet request
     */
    private fun resolveClientAddress(remoteAddr: String, request: HttpServletRequest): String {
        if (trustedProxies.isEmpty()) {
            return remoteAddr
        }
        val remoteInet = parseAddress(remoteAddr) ?: return remoteAddr
        if (remoteInet !in trustedProxies) {
            return remoteAddr
        }
        // Try X-Forwarded-For first: take the leftmost non-trusted-proxy address.
        val forwarded = request.getHeader(HEADER_X_FORWARDED_FOR)
        if (!forwarded.isNullOrBlank()) {
            val candidate = forwarded.split(",")
                .map { it.trim() }
                .firstOrNull { addr ->
                    val parsed = parseAddress(addr)
                    parsed != null && parsed !in trustedProxies
                }
            if (candidate != null) return candidate
        }
        // Fall back to X-Real-IP if set by the trusted proxy.
        val realIp = request.getHeader(HEADER_X_REAL_IP)?.trim()
        if (!realIp.isNullOrBlank()) {
            return realIp
        }
        // The trusted proxy did not supply a client address; use the proxy's address
        // rather than accepting a potentially spoofed header value.
        return remoteAddr
    }

    /**
     * Normalizes an address string to a coarse network partition.
     *
     * - IPv4: retains the first three octets (i.e., `/24` subnet).
     * - IPv6: retains the first three 16-bit groups (i.e., `/48` prefix).
     * - Unresolvable: returns [FALLBACK_PARTITION].
     */
    private fun normalizeToPartition(address: String): String {
        return when (val inet = parseAddress(address)) {
            is Inet4Address -> {
                val parts = inet.hostAddress.split(".")
                "${parts[0]}.${parts[1]}.${parts[2]}"
            }
            is Inet6Address -> {
                val bytes = inet.address
                (0 until 3).joinToString(":") { i ->
                    val high = bytes[i * 2].toInt() and 0xFF
                    val low = bytes[i * 2 + 1].toInt() and 0xFF
                    "%02x%02x".format(high, low)
                }
            }
            else -> FALLBACK_PARTITION
        }
    }

    /** Parses an IP address string into an [InetAddress], returning null on failure. */
    private fun parseAddress(address: String): InetAddress? = runCatching {
        InetAddress.getByName(address.trim())
    }.getOrNull()

    companion object {
        private const val FALLBACK_PARTITION = "unknown"
        private const val HEADER_X_FORWARDED_FOR = "X-Forwarded-For"
        private const val HEADER_X_REAL_IP = "X-Real-IP"

        /**
         * Parses the [TrustedProxyProperties.addresses] list into resolved [InetAddress] set.
         * Unresolvable addresses are silently skipped so deployment errors cannot cause a startup
         * failure that blocks the entire service.
         */
        fun fromProperties(properties: TrustedProxyProperties): ClientAddressResolver {
            val resolved = properties.addresses.mapNotNull { addr ->
                runCatching { InetAddress.getByName(addr.trim()) }.getOrNull()
            }.toSet()
            return ClientAddressResolver(resolved)
        }
    }
}
