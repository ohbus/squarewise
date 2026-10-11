package com.subhrodip.squarewise.accounts.auth.credential

import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

/** Verifies the credential digest boundary and secret-size guard. */
class HmacCredentialDigestTest {
    @Test
    fun `produces stable SHA-256 digests without exposing mutable key state`() {
        val secret = ByteArray(32) { it.toByte() }
        val digest = HmacCredentialDigest(secret)
        val first = digest.digest("credential")

        secret[0] = 99

        assertArrayEquals(first, digest.digest("credential"))
        assertNotEquals(first.toList(), digest.digest("different").toList())
    }

    @Test
    fun `rejects secrets shorter than the minimum size`() {
        assertThrows(IllegalArgumentException::class.java) {
            HmacCredentialDigest(ByteArray(31))
        }
    }
}
