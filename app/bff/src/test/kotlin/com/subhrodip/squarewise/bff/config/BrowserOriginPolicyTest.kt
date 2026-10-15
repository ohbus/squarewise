package com.subhrodip.squarewise.bff.config

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** Verifies exact-origin browser policy behavior without a network dependency. */
class BrowserOriginPolicyTest {
    private val policy = BrowserOriginPolicy(listOf("https://app.example.test", "http://localhost:3000"))

    @Test
    fun `accepts native requests without an origin`() {
        assertTrue(policy.allows(null))
    }

    @Test
    fun `accepts only exact configured origins`() {
        assertTrue(policy.allows("https://app.example.test"))
        assertTrue(policy.allows("https://app.example.test:443"))
        assertTrue(policy.allows("HTTP://LOCALHOST:3000"))
        assertFalse(policy.allows("https://evil.example.test"))
        assertFalse(policy.allows("https://app.example.test/path"))
        assertFalse(policy.allows("ws://app.example.test"))
    }

    @Test
    fun `rejects wildcard configuration`() {
        assertThrows(IllegalArgumentException::class.java) {
            BrowserOriginPolicy(listOf("*"))
        }
        assertThrows(IllegalArgumentException::class.java) {
            BrowserOriginPolicy(listOf(" * "))
        }
        assertThrows(IllegalArgumentException::class.java) {
            BrowserOriginPolicy(listOf("https://app.example.test", "*"))
        }
    }

    /** Verifies deployment fails fast when a configured origin is not an absolute origin. */
    @Test
    fun `rejects malformed configured origins`() {
        assertThrows(IllegalArgumentException::class.java) {
            BrowserOriginPolicy(listOf("/relative"))
        }
    }

    @Test
    fun `rejects malformed origins and preserves explicit non-default ports`() {
        assertTrue(BrowserOriginPolicy(listOf("https://app.example.test:8443")).allows("https://app.example.test:8443"))
        listOf(
            "not-an-origin",
            "http://[",
            "https:relative",
            "http:///path",
            "/relative",
            "ftp://app.example.test",
            "https://user:password@app.example.test",
            "https://app.example.test?query=1",
            "https://app.example.test#fragment",
            "https://app.example.test/path",
            "https://",
        ).forEach { origin ->
            assertFalse(policy.allows(origin), "Origin must be rejected: $origin")
        }
    }

    @Test
    fun `normalizes the default HTTP port`() {
        val defaultHttp = BrowserOriginPolicy(listOf("http://localhost"))

        assertTrue(defaultHttp.allows("http://localhost:80"))
        assertTrue(defaultHttp.configuredOrigins().contains("http://localhost"))
    }

    /** Verifies a native-only configuration remains valid while rejecting every supplied origin. */
    @Test
    fun `allows an empty native-only origin configuration`() {
        val nativeOnly = BrowserOriginPolicy(emptyList())

        assertTrue(nativeOnly.configuredOrigins().isEmpty())
        assertTrue(nativeOnly.allows(null))
        assertFalse(nativeOnly.allows("https://app.example.test"))
    }
}
