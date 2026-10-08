package com.subhrodip.squarewise.accounts.auth.credential

import org.junit.jupiter.api.Assertions.assertEquals

import java.time.Duration
import java.time.Instant
import java.util.Base64
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** Verifies entropy, hashing, expiry, and bounded policy for one-time credentials. */
class OneTimeCredentialIssuerTest {
    private val now: Instant = Instant.parse("2026-09-20T00:00:00Z")
    private val digest = HmacCredentialDigest(ByteArray(32) { it.toByte() })
    private val issuer = OneTimeCredentialIssuer(digest)

    @Test
    fun `issues distinct high entropy delivery values and digest metadata`() {
        val first = issuer.issue(now, Duration.ofMinutes(10), 5)
        val second = issuer.issue(now, Duration.ofMinutes(10), 5)

        assertNotEquals(first.plaintext, second.plaintext)
        assertTrue(Base64.getUrlDecoder().decode(first.plaintext).size >= 32)
        assertArrayEquals(digest.digest(first.plaintext), first.digest)
        assertNotEquals(first.plaintext, String(first.digest))
        assertTrue(first.expiresAt.isAfter(first.issuedAt))
    }

    @Test
    fun `rejects unsafe lifetime and attempt policy`() {
        assertThrows(IllegalArgumentException::class.java) {
            issuer.issue(now, Duration.ZERO, 5)
        }
        assertThrows(IllegalArgumentException::class.java) {
            issuer.issue(now, Duration.ofMinutes(-1), 5)
        }
        assertThrows(IllegalArgumentException::class.java) {
            issuer.issue(now, Duration.ofMinutes(16), 5)
        }
        assertThrows(IllegalArgumentException::class.java) {
            issuer.issue(now, Duration.ofMinutes(10), 0)
        }
        assertThrows(IllegalArgumentException::class.java) {
            issuer.issue(now, Duration.ofMinutes(10), 11)
        }
    }

    @Test
    fun `verifies IssuedCredential value equality and hashCode with byte arrays`() {
        val cred1 = OneTimeCredentialIssuer.IssuedCredential(
            plaintext = "abc",
            digest = byteArrayOf(1, 2, 3),
            issuedAt = now,
            expiresAt = now.plusSeconds(300),
            remainingAttempts = 3
        )
        val cred2 = OneTimeCredentialIssuer.IssuedCredential(
            plaintext = "abc",
            digest = byteArrayOf(1, 2, 3),
            issuedAt = now,
            expiresAt = now.plusSeconds(300),
            remainingAttempts = 3
        )
        val cred3 = OneTimeCredentialIssuer.IssuedCredential(
            plaintext = "abc",
            digest = byteArrayOf(1, 2, 4),
            issuedAt = now,
            expiresAt = now.plusSeconds(300),
            remainingAttempts = 3
        )

        assertEquals(cred1, cred2)
        assertEquals(cred1.hashCode(), cred2.hashCode())
        assertNotEquals(cred1, cred3)
        assertTrue(cred1.toString().contains("[REDACTED]"))
    }

    @Test
    fun `equality rejects every differing field null and unrelated types`() {
        val base = OneTimeCredentialIssuer.IssuedCredential(
            plaintext = "abc",
            digest = byteArrayOf(1, 2, 3),
            issuedAt = now,
            expiresAt = now.plusSeconds(300),
            remainingAttempts = 3,
        )

        assertEquals(base, base)
        assertNotEquals(base, base.copy(plaintext = "different"))
        assertNotEquals(base, base.copy(digest = byteArrayOf(1, 2, 4)))
        assertNotEquals(base, base.copy(issuedAt = now.plusSeconds(1)))
        assertNotEquals(base, base.copy(expiresAt = now.plusSeconds(301)))
        assertNotEquals(base, base.copy(remainingAttempts = 2))
        assertNotEquals(base, null)
        assertNotEquals(base, "not-an-issued-credential")
    }

    @Test
    fun `rejects weak digest secret`() {
        assertThrows(IllegalArgumentException::class.java) { HmacCredentialDigest(ByteArray(31)) }
    }
}
