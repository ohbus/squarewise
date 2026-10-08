package com.subhrodip.squarewise.accounts.auth.credential

import java.time.Duration

import java.time.Instant
import java.util.UUID
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock
import org.mockito.Mockito.verifyNoInteractions

/** Verifies generic one-time credential outcomes without exposing persistence details. */
class LoginCredentialServiceTest {
    private val repository = mock(LoginCredentialRepository::class.java)
    private val digest = HmacCredentialDigest(ByteArray(32) { it.toByte() })
    private val issuer = OneTimeCredentialIssuer(digest)
    private val service = LoginCredentialService(repository, issuer)
    private val now = Instant.parse("2026-01-01T00:00:00Z")

    @Test
    fun `verify accepts an atomically redeemed credential`() {
        val issued = issuer.issue(now, Duration.ofMinutes(5), maxAttempts = 3)
        `when`(repository.consumeIfActive(issued.digest, now)).thenReturn(1)
        `when`(repository.findByCredentialDigest(issued.digest)).thenReturn(entity(issued))

        assertEquals(LoginCredentialService.VerificationOutcome.ACCEPTED, service.verify(issued.plaintext, now))
    }

    @Test
    fun `verify rejects a credential that was not atomically redeemed`() {
        val issued = issuer.issue(now, Duration.ofMinutes(5), maxAttempts = 3)
        `when`(repository.consumeIfActive(issued.digest, now)).thenReturn(0)

        assertEquals(LoginCredentialService.VerificationOutcome.REJECTED, service.verify(issued.plaintext, now))
    }

    @Test
    fun `redeem rejects blank plaintext without consulting the repository`() {
        assertNull(service.redeem(" \t", now))

        verifyNoInteractions(repository)
    }

    private fun entity(issued: OneTimeCredentialIssuer.IssuedCredential): LoginCredentialEntity =
        LoginCredentialEntity(
            credentialId = UUID.randomUUID(),
            canonicalEmail = "user@example.com",
            credentialDigest = issued.digest,
            credentialKind = LoginCredentialService.CredentialKind.CODE.name,
            issuedAt = issued.issuedAt,
            expiresAt = issued.expiresAt,
            remainingAttempts = issued.remainingAttempts,
        )
}
