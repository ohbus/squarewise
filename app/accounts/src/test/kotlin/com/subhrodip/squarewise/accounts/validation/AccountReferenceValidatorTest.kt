package com.subhrodip.squarewise.accounts.validation

import com.subhrodip.squarewise.accounts.auth.session.AuthSessionEntity
import com.subhrodip.squarewise.accounts.auth.session.AuthSessionRepository
import com.subhrodip.squarewise.accounts.errors.AccountsDomainException
import com.subhrodip.squarewise.accounts.profile.api.ProfileResponse
import com.subhrodip.squarewise.accounts.profile.cache.ProfileCache
import com.subhrodip.squarewise.accounts.profile.persistence.ProfileEntity
import com.subhrodip.squarewise.accounts.profile.persistence.ProfileRepository
import com.subhrodip.squarewise.errors.catalog.AccountsErrors
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.verifyNoInteractions
import org.mockito.Mockito.`when`
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.Optional
import java.util.UUID

/**
 * Unit tests verifying [AccountReferenceValidator] referential integrity and lifecycle enforcement.
 */
class AccountReferenceValidatorTest {

    private lateinit var profileRepository: ProfileRepository
    private lateinit var sessionRepository: AuthSessionRepository
    private lateinit var validator: AccountReferenceValidator

    @BeforeEach
    fun setUp() {
        profileRepository = mock(ProfileRepository::class.java)
        sessionRepository = mock(AuthSessionRepository::class.java)
        validator = AccountReferenceValidator(profileRepository, sessionRepository)
    }

    @Test
    fun `validateActiveAccount returns entity when account exists and not deleted`() {
        val accountId = UUID.randomUUID()
        val profile = ProfileEntity(
            accountId = accountId,
            subject = "internal:test@example.com",
            displayName = "Test User",
            timezone = "UTC",
            defaultCurrency = "EUR",
            deletionRequested = false
        )
        `when`(profileRepository.findById(accountId)).thenReturn(Optional.of(profile))

        val result = validator.validateActiveAccount(accountId)

        assertNotNull(result)
        assertEquals(accountId, result.accountId)
    }

    @Test
    fun `validateActiveAccount throws when account not found`() {
        val accountId = UUID.randomUUID()
        `when`(profileRepository.findById(accountId)).thenReturn(Optional.empty())

        val ex = assertThrows(AccountsDomainException::class.java) {
            validator.validateActiveAccount(accountId)
        }
        assertSame(AccountsErrors.AUTHENTICATED_PROFILE_NOT_FOUND, ex.definition)
    }

    @Test
    fun `validateActiveAccount throws when deletion requested`() {
        val accountId = UUID.randomUUID()
        val profile = ProfileEntity(
            accountId = accountId,
            subject = "internal:test@example.com",
            displayName = "Deleted User",
            timezone = "UTC",
            defaultCurrency = "EUR",
            deletionRequested = true
        )
        `when`(profileRepository.findById(accountId)).thenReturn(Optional.of(profile))

        val ex = assertThrows(AccountsDomainException::class.java) {
            validator.validateActiveAccount(accountId)
        }
        assertSame(AccountsErrors.PROFILE_REQUEST_INVALID, ex.definition)
    }

    @Test
    fun `validateActiveSubject returns entity when profile is active`() {
        val subject = "alice"
        val profile = ProfileEntity(
            accountId = UUID.randomUUID(),
            subject = subject,
            displayName = "Alice",
            timezone = "UTC",
            defaultCurrency = "EUR",
            deletionRequested = false
        )
        `when`(profileRepository.findBySubject(subject)).thenReturn(profile)

        val result = validator.validateActiveSubject(subject)

        assertNotNull(result)
        assertEquals(subject, result.subject)
    }

    @Test
    fun `validateActiveSubject throws when subject not found`() {
        val subject = "unknown"
        `when`(profileRepository.findBySubject(subject)).thenReturn(null)

        val ex = assertThrows(AccountsDomainException::class.java) {
            validator.validateActiveSubject(subject)
        }
        assertSame(AccountsErrors.AUTHENTICATED_PROFILE_NOT_FOUND, ex.definition)
    }

