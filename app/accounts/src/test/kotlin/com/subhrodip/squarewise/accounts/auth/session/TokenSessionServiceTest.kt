package com.subhrodip.squarewise.accounts.auth.session

import com.subhrodip.squarewise.errors.code.CategoryCode
import java.time.Duration

import com.subhrodip.squarewise.accounts.auth.identity.AccountIdentity
import com.subhrodip.squarewise.accounts.auth.identity.AccountIdentityStore
import com.subhrodip.squarewise.accounts.auth.credential.HmacCredentialDigest
import com.subhrodip.squarewise.accounts.auth.provider.InternalJwtTokenProvider
import com.subhrodip.squarewise.errors.exceptions.SquarewiseException
import java.time.Instant
import java.util.UUID
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.transaction.annotation.Transactional

@SpringBootTest
@Transactional
class TokenSessionServiceTest @Autowired constructor(
    private val sessionRepository: AuthSessionRepository
) {
    private var currentSubject = "internal:test@example.com"
    private var identityAvailable = true
    private var identityDeletionRequested = false
    private val secret = ByteArray(32) { it.toByte() }
    private val digest = HmacCredentialDigest(secret)
    private val tokenProvider = InternalJwtTokenProvider(
        secretSigningKey = secret,
        issuerUri = "https://issuer.example.squarewise",
        audience = "squarewise-api",
        tokenLifetime = Duration.ofMinutes(10)
    )
    private val service = TokenSessionService(
        sessionRepository = sessionRepository,
        identityProviderPort = tokenProvider,
        credentialDigest = digest,
        sessionPolicy = SessionPolicy(
            accessTokenLifetime = Duration.ofMinutes(10),
            refreshIdleLifetime = Duration.ofDays(30),
            absoluteSessionLifetime = Duration.ofDays(90),
            clockSkew = Duration.ZERO
        ),
        accountIdentityStore = object : AccountIdentityStore {
            override fun findByAccountId(accountId: UUID): AccountIdentity? =
                if (identityAvailable) {
                    AccountIdentity(accountId, currentSubject, "test@example.com", identityDeletionRequested)
                } else {
                    null
                }
        }
    )

    @Test
    fun `creates initial session with valid tokens`() {
        val now = Instant.now()
        val accountId = UUID.randomUUID()
        val response = service.createSession(
            accountId = accountId,
            subject = currentSubject,
            email = "user@example.com",
            clientKind = "BROWSER",
            deviceLabel = "Mozilla/5.0",
            now = now
        )

        assertNotNull(response.accessToken)
        assertEquals("Bearer", response.tokenType)
        assertTrue(response.expiresIn > 0)
        assertNotNull(response.refreshToken)

        val stored = sessionRepository.findByRefreshTokenDigest(digest.digest(response.refreshToken))
        assertNotNull(stored)
        assertEquals(accountId, stored?.accountId)
        assertEquals(currentSubject, stored?.subject)
        assertEquals("BROWSER", stored?.clientKind)
    }

    @Test
    fun `rotates refresh token within the same family`() {
        val now = Instant.now()
        val accountId = UUID.randomUUID()
        val initial = service.createSession(
            accountId = accountId,
            subject = currentSubject,
            email = "rotate@example.com",
            clientKind = "BROWSER",
            deviceLabel = "test",
            now = now
        )

        val rotated = service.rotateSession(
            rawRefreshToken = initial.refreshToken,
            deviceLabel = "test-2",
            now = now.plusSeconds(10)
        )

        assertNotNull(rotated.accessToken)
        assertNotNull(rotated.refreshToken)
        assertTrue(rotated.refreshToken != initial.refreshToken)

        // Old token session is marked replaced and revoked
        val oldSession = sessionRepository.findByRefreshTokenDigest(digest.digest(initial.refreshToken))
        assertNotNull(oldSession?.revokedAt)
        assertNotNull(oldSession?.replacedBySessionId)

        // New token session belongs to same family
        val newSession = sessionRepository.findByRefreshTokenDigest(digest.digest(rotated.refreshToken))
        assertNotNull(newSession)
        assertEquals(oldSession?.familyId, newSession?.familyId)
    }

    /** Verifies rotation fails closed before lookup for blank and unknown refresh tokens. */
    @Test
    fun `rejects blank and unknown refresh tokens during rotation`() {
        val now = Instant.now()

        assertThrows(SquarewiseException::class.java) {
            service.rotateSession(" ", "test", now)
        }
        assertThrows(SquarewiseException::class.java) {
            service.rotateSession("unknown-rotate-token", "test", now)
        }
    }

    @Test
    fun `detects token reuse and revokes entire family`() {
        val now = Instant.now()
        val accountId = UUID.randomUUID()
        val initial = service.createSession(
            accountId = accountId,
            subject = currentSubject,
            email = "reuse@example.com",
            clientKind = "BROWSER",
            deviceLabel = "test",
            now = now
        )

        // Rotate once (valid)
        val rotated = service.rotateSession(
            rawRefreshToken = initial.refreshToken,
            deviceLabel = "test-2",
            now = now.plusSeconds(10)
        )

        // Presenting old token again (reuse attack)
        val ex = assertThrows(SquarewiseException::class.java) {
            service.rotateSession(
                rawRefreshToken = initial.refreshToken,
                deviceLabel = "attacker",
                now = now.plusSeconds(20)
            )
        }
        assertEquals(CategoryCode.AUTHENTICATION_ERROR, ex.definition.category)

        // The whole family, including rotated, must now be revoked
        val rotatedSession = sessionRepository.findByRefreshTokenDigest(digest.digest(rotated.refreshToken))
        assertNotNull(rotatedSession?.revokedAt)
    }

    /** Verifies a revoked-only refresh session is rejected before identity lookup. */
    @Test
    fun `rejects a revoked refresh session without replacement metadata`() {
        val now = Instant.now()
        val rawRefreshToken = UUID.randomUUID().toString()
        val familyId = UUID.randomUUID()
        sessionRepository.save(AuthSessionEntity(
            sessionId = UUID.randomUUID(),
            accountId = UUID.randomUUID(),
            familyId = familyId,
            refreshTokenDigest = digest.digest(rawRefreshToken),
            createdAt = now,
            lastUsedAt = now,
            expiresAt = now.plusSeconds(300),
            absoluteExpiresAt = now.plusSeconds(600),
            revokedAt = now,
            replacedBySessionId = null,
            subject = currentSubject,
            clientKind = "BROWSER"
        ))

        val ex = assertThrows(SquarewiseException::class.java) {
            service.rotateSession(rawRefreshToken, "test", now.plusSeconds(1))
        }

        assertEquals(CategoryCode.AUTHENTICATION_ERROR, ex.definition.category)
    }

    /** Verifies a replacement-only refresh session is treated as token reuse. */
    @Test
    fun `rejects a replaced refresh session without a revocation timestamp`() {
        val now = Instant.now()
        val rawRefreshToken = UUID.randomUUID().toString()
        val familyId = UUID.randomUUID()
        val replacementSessionId = UUID.randomUUID()
        sessionRepository.save(AuthSessionEntity(
            sessionId = replacementSessionId,
            accountId = UUID.randomUUID(),
            familyId = familyId,
            refreshTokenDigest = digest.digest(UUID.randomUUID().toString()),
            createdAt = now,
            lastUsedAt = now,
            expiresAt = now.plusSeconds(300),
            absoluteExpiresAt = now.plusSeconds(600),
            subject = currentSubject,
            clientKind = "BROWSER"
        ))
        sessionRepository.save(AuthSessionEntity(
            sessionId = UUID.randomUUID(),
            accountId = UUID.randomUUID(),
            familyId = familyId,
            refreshTokenDigest = digest.digest(rawRefreshToken),
            createdAt = now,
            lastUsedAt = now,
            expiresAt = now.plusSeconds(300),
            absoluteExpiresAt = now.plusSeconds(600),
            revokedAt = null,
            replacedBySessionId = replacementSessionId,
            subject = currentSubject,
            clientKind = "BROWSER"
        ))

        val ex = assertThrows(SquarewiseException::class.java) {
            service.rotateSession(rawRefreshToken, "test", now.plusSeconds(1))
        }

        assertEquals(CategoryCode.AUTHENTICATION_ERROR, ex.definition.category)
    }

    /** Verifies a legacy accountless refresh session fails closed without mutation. */
    @Test
    fun `rejects an accountless refresh session`() {
        val now = Instant.now()
        val rawRefreshToken = UUID.randomUUID().toString()
        val sessionId = UUID.randomUUID()
        sessionRepository.save(AuthSessionEntity(
            sessionId = sessionId,
            accountId = null,
            familyId = UUID.randomUUID(),
            refreshTokenDigest = digest.digest(rawRefreshToken),
            createdAt = now,
            lastUsedAt = now,
            expiresAt = now.plusSeconds(300),
            absoluteExpiresAt = now.plusSeconds(600),
            subject = currentSubject,
            clientKind = "NATIVE"
        ))

        val ex = assertThrows(SquarewiseException::class.java) {
            service.rotateSession(rawRefreshToken, "legacy", now.plusSeconds(1))
        }

        assertEquals(CategoryCode.AUTHENTICATION_ERROR, ex.definition.category)
        assertEquals(null, sessionRepository.findById(sessionId).orElseThrow().revokedAt)
    }

    @Test
    fun `logout does not revoke a family when authenticated subject does not own it`() {
        val now = Instant.now()
        val initial = service.createSession(
            accountId = UUID.randomUUID(),
            subject = currentSubject,
            email = "owner@example.com",
            clientKind = "BROWSER",
            deviceLabel = "test",
            now = now
        )

        service.revokeSessionByRefreshToken(
            rawRefreshToken = initial.refreshToken,
            expectedSubject = "internal:attacker@example.com",
            now = now.plusSeconds(1)
        )

        val stored = sessionRepository.findByRefreshTokenDigest(digest.digest(initial.refreshToken))
        assertEquals(null, stored?.revokedAt)
    }

    @Test
    fun `logout is idempotent for an already revoked family`() {
        val now = Instant.now()
        val initial = service.createSession(
            accountId = UUID.randomUUID(),
            subject = currentSubject,
            email = "logout@example.com",
            clientKind = "BROWSER",
            deviceLabel = "test",
            now = now
        )

        service.revokeSessionByRefreshToken(initial.refreshToken, now = now.plusSeconds(1))
        val firstRevocation = sessionRepository.findByRefreshTokenDigest(digest.digest(initial.refreshToken))?.revokedAt
        service.revokeSessionByRefreshToken(initial.refreshToken, now = now.plusSeconds(2))
        val secondRevocation = sessionRepository.findByRefreshTokenDigest(digest.digest(initial.refreshToken))?.revokedAt

        assertEquals(firstRevocation, secondRevocation)
    }

    @Test
    fun `logout with a matching authenticated subject revokes the token family`() {
        val now = Instant.now()
        val initial = service.createSession(
            accountId = UUID.randomUUID(),
            subject = currentSubject,
            email = "owned-logout@example.com",
            clientKind = "BROWSER",
            deviceLabel = "test",
            now = now
        )

        service.revokeSessionByRefreshToken(
            rawRefreshToken = initial.refreshToken,
            expectedSubject = currentSubject,
            now = now.plusSeconds(1)
        )

        assertNotNull(sessionRepository.findByRefreshTokenDigest(digest.digest(initial.refreshToken))?.revokedAt)
    }

    @Test
    fun `direct family revocation persists for every session in the family`() {
        val now = Instant.now()
        val initial = service.createSession(
            accountId = UUID.randomUUID(),
            subject = currentSubject,
            email = "family-revoke@example.com",
            clientKind = "BROWSER",
            deviceLabel = "test",
            now = now,
        )
        val rotated = service.rotateSession(
            rawRefreshToken = initial.refreshToken,
            deviceLabel = "test-2",
            now = now.plusSeconds(1),
        )
        val familyId = sessionRepository
            .findByRefreshTokenDigest(digest.digest(rotated.refreshToken))
            ?.familyId

        requireNotNull(familyId)
        service.revokeFamily(familyId, now.plusSeconds(2))

        assertNotNull(sessionRepository.findByRefreshTokenDigest(digest.digest(initial.refreshToken))?.revokedAt)
        assertNotNull(sessionRepository.findByRefreshTokenDigest(digest.digest(rotated.refreshToken))?.revokedAt)
    }

    @Test
    fun `logout ignores blank and unknown refresh tokens`() {
        service.revokeSessionByRefreshToken("   ", now = Instant.now())
        service.revokeSessionByRefreshToken("unknown-refresh-token", now = Instant.now())
    }

    @Test
    fun `logout does not revoke a family when its identity is missing`() {
        val now = Instant.now()
        val initial = service.createSession(
            accountId = UUID.randomUUID(),
            subject = currentSubject,
            email = "missing-logout-identity@example.com",
            clientKind = "NATIVE",
            deviceLabel = "test",
            now = now
        )
        identityAvailable = false

        service.revokeSessionByRefreshToken(
            rawRefreshToken = initial.refreshToken,
            expectedSubject = currentSubject,
            now = now.plusSeconds(1)
        )

        assertEquals(null, sessionRepository.findByRefreshTokenDigest(digest.digest(initial.refreshToken))?.revokedAt)
    }

    @Test
    fun `logout with an authenticated subject ignores a legacy session without an account`() {
        val now = Instant.now()
        val rawRefreshToken = "legacy-accountless-logout" // security-hygiene: test-fixture
        val session = AuthSessionEntity(
            sessionId = UUID.randomUUID(),
            accountId = null,
            familyId = UUID.randomUUID(),
            refreshTokenDigest = digest.digest(rawRefreshToken),
            createdAt = now,
            lastUsedAt = now,
            expiresAt = now.plusSeconds(300),
            absoluteExpiresAt = now.plusSeconds(600),
            subject = null,
            clientKind = "NATIVE"
        )
        sessionRepository.save(session)

        service.revokeSessionByRefreshToken(
            rawRefreshToken = rawRefreshToken,
            expectedSubject = currentSubject,
            now = now.plusSeconds(1)
        )

        assertEquals(null, sessionRepository.findByRefreshTokenDigest(digest.digest(rawRefreshToken))?.revokedAt)
    }

    @Test
    fun `provider subject change revokes the existing session family`() {
        val now = Instant.now()
        val accountId = UUID.randomUUID()
        val initial = service.createSession(
            accountId = accountId,
            subject = currentSubject,
            email = "test@example.com",
            clientKind = "NATIVE",
            deviceLabel = "test",
            now = now
        )
        currentSubject = "internal:remapped@example.com"

        val ex = assertThrows(SquarewiseException::class.java) {
            service.rotateSession(initial.refreshToken, "test", now.plusSeconds(1))
        }

        assertEquals(CategoryCode.AUTHENTICATION_ERROR, ex.definition.category)
        val stored = sessionRepository.findByRefreshTokenDigest(digest.digest(initial.refreshToken))
        assertNotNull(stored?.revokedAt)
    }

    @Test
    fun `expired refresh token revokes its family and fails closed`() {
        val now = Instant.now()
        val initial = service.createSession(
            accountId = UUID.randomUUID(),
            subject = currentSubject,
            email = "expired@example.com",
            clientKind = "BROWSER",
            deviceLabel = "test",
            now = now
        )

        val ex = assertThrows(SquarewiseException::class.java) {
            service.rotateSession(
                rawRefreshToken = initial.refreshToken,
                deviceLabel = "test",
                now = now.plusSeconds(90 * 24 * 60 * 60L + 1)
            )
        }

        assertEquals(CategoryCode.AUTHENTICATION_ERROR, ex.definition.category)
        assertNotNull(sessionRepository.findByRefreshTokenDigest(digest.digest(initial.refreshToken))?.revokedAt)
    }

    @Test
    fun `refresh is denied and family revoked when identity is missing`() {
        val now = Instant.now()
        val initial = service.createSession(
            accountId = UUID.randomUUID(),
            subject = currentSubject,
            email = "missing-identity@example.com",
            clientKind = "NATIVE",
            deviceLabel = "test",
            now = now
        )
        identityAvailable = false

        val ex = assertThrows(SquarewiseException::class.java) {
            service.rotateSession(initial.refreshToken, "test", now.plusSeconds(1))
        }

        assertEquals(CategoryCode.AUTHENTICATION_ERROR, ex.definition.category)
        assertEquals(null, sessionRepository.findByRefreshTokenDigest(digest.digest(initial.refreshToken))?.revokedAt)
    }

    @Test
    fun `refresh is denied and family revoked when deletion was requested`() {
        val now = Instant.now()
        val initial = service.createSession(
            accountId = UUID.randomUUID(),
            subject = currentSubject,
            email = "deletion-requested@example.com",
            clientKind = "BROWSER",
            deviceLabel = "test",
            now = now
        )
        identityDeletionRequested = true

        val ex = assertThrows(SquarewiseException::class.java) {
            service.rotateSession(initial.refreshToken, "test", now.plusSeconds(1))
        }

        assertEquals(CategoryCode.AUTHENTICATION_ERROR, ex.definition.category)
        assertNotNull(sessionRepository.findByRefreshTokenDigest(digest.digest(initial.refreshToken))?.revokedAt)
    }

    @Test
    fun `legacy session without a backfilled subject fails closed`() {
        val now = Instant.now()
        val legacyCredential = "legacy-value"
        val familyId = UUID.randomUUID()
        sessionRepository.save(AuthSessionEntity(
            sessionId = UUID.randomUUID(),
            accountId = UUID.randomUUID(),
            familyId = familyId,
            refreshTokenDigest = digest.digest(legacyCredential),
            createdAt = now,
            lastUsedAt = now,
            expiresAt = now.plusSeconds(300),
            absoluteExpiresAt = now.plusSeconds(600),
            subject = null,
            clientKind = "NATIVE"
        ))

        assertThrows(SquarewiseException::class.java) {
            service.rotateSession(legacyCredential, "legacy", now.plusSeconds(1))
        }

        assertNotNull(sessionRepository.findByRefreshTokenDigest(digest.digest(legacyCredential))?.revokedAt)
    }
}
