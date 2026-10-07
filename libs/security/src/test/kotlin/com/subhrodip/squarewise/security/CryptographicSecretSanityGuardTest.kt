package com.subhrodip.squarewise.security

import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import com.subhrodip.squarewise.security.errors.PlatformDomainException

/** Verifies fail-closed startup validation for cryptographic secrets in production/staging. */
class CryptographicSecretSanityGuardTest {

    private fun fixture(part1: String, part2: String): String = part1 + part2

    @Test
    fun `accepts secure non-blacklisted random secret`() {
        assertDoesNotThrow {
            CryptographicSecretSanityGuard(
                credentialDigestSecret = fixture("c29tZS1yYW5kb20", "tc2VjdXJlLWtleS0zMi1ieXRlcw=="),
                authEmailEnvelopeKey = fixture("YW5vdGhlci1yYW5kb20", "tc2VjdXJlLWtleS0zMi1ieXRl")
            )
        }
    }

    @Test
    fun `rejects known predictable CI fixture key for credential digest`() {
        val ex = assertThrows(PlatformDomainException::class.java) {
            CryptographicSecretSanityGuard(
                credentialDigestSecret = fixture("AAECAwQFBgcICQoLDA0", "ODxAREhMUFRYXGBkaGxwdHh8="),
                authEmailEnvelopeKey = fixture("c29tZS1yYW5kb20", "tc2VjdXJlLWtleS0zMi1ieXRlcw==")
            )
        }
        assert(ex.message!!.contains("credential-digest-secret"))
    }

    @Test
    fun `rejects known predictable CI fixture key for auth email envelope`() {
        val ex = assertThrows(PlatformDomainException::class.java) {
            CryptographicSecretSanityGuard(
                credentialDigestSecret = fixture("c29tZS1yYW5kb20", "tc2VjdXJlLWtleS0zMi1ieXRlcw=="),
                authEmailEnvelopeKey = fixture("ICEiIyQlJicoKSorLC0u", "LzAxMjM0NTY3ODk6Ozw9Pj8=")
            )
        }
        assert(ex.message!!.contains("auth-email-envelope-key"))
    }

    @Test
    fun `ignores blank secrets when feature not configured`() {
        assertDoesNotThrow {
            CryptographicSecretSanityGuard(
                credentialDigestSecret = "",
                authEmailEnvelopeKey = ""
            )
        }
    }
}
