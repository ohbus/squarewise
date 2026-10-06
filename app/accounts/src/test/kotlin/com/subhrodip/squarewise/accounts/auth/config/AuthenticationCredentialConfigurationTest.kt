package com.subhrodip.squarewise.accounts.auth.config

import java.util.Base64
import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

/** Verifies fail-closed decoding and size contracts for deployment cryptographic secrets. */
class AuthenticationCredentialConfigurationTest {

    @Test
    fun `accepts a digest secret of at least 32 bytes and an exact 32 byte envelope key`() {
        val configuration = configuration(
            digestSecret = Base64.getEncoder().encodeToString(ByteArray(32) { it.toByte() }),
            envelopeKey = Base64.getEncoder().encodeToString(ByteArray(32) { (it + 1).toByte() }),
        )

        assertDoesNotThrow { configuration.credentialDigest() }
        assertDoesNotThrow { configuration.credentialEnvelopeProtector() }
    }

    @Test
    fun `rejects malformed or undersized digest secrets`() {
        val malformed = configuration(digestSecret = "not-base64")
        val undersized = configuration(
            digestSecret = Base64.getEncoder().encodeToString(ByteArray(31)),
        )

        assertEquals(
            "Credential digest secret must be base64",
            assertThrows(IllegalArgumentException::class.java) { malformed.credentialDigest() }.message,
        )
        assertEquals(
            "Credential digest secret must contain at least 32 bytes",
            assertThrows(IllegalArgumentException::class.java) { undersized.credentialDigest() }.message,
        )
    }

    @Test
    fun `rejects malformed or non-32-byte envelope keys`() {
        val malformed = configuration(envelopeKey = "not-base64")
        val undersized = configuration(
            envelopeKey = Base64.getEncoder().encodeToString(ByteArray(31)),
        )
        val oversized = configuration(
            envelopeKey = Base64.getEncoder().encodeToString(ByteArray(33)),
        )

        assertEquals(
            "Auth email envelope key must be base64",
            assertThrows(IllegalArgumentException::class.java) { malformed.credentialEnvelopeProtector() }.message,
        )
        assertEquals(
            "Auth email envelope key must contain exactly 32 bytes",
            assertThrows(IllegalArgumentException::class.java) { undersized.credentialEnvelopeProtector() }.message,
        )
        assertEquals(
            "Auth email envelope key must contain exactly 32 bytes",
            assertThrows(IllegalArgumentException::class.java) { oversized.credentialEnvelopeProtector() }.message,
        )
    }

    @Test
    fun `rejects an unsafe login resend cooldown`() {
        assertThrows(IllegalArgumentException::class.java) {
            configuration(loginResendCooldownSeconds = 901)
        }
    }

    @Test
    fun `rejects invalid verification request bounds`() {
        assertThrows(IllegalArgumentException::class.java) {
            configuration(loginVerifyMaximumRequests = 0)
        }
        assertThrows(IllegalArgumentException::class.java) {
            configuration(loginVerifyMaximumRequests = 1_000_001)
        }
        assertThrows(IllegalArgumentException::class.java) {
            configuration(loginVerifyWindowSeconds = 0)
        }
        assertThrows(IllegalArgumentException::class.java) {
            configuration(loginVerifyWindowSeconds = 86_401)
        }
    }

    private fun configuration(
        digestSecret: String = Base64.getEncoder().encodeToString(ByteArray(32)),
        envelopeKey: String = Base64.getEncoder().encodeToString(ByteArray(32)),
        loginResendCooldownSeconds: Long = 60,
        loginVerifyMaximumRequests: Int = 5,
        loginVerifyWindowSeconds: Long = 300,
    ): AuthenticationCredentialConfiguration =
        AuthenticationCredentialConfiguration(
            digestSecret,
            envelopeKey,
            loginResendCooldownSeconds,
            loginVerifyMaximumRequests,
            loginVerifyWindowSeconds,
        )
}
