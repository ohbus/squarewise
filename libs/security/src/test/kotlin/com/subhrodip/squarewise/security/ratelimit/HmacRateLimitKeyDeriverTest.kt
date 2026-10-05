package com.subhrodip.squarewise.security.ratelimit

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals

/** Verifies opaque, deployment-scoped rate-limit key derivation. */
class HmacRateLimitKeyDeriverTest {
    private val secret = "AAECAwQFBgcICQoLDA0ODxAREhMUFRYXGBkaGxwdHh8="

    @Test
    fun `same canonical key is stable and different keys are separated`() {
        val deriver = HmacRateLimitKeyDeriver.fromBase64(secret)

        assertEquals(deriver.derive("email|example.test"), deriver.derive("email|example.test"))
        assertNotEquals(deriver.derive("email|example.test"), deriver.derive("email|other.test"))
        assertNotEquals("email|example.test", deriver.derive("email|example.test"))
    }

    @Test
    fun `short or malformed deployment secrets fail closed`() {
        assertThrows<IllegalArgumentException> { HmacRateLimitKeyDeriver.fromBase64("not-base64") }
        assertThrows<IllegalArgumentException> {
            HmacRateLimitKeyDeriver.fromBase64("AQIDBAUGBwgJCg==")
        }
    }
}
