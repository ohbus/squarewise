package com.subhrodip.squarewise.accounts.auth.identity.persistence

import com.subhrodip.squarewise.accounts.profile.persistence.ProfileEntity
import com.subhrodip.squarewise.accounts.profile.persistence.ProfileRepository
import java.time.Instant
import java.util.Optional
import java.util.UUID
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import com.subhrodip.squarewise.accounts.errors.AccountsDomainException
import com.subhrodip.squarewise.accounts.validation.AccountReferenceValidator
import com.subhrodip.squarewise.errors.catalog.AccountsErrors

/**
 * Unit coverage for defensive identity projections that are not constructible through the
 * normal foreign-key-backed integration fixture.
 *
 * The database prevents an identity from surviving its profile, but the store still fails
 * closed when a repository returns an orphaned row. Nullable email projections are also
 * retained because legacy identity rows may not have a contact address.
 */
class JpaAccountIdentityStoreUnitTest {
    private val identities = mock(AccountIdentityRepository::class.java)
    private val profiles = mock(ProfileRepository::class.java)
    private val validator = mock(AccountReferenceValidator::class.java)
    private val store = JpaAccountIdentityStore(identities, profiles, validator)

    @Test
    fun `issuer lookup returns null when the identity profile is missing`() {
        val accountId = UUID.randomUUID()
        val entity = identity(accountId, email = "orphan@example.com")
        `when`(identities.findByIssuerAndProviderSubject("issuer", "subject"))
            .thenReturn(entity)
        `when`(profiles.findById(accountId)).thenReturn(Optional.empty())

        assertNull(store.findByIssuerAndSubject("issuer", "subject"))
    }

    @Test
    fun `email lookup returns null when the identity profile is missing`() {
        val accountId = UUID.randomUUID()
        val entity = identity(accountId, email = "orphan@example.com")
        `when`(identities.findFirstByEmailIgnoreCaseAndStatus("orphan@example.com", "ACTIVE"))
            .thenReturn(entity)
        `when`(profiles.findById(accountId)).thenReturn(Optional.empty())

        assertNull(store.findByEmail("orphan@example.com"))
    }

    @Test
    fun `email lookup falls back to requested email for a legacy null email`() {
        val accountId = UUID.randomUUID()
        val entity = identity(accountId, email = null)
        val profile = profile(accountId)
        `when`(identities.findFirstByEmailIgnoreCaseAndStatus("legacy@example.com", "ACTIVE"))
            .thenReturn(entity)
        `when`(profiles.findById(accountId)).thenReturn(Optional.of(profile))

        val resolved = store.findByEmail("legacy@example.com")

        assertEquals("legacy@example.com", resolved?.email)
        assertEquals(accountId, resolved?.accountId)
    }

    @Test
    fun `re-enrollment keeps a false deletion flag when the profile is missing`() {
        val accountId = UUID.randomUUID()
        val entity = identity(accountId, email = "old@example.com")
        val profile = profile(accountId).apply { deletionRequested = false }
        `when`(identities.findByIssuerAndProviderSubject("issuer", "subject"))
            .thenReturn(entity)
        `when`(identities.save(entity)).thenReturn(entity)
        `when`(validator.validateActiveAccount(accountId)).thenReturn(profile)

        val enrolled = store.enrollIdentity(
            accountId = accountId,
            issuer = "issuer",
            providerSubject = "subject",
            email = "new@example.com",
            verified = true
        )

        assertEquals("new@example.com", enrolled.email)
        assertFalse(enrolled.deletionRequested)
    }

    @Test
    fun `new enrollment preserves a profile deletion request`() {
        val accountId = UUID.randomUUID()
        val profile = profile(accountId).apply { deletionRequested = true }
        `when`(identities.findByIssuerAndProviderSubject("issuer", "new-subject"))
            .thenReturn(null)
        `when`(validator.validateActiveAccount(accountId)).thenReturn(profile)
        val persistedLegacyIdentity = identity(accountId, email = null).apply {
            providerSubject = "new-subject"
        }
        `when`(identities.save(any(AccountIdentityEntity::class.java)))
            .thenReturn(persistedLegacyIdentity)

        val enrolled = store.enrollIdentity(
            accountId = accountId,
            issuer = "issuer",
            providerSubject = "new-subject",
            email = "new@example.com",
            verified = true
        )

        assertEquals(accountId, enrolled.accountId)
        assertEquals(true, enrolled.deletionRequested)
    }

    @Test
    fun `updateEmail propagates rejection when validateActiveAccount throws and does not save`() {
        val accountId = UUID.randomUUID()
        val entity = identity(accountId, email = "old@example.com")
        `when`(identities.findFirstByAccountIdOrderByIdentityIdAsc(accountId)).thenReturn(entity)
        `when`(validator.validateActiveAccount(accountId)).thenThrow(
            AccountsDomainException(
                AccountsErrors.AUTHENTICATED_PROFILE_NOT_FOUND
            )
        )

        assertThrows(
            AccountsDomainException::class.java
        ) {
            store.updateEmail(accountId, "new@example.com", verified = true)
        }
        verify(identities, never()).save(any())
    }

    @Test
    fun `enrollIdentity propagates rejection when validateActiveAccount throws and does not save`() {
        val accountId = UUID.randomUUID()
        `when`(validator.validateActiveAccount(accountId)).thenThrow(
            AccountsDomainException(
                AccountsErrors.AUTHENTICATED_PROFILE_NOT_FOUND
            )
        )

        assertThrows(
            AccountsDomainException::class.java
        ) {
            store.enrollIdentity(accountId, "issuer", "subject", "test@example.com", true)
        }
        verify(identities, never()).save(any())
    }

    @Test
    fun `email update validates active account via reference validator when present`() {
        val accountId = UUID.randomUUID()
        val entity = identity(accountId, email = "old@example.com")
        val profile = profile(accountId)

        `when`(identities.findFirstByAccountIdOrderByIdentityIdAsc(accountId)).thenReturn(entity)
        `when`(identities.save(entity)).thenReturn(entity)
        `when`(validator.validateActiveAccount(accountId)).thenReturn(profile)

        val updated = store.updateEmail(accountId, "new@example.com", verified = true)

        assertEquals("new@example.com", updated.email)
        verify(validator).validateActiveAccount(accountId)
    }

    private fun identity(accountId: UUID, email: String?): AccountIdentityEntity =
        AccountIdentityEntity(
            identityId = UUID.randomUUID(),
            accountId = accountId,
            issuer = "issuer",
            providerSubject = "subject",
            email = email,
            createdAt = Instant.parse("2026-01-01T00:00:00Z"),
            updatedAt = Instant.parse("2026-01-01T00:00:00Z")
        )

    private fun profile(accountId: UUID) = ProfileEntity(
        accountId = accountId,
        subject = "subject",
        displayName = "Legacy User",
        timezone = "UTC",
        defaultCurrency = "EUR"
    )
}
