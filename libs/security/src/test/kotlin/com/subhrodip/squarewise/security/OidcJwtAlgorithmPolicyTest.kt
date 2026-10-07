package com.subhrodip.squarewise.security

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.security.oauth2.jwt.Jwt
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import com.subhrodip.squarewise.security.errors.PlatformDomainException

/** Verifies the configurable asymmetric JWT algorithm policy. */
class OidcJwtAlgorithmPolicyTest {
    @Test
    fun `accepts configured algorithm`() {
        assertFalse(policy("RS256").validate(jwt("RS256")).hasErrors())
    }

    @Test
    fun `rejects unconfigured algorithm`() {
        assertTrue(policy("RS256").validate(jwt("RS512")).hasErrors())
    }

    @Test
    fun `rejects blank algorithm`() {
        assertTrue(policy("RS256").validate(jwt("")).hasErrors())
    }

    @Test
    fun `rejects symmetric algorithms in configuration`() {
        assertThrows(PlatformDomainException::class.java) { policy("HS256") }
    }

    /** Empty or whitespace-only configuration cannot silently produce an allow-list. */
    @Test
    fun `rejects empty configured algorithm set`() {
        assertThrows(PlatformDomainException::class.java) { OidcJwtAlgorithmPolicy(emptySet()) }
        assertThrows(PlatformDomainException::class.java) { OidcJwtAlgorithmPolicy(setOf(" ", "")) }
    }

    /** A token without an algorithm header is rejected fail-closed. */
    @Test
    fun `rejects token with missing algorithm header`() {
        val token = mock(Jwt::class.java)
        `when`(token.headers).thenReturn(emptyMap())
        assertTrue(policy("RS256").validate(token).hasErrors())
    }

    private fun policy(algorithm: String) = OidcJwtAlgorithmPolicy(setOf(algorithm))

    private fun jwt(algorithm: String?) = Jwt.withTokenValue("token")
        .apply { if (algorithm != null) header("alg", algorithm) }
        .claim("sub", "subject")
        .build()
}
