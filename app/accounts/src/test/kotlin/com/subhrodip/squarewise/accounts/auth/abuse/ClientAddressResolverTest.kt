package com.subhrodip.squarewise.accounts.auth.abuse

import java.net.InetAddress
import jakarta.servlet.http.HttpServletRequest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Test
import org.springframework.mock.web.MockHttpServletRequest
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock

/**
 * Verifies [ClientAddressResolver] client IP extraction and network partition derivation.
 *
 * SEC-007 acceptance matrix:
 * - Direct connection: raw socket address used regardless of forwarded headers.
 * - Trusted proxy: leftmost non-proxy IP from X-Forwarded-For is used.
 * - Trusted proxy with X-Real-IP fallback: real IP used when forwarded header is absent.
 * - Untrusted proxy: raw socket address used even when forwarded headers are present.
 * - Spoofed header from direct connection: raw socket address overrides header.
 * - IPv4 normalization: /24 subnet (three octets).
 * - IPv6 normalization: /48 prefix (three groups).
 * - Multi-hop proxy chain: leftmost untrusted IP chosen.
 * - No collision between unrelated /16 subnets that share the same 2-octet prefix.
 */
class ClientAddressResolverTest {

    private val loopback = InetAddress.getByName("127.0.0.1")
    private val trustedProxy = InetAddress.getByName("10.0.0.1")
    private val resolver = ClientAddressResolver(setOf(trustedProxy, loopback))
    private val noTrustResolver = ClientAddressResolver()

    // ---------------------------------------------------------------------------
    // Direct connections: no trusted proxies configured
    // ---------------------------------------------------------------------------

    @Test
    fun `direct connection without trusted proxies uses raw remote address`() {
        val request = MockHttpServletRequest()
        request.remoteAddr = "203.0.113.42"
        request.addHeader("X-Forwarded-For", "1.2.3.4")

        val partition = noTrustResolver.resolvePartition(request)
        assertEquals("203.0.113.42".toIpv4Prefix(), partition)
    }

    // ---------------------------------------------------------------------------
    // Trusted proxy: forwarded header resolution
    // ---------------------------------------------------------------------------

    @Test
    fun `connection from trusted proxy uses X-Forwarded-For leftmost non-proxy IP`() {
        val request = MockHttpServletRequest()
        request.remoteAddr = "10.0.0.1"
        request.addHeader("X-Forwarded-For", "203.0.113.55, 10.0.0.1")

        val partition = resolver.resolvePartition(request)
        assertEquals("203.0.113.55".toIpv4Prefix(), partition)
    }

    @Test
    fun `connection from trusted proxy uses X-Real-IP when X-Forwarded-For is absent`() {
        val request = MockHttpServletRequest()
        request.remoteAddr = "10.0.0.1"
        request.addHeader("X-Real-IP", "198.51.100.7")

        val partition = resolver.resolvePartition(request)
        assertEquals("198.51.100.7".toIpv4Prefix(), partition)
    }

    @Test
    fun `connection from untrusted remote address ignores forwarded headers`() {
        val request = MockHttpServletRequest()
        request.remoteAddr = "172.20.5.99"  // not in trusted set
        request.addHeader("X-Forwarded-For", "1.2.3.4")

        val partition = resolver.resolvePartition(request)
        // Must use 172.20.5.99, NOT 1.2.3.4
        assertEquals("172.20.5".toNormalizedPartition(), partition)
        assertNotEquals("1.2.3".toNormalizedPartition(), partition)
    }

    // ---------------------------------------------------------------------------
    // Header spoofing prevention
    // ---------------------------------------------------------------------------

    @Test
    fun `spoofed X-Forwarded-For from direct connection is ignored`() {
        val request = MockHttpServletRequest()
        request.remoteAddr = "203.0.113.42"  // direct client, not in trusted set
        request.addHeader("X-Forwarded-For", "192.0.2.1, 10.0.0.1")

        val partition = noTrustResolver.resolvePartition(request)
        assertEquals("203.0.113.42".toIpv4Prefix(), partition)
        assertNotEquals("192.0.2".toNormalizedPartition(), partition)
    }

    // ---------------------------------------------------------------------------
    // IPv4 /24 subnet normalization
    // ---------------------------------------------------------------------------

    @Test
    fun `IPv4 partition uses first three octets (slash-24)`() {
        val request = MockHttpServletRequest()
        request.remoteAddr = "10.20.30.40"
        val partition = noTrustResolver.resolvePartition(request)
        assertEquals("10.20.30", partition)
    }

    @Test
    fun `addresses in same slash-24 produce same partition`() {
        val req1 = MockHttpServletRequest().apply { remoteAddr = "10.20.30.1" }
        val req2 = MockHttpServletRequest().apply { remoteAddr = "10.20.30.254" }
        assertEquals(noTrustResolver.resolvePartition(req1), noTrustResolver.resolvePartition(req2))
    }

    @Test
    fun `addresses in different slash-24 subnets do NOT collide`() {
        val req1 = MockHttpServletRequest().apply { remoteAddr = "10.20.30.1" }
        val req2 = MockHttpServletRequest().apply { remoteAddr = "10.20.31.1" }
        assertNotEquals(noTrustResolver.resolvePartition(req1), noTrustResolver.resolvePartition(req2))
    }

    @Test
    fun `unrelated slash-16 subnets that share first two octets do not collide`() {
        // This test documents that the old two-octet truncation produced identical
        // partitions for 10.20.*.* and 10.20.*.*, which could rate-limit unrelated
        // users at different /24 subnets. The /24 normalization distinguishes them.
        val reqA = MockHttpServletRequest().apply { remoteAddr = "10.20.30.5" }
        val reqB = MockHttpServletRequest().apply { remoteAddr = "10.20.99.5" }
        assertNotEquals(noTrustResolver.resolvePartition(reqA), noTrustResolver.resolvePartition(reqB))
    }