    @Test
    fun `validateActiveSubject throws when deletion requested`() {
        val subject = "deleted-user"
        val profile = ProfileEntity(
            accountId = UUID.randomUUID(),
            subject = subject,
            displayName = "Deleted",
            timezone = "UTC",
            defaultCurrency = "EUR",
            deletionRequested = true
        )
        `when`(profileRepository.findBySubject(subject)).thenReturn(profile)

        val ex = assertThrows(AccountsDomainException::class.java) {
            validator.validateActiveSubject(subject)
        }
        assertSame(AccountsErrors.PROFILE_REQUEST_INVALID, ex.definition)
    }

    @Test
    fun `validateActiveSession returns entity when session is active`() {
        val sessionId = UUID.randomUUID()
        val now = Instant.now()
        val session = AuthSessionEntity(
            sessionId = sessionId,
            accountId = UUID.randomUUID(),
            familyId = UUID.randomUUID(),
            clientKind = "WEB",
            deviceLabel = "Chrome",
            refreshTokenDigest = byteArrayOf(1, 2, 3),
            createdAt = now.minus(1, ChronoUnit.HOURS),
            lastUsedAt = now.minus(1, ChronoUnit.HOURS),
            expiresAt = now.plus(1, ChronoUnit.HOURS),
            absoluteExpiresAt = now.plus(30, ChronoUnit.DAYS),
            revokedAt = null,
            replacedBySessionId = null
        )
        `when`(sessionRepository.findById(sessionId)).thenReturn(Optional.of(session))

        val result = validator.validateActiveSession(sessionId, now)

        assertNotNull(result)
        assertEquals(sessionId, result.sessionId)
    }

    @Test
    fun `validateActiveSession throws when session is revoked`() {
        val sessionId = UUID.randomUUID()
        val now = Instant.now()
        val session = AuthSessionEntity(
            sessionId = sessionId,
            accountId = UUID.randomUUID(),
            familyId = UUID.randomUUID(),
            clientKind = "WEB",
            deviceLabel = "Chrome",
            refreshTokenDigest = byteArrayOf(1, 2, 3),
            createdAt = now.minus(2, ChronoUnit.HOURS),
            lastUsedAt = now.minus(2, ChronoUnit.HOURS),
            expiresAt = now.plus(1, ChronoUnit.HOURS),
            absoluteExpiresAt = now.plus(30, ChronoUnit.DAYS),
            revokedAt = now.minus(1, ChronoUnit.HOURS),
            replacedBySessionId = null
        )
        `when`(sessionRepository.findById(sessionId)).thenReturn(Optional.of(session))

        val ex = assertThrows(AccountsDomainException::class.java) {
            validator.validateActiveSession(sessionId, now)
        }
        assertSame(AccountsErrors.SESSION_REVOKED, ex.definition)
    }

