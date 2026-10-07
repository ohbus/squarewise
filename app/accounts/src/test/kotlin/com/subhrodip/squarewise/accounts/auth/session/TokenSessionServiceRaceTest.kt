package com.subhrodip.squarewise.accounts.auth.session

import com.subhrodip.squarewise.accounts.auth.credential.HmacCredentialDigest
import com.subhrodip.squarewise.accounts.auth.identity.AccountIdentity
import com.subhrodip.squarewise.accounts.auth.identity.AccountIdentityStore
import com.subhrodip.squarewise.accounts.auth.provider.IdentityProviderPort
import com.subhrodip.squarewise.errors.exceptions.SquarewiseException
import java.time.Duration
import java.time.Instant
import java.util.UUID
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoInteractions

/** Verifies the refresh-rotation compare-and-set race contract in isolation. */
class TokenSessionServiceRaceTest {
    private val repository = mock(AuthSessionRepository::class.java)
    private val tokenProvider = mock(IdentityProviderPort::class.java)
    private val digest = HmacCredentialDigest(ByteArray(32) { it.toByte() })
    private val accountId = UUID.randomUUID()
    private val subject = "internal:racing@example.com"
    private val identityStore = object : AccountIdentityStore {
        override fun findByAccountId(accountId: UUID): AccountIdentity =
            AccountIdentity(accountId, subject, "racing@example.com", deletionRequested = false)
    }
    private val service = TokenSessionService(
        sessionRepository = repository,
        identityProviderPort = tokenProvider,
        credentialDigest = digest,
        sessionPolicy = SessionPolicy(
            accessTokenLifetime = Duration.ofMinutes(10),
            refreshIdleLifetime = Duration.ofDays(30),
            absoluteSessionLifetime = Duration.ofDays(90),
        ),
        accountIdentityStore = identityStore,
    )

    @Test
    fun `rotation race revokes the family and fails closed before minting a token`() {
        val now = Instant.parse("2026-01-01T00:00:00Z")
        val rawRefreshToken = "race-refresh-token" // security-hygiene: test-fixture
        val session = AuthSessionEntity(
            sessionId = UUID.randomUUID(),
            accountId = accountId,
            subject = subject,
            familyId = UUID.randomUUID(),
            refreshTokenDigest = digest.digest(rawRefreshToken),
            createdAt = now,
            lastUsedAt = now,
            expiresAt = now.plusSeconds(300),
            absoluteExpiresAt = now.plusSeconds(600),
            clientKind = "BROWSER",
        )
        `when`(repository.findByRefreshTokenDigest(session.refreshTokenDigest)).thenReturn(session)
        `when`(
            repository.rotateIfActive(
                any(UUID::class.java) ?: UUID(0, 0),
                any(UUID::class.java) ?: UUID(0, 0),
                any(Instant::class.java) ?: now,
            )
        ).thenReturn(0)

        val exception = assertThrows(SquarewiseException::class.java) {
            service.rotateSession(rawRefreshToken, "browser", now.plusSeconds(1))
        }

        assertEquals("UNAUTHENTICATED", exception.definition.legacyCode)
        verify(repository).revokeFamily(session.familyId, now.plusSeconds(1))
        verifyNoInteractions(tokenProvider)
    }
}