    // ---------------------------------------------------------------------------
    // IPv6 /48 prefix normalization
    // ---------------------------------------------------------------------------

    @Test
    fun `IPv6 partition uses first three groups (slash-48 prefix)`() {
        val request = MockHttpServletRequest()
        request.remoteAddr = "2001:db8:0:1:2:3:4:5"
        val partition = noTrustResolver.resolvePartition(request)
        // Expected: first three 16-bit groups expanded
        assertEquals("2001:0db8:0000", partition)
    }

    @Test
    fun `loopback IPv6 address produces a valid partition`() {
        val request = MockHttpServletRequest()
        request.remoteAddr = "::1"
        val partition = noTrustResolver.resolvePartition(request)
        // ::1 expands to 0000:0000:0000:...
        assertEquals("0000:0000:0000", partition)
    }

    // ---------------------------------------------------------------------------
    // Multi-hop proxy chain
    // ---------------------------------------------------------------------------

    @Test
    fun `multi-hop proxy chain selects leftmost non-trusted address`() {
        val request = MockHttpServletRequest()
        request.remoteAddr = "10.0.0.1"  // trusted
        // Chain: client → internal-router → trusted-proxy
        request.addHeader("X-Forwarded-For", "198.51.100.10, 10.0.0.2, 10.0.0.1")

        val partition = resolver.resolvePartition(request)
        assertEquals("198.51.100.10".toIpv4Prefix(), partition)
    }

    // ---------------------------------------------------------------------------
    // Unknown / null addresses
    // ---------------------------------------------------------------------------

    @Test
    fun `unresolvable address falls back to 'unknown' partition`() {
        val request = MockHttpServletRequest()
        request.remoteAddr = "not-an-ip"
        val partition = noTrustResolver.resolvePartition(request)
        assertEquals("unknown", partition)
    }

    @Test
    fun `missing remote address falls back without consulting headers`() {
        val request = mock(HttpServletRequest::class.java)
        `when`(request.remoteAddr).thenReturn(null)

        assertEquals("unknown", noTrustResolver.resolvePartition(request))
    }

    @Test
    fun `trusted proxy ignores invalid forwarded candidates and uses real ip`() {
        val request = MockHttpServletRequest()
        request.remoteAddr = "10.0.0.1"
        request.addHeader("X-Forwarded-For", "not-an-ip, 10.0.0.1")
        request.addHeader("X-Real-IP", "198.51.100.22")

        assertEquals("198.51.100.22".toIpv4Prefix(), resolver.resolvePartition(request))
    }

    @Test
    fun `trusted proxy falls back to proxy when all forwarded addresses are trusted`() {
        val request = MockHttpServletRequest()
        request.remoteAddr = "10.0.0.1"
        request.addHeader("X-Forwarded-For", "10.0.0.1, 127.0.0.1")

        assertEquals("10.0.0.1".toIpv4Prefix(), resolver.resolvePartition(request))
    }

    @Test
    fun `trusted proxy with invalid socket address uses the raw address`() {
        val request = MockHttpServletRequest()
        request.remoteAddr = "not-an-ip"
        request.addHeader("X-Forwarded-For", "203.0.113.44")

        assertEquals("unknown", resolver.resolvePartition(request))
    }

    @Test
    fun `blank forwarded headers are ignored before proxy fallback`() {
        val request = MockHttpServletRequest()
        request.remoteAddr = "10.0.0.1"
        request.addHeader("X-Forwarded-For", " ")
        request.addHeader("X-Real-IP", " ")

        assertEquals("10.0.0.1".toIpv4Prefix(), resolver.resolvePartition(request))
    }

    // ---------------------------------------------------------------------------
    // fromProperties factory
    // ---------------------------------------------------------------------------

    @Test
    fun `fromProperties creates resolver with parsed trusted proxy set`() {
        val props = TrustedProxyProperties(addresses = listOf("127.0.0.1", "10.0.0.1"))
        val r = ClientAddressResolver.fromProperties(props)
        val request = MockHttpServletRequest()
        request.remoteAddr = "127.0.0.1"
        request.addHeader("X-Forwarded-For", "203.0.113.99")
        val partition = r.resolvePartition(request)
        assertEquals("203.0.113.99".toIpv4Prefix(), partition)
    }

    @Test
    fun `fromProperties with empty list creates no-trust resolver`() {
        val props = TrustedProxyProperties(addresses = emptyList())
        val r = ClientAddressResolver.fromProperties(props)
        val request = MockHttpServletRequest()
        request.remoteAddr = "203.0.113.1"
        request.addHeader("X-Forwarded-For", "1.2.3.4")
        val partition = r.resolvePartition(request)
        assertEquals("203.0.113.1".toIpv4Prefix(), partition)
    }

    @Test
    fun `fromProperties skips invalid proxy addresses`() {
        val resolver = ClientAddressResolver.fromProperties(
            TrustedProxyProperties(addresses = listOf("not-an-ip", "10.0.0.1"))
        )
        val request = MockHttpServletRequest()
        request.remoteAddr = "10.0.0.1"
        request.addHeader("X-Forwarded-For", "203.0.113.33")

        assertEquals("203.0.113.33".toIpv4Prefix(), resolver.resolvePartition(request))
    }

    // ---------------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------------

    /** Returns the /24 (three-octet) prefix of a dot-notation IPv4 address. */
    private fun String.toIpv4Prefix(): String = split(".").take(3).joinToString(".")

    /** Alias for clarity in assertion messages. */
    private fun String.toNormalizedPartition(): String = this
}