    @Test
    fun `validateActiveSession throws when session is expired by relative or absolute expiry`() {
        val sessionId = UUID.randomUUID()
        val now = Instant.now()
        val session = AuthSessionEntity(
            sessionId = sessionId,
            accountId = UUID.randomUUID(),
            familyId = UUID.randomUUID(),
            clientKind = "WEB",
            deviceLabel = "Chrome",
            refreshTokenDigest = byteArrayOf(1, 2, 3),
            createdAt = now.minus(2, ChronoUnit.HOURS),
            lastUsedAt = now.minus(2, ChronoUnit.HOURS),
            expiresAt = now.minus(1, ChronoUnit.HOURS),
            absoluteExpiresAt = now.plus(30, ChronoUnit.DAYS),
            revokedAt = null,
            replacedBySessionId = null
        )
        `when`(sessionRepository.findById(sessionId)).thenReturn(Optional.of(session))

        val ex = assertThrows(AccountsDomainException::class.java) {
            validator.validateActiveSession(sessionId, now)
        }
        assertSame(AccountsErrors.SESSION_EXPIRED, ex.definition)

        // Test with default now parameter
        assertThrows(AccountsDomainException::class.java) {
            validator.validateActiveSession(sessionId)
        }

        // Test absolute expiry
        val sessionWithAbsExpired = AuthSessionEntity(
            sessionId = sessionId,
            accountId = UUID.randomUUID(),
            familyId = UUID.randomUUID(),
            clientKind = "WEB",
            deviceLabel = "Chrome",
            refreshTokenDigest = byteArrayOf(1, 2, 3),
            createdAt = now.minus(2, ChronoUnit.HOURS),
            lastUsedAt = now.minus(2, ChronoUnit.HOURS),
            expiresAt = now.plus(1, ChronoUnit.HOURS),
            absoluteExpiresAt = now.minus(1, ChronoUnit.SECONDS),
            revokedAt = null,
            replacedBySessionId = null
        )
        `when`(sessionRepository.findById(sessionId)).thenReturn(Optional.of(sessionWithAbsExpired))
        val exAbs = assertThrows(AccountsDomainException::class.java) {
            validator.validateActiveSession(sessionId, now)
        }
        assertSame(AccountsErrors.SESSION_EXPIRED, exAbs.definition)
    }

    @Test
    fun `isActiveAccount returns true when profile exists in repository`() {
        val accountId = UUID.randomUUID()
        val profile = ProfileEntity(
            accountId = accountId,
            subject = "user@example.com",
            displayName = "User",
            timezone = "UTC",
            defaultCurrency = "USD",
            deletionRequested = false
        )
        `when`(profileRepository.findById(accountId)).thenReturn(Optional.of(profile))

        assertTrue(validator.isActiveAccount(accountId))
    }

    @Test
    fun `isActiveAccount returns false when profile has deletion requested or missing`() {
        val accountId = UUID.randomUUID()
        `when`(profileRepository.findById(accountId)).thenReturn(Optional.empty())
        assertFalse(validator.isActiveAccount(accountId))

        val deletedProfile = ProfileEntity(
            accountId = accountId,
            subject = "del@example.com",
            displayName = "Deleted",
            timezone = "UTC",
            defaultCurrency = "USD",
            deletionRequested = true
        )
        `when`(profileRepository.findById(accountId)).thenReturn(Optional.of(deletedProfile))
        assertFalse(validator.isActiveAccount(accountId))
    }

    @Test
    fun `isActiveAccount queries profileRepository and returns false when deletion requested even if cache is present`() {
        val cache = ProfileCache()
        val cachedValidator = AccountReferenceValidator(profileRepository, sessionRepository, cache)
        val accountId = UUID.randomUUID()
        val profileResponse = ProfileResponse(
            accountId = accountId,
            displayName = "Cached",
            timezone = "UTC",
            defaultCurrency = "EUR"
        )
        cache.put(profileResponse)

        val deletedProfile = ProfileEntity(
            accountId = accountId,
            subject = "del@example.com",
            displayName = "Cached",
            timezone = "UTC",
            defaultCurrency = "EUR",
            deletionRequested = true
        )
        `when`(profileRepository.findById(accountId)).thenReturn(Optional.of(deletedProfile))

        assertFalse(cachedValidator.isActiveAccount(accountId))
    }

    @Test
    fun `isActiveAccount queries profileRepository and returns true when active`() {
        val cache = ProfileCache()
        val cachedValidator = AccountReferenceValidator(profileRepository, sessionRepository, cache)
        val accountId = UUID.randomUUID()
        val profile = ProfileEntity(
            accountId = accountId,
            subject = "fallback@example.com",
            displayName = "Fallback User",
            timezone = "UTC",
            defaultCurrency = "EUR",
            deletionRequested = false
        )
        `when`(profileRepository.findById(accountId)).thenReturn(Optional.of(profile))

        assertTrue(cachedValidator.isActiveAccount(accountId))
    }
}
