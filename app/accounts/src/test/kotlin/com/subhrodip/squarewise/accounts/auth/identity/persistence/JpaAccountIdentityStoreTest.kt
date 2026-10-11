package com.subhrodip.squarewise.accounts.auth.identity.persistence

import com.subhrodip.squarewise.accounts.auth.identity.AccountIdentityStore
import com.subhrodip.squarewise.accounts.profile.persistence.ProfileEntity
import com.subhrodip.squarewise.accounts.profile.persistence.ProfileRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

/**
 * Integration test verifying JPA-backed durable identity persistence,
 * uniqueness constraints, and email updates decoupled from subjects.
 */
@SpringBootTest
@Transactional
class JpaAccountIdentityStoreTest @Autowired constructor(
    private val identityStore: AccountIdentityStore,
    private val identityRepository: AccountIdentityRepository,
    private val profileRepository: ProfileRepository
) {

    @Test
    fun `enrolls new identity and retrieves by accountId, composite key, and email`() {
        val accountId = UUID.randomUUID()
        val issuer = "https://accounts.squarewise.test"
        val subject = "sqw:$accountId"
        val email = "alice.smith@example.com"

        profileRepository.save(
            ProfileEntity(
                accountId = accountId,
                subject = subject,
                displayName = "Alice Smith",
                timezone = "UTC",
                defaultCurrency = "EUR"
            )
        )

        val enrolled = identityStore.enrollIdentity(
            accountId = accountId,
            issuer = issuer,
            providerSubject = subject,
            email = email,
            verified = true
        )

        assertEquals(accountId, enrolled.accountId)
        assertEquals(subject, enrolled.subject)
        assertEquals(email, enrolled.email)

        val byAccountId = identityStore.findByAccountId(accountId)
        assertNotNull(byAccountId)
        assertEquals(subject, byAccountId?.subject)

        val byComposite = identityStore.findByIssuerAndSubject(issuer, subject)
        assertNotNull(byComposite)
        assertEquals(accountId, byComposite?.accountId)

        val byEmail = identityStore.findByEmail("ALICE.SMITH@EXAMPLE.COM")
        assertNotNull(byEmail)
        assertEquals(accountId, byEmail?.accountId)
    }

    @Test
    fun `repository email lookup defaults to active identities`() {
        val accountId = UUID.randomUUID()
        profileRepository.save(
            ProfileEntity(
                accountId = accountId,
                subject = "sqw:$accountId",
                displayName = "Default Status",
                timezone = "UTC",
                defaultCurrency = "EUR"
            )
        )
        val identity = AccountIdentityEntity(
            identityId = UUID.randomUUID(),
            accountId = accountId,
            issuer = "https://accounts.squarewise.test",
            providerSubject = "sqw:$accountId",
            email = "default-status@example.com",
            status = "ACTIVE"
        )
        identityRepository.save(identity)

        val resolved = identityRepository.findFirstByEmailIgnoreCaseAndStatus("DEFAULT-STATUS@EXAMPLE.COM")

        assertEquals(identity.identityId, resolved?.identityId)
    }

    @Test
    fun `updates email without modifying account subject or issuer`() {
        val accountId = UUID.randomUUID()
        val issuer = "https://accounts.squarewise.test"
        val subject = "sqw:$accountId"
        val originalEmail = "bob.old@example.com"
        val newEmail = "bob.new@example.com"

        profileRepository.save(
            ProfileEntity(
                accountId = accountId,
                subject = subject,
                displayName = "Bob Jones",
                timezone = "UTC",
                defaultCurrency = "EUR"
            )
        )

        identityStore.enrollIdentity(
            accountId = accountId,
            issuer = issuer,
            providerSubject = subject,
            email = originalEmail,
            verified = true
        )

        val updated = identityStore.updateEmail(accountId, newEmail, verified = true)
        assertEquals(newEmail, updated.email)
        assertEquals(subject, updated.subject)

        assertNull(identityStore.findByEmail(originalEmail))
        val byNewEmail = identityStore.findByEmail(newEmail)
        assertNotNull(byNewEmail)
        assertEquals(accountId, byNewEmail?.accountId)
    }

    @Test
    fun `falls back to profile subject and internal issuer without durable identity`() {
        val accountId = UUID.randomUUID()
        profileRepository.save(
            ProfileEntity(
                accountId = accountId,
                subject = "internal:fallback@example.com",
                displayName = "Fallback User",
                timezone = "UTC",
                defaultCurrency = "EUR",
                deletionRequested = true,
            )
        )

        val resolved = identityStore.findByAccountId(accountId)

        assertNotNull(resolved)
        assertEquals("internal:fallback@example.com", resolved?.subject)
        assertEquals("fallback@example.com", resolved?.email)
        assertEquals("squarewise-internal", resolved?.issuer)
        assertEquals(true, resolved?.deletionRequested)
    }

    @Test
    fun `returns null when account profile does not exist`() {
        assertNull(identityStore.findByAccountId(UUID.randomUUID()))
    }

    @Test
    fun `returns null for unknown composite identity and email`() {
        assertNull(identityStore.findByIssuerAndSubject("https://unknown.example", "missing-subject"))
        assertNull(identityStore.findByEmail("missing@example.com"))
    }

    /** Verifies email updates reject an account without a durable identity before mutation. */
    @Test
    fun `rejects email update when account has no identity`() {
        val accountId = UUID.randomUUID()

        val error = assertThrows(IllegalArgumentException::class.java) {
            identityStore.updateEmail(accountId, "missing@example.com", verified = true)
        }

        assertEquals("No identity found for account $accountId", error.message)
    }

    @Test
    fun `returns empty email when durable identity has no email`() {
        val accountId = UUID.randomUUID()
        val issuer = "https://accounts.squarewise.test"
        val subject = "sqw:null-email-$accountId"
        profileRepository.save(
            ProfileEntity(
                accountId = accountId,
                subject = subject,
                displayName = "No Email",
                timezone = "UTC",
                defaultCurrency = "EUR",
            )
        )
        identityRepository.save(
            AccountIdentityEntity(
                identityId = UUID.randomUUID(),
                accountId = accountId,
                issuer = issuer,
                providerSubject = subject,
                email = null,
            )
        )

        val resolved = identityStore.findByIssuerAndSubject(issuer, subject)

        assertNotNull(resolved)
        assertEquals("", resolved?.email)
    }

    @Test
    fun `re-enrolling an existing composite identity updates contact and verification state`() {
        val accountId = UUID.randomUUID()
        val issuer = "https://accounts.squarewise.test"
        val subject = "sqw:re-enroll-$accountId"
        profileRepository.save(
            ProfileEntity(
                accountId = accountId,
                subject = subject,
                displayName = "Re-enrolled User",
                timezone = "UTC",
                defaultCurrency = "EUR",
            )
        )

        identityStore.enrollIdentity(accountId, issuer, subject, "old@example.com", verified = false)
        val updated = identityStore.enrollIdentity(
            accountId,
            issuer,
            subject,
            "new@example.com",
            verified = true,
        )

        assertEquals("new@example.com", updated.email)
        assertEquals(accountId, identityStore.findByEmail("new@example.com")?.accountId)
        assertNull(identityStore.findByEmail("old@example.com"))
    }
}
