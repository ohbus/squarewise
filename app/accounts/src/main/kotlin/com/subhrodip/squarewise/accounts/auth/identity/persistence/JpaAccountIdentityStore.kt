package com.subhrodip.squarewise.accounts.auth.identity.persistence

import com.subhrodip.squarewise.accounts.errors.AccountsInputException

import com.subhrodip.squarewise.accounts.auth.identity.AccountIdentity
import com.subhrodip.squarewise.accounts.auth.identity.AccountIdentityStore
import com.subhrodip.squarewise.accounts.profile.persistence.ProfileRepository
import java.time.Instant
import java.util.UUID
import org.springframework.context.annotation.Primary
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * JPA-backed implementation of [AccountIdentityStore] managing durable account identity records.
 *
 * PostgreSQL is authoritative for identity bindings, linking (issuer, provider_subject)
 * to stable local account UUIDs, decoupled from mutable email contact information.
 */
@Primary
@Service
class JpaAccountIdentityStore(
    private val identityRepository: AccountIdentityRepository,
    private val profileRepository: ProfileRepository
) : AccountIdentityStore {

    /**
     * Resolves the current identity for a durable account identifier.
     *
     * @param accountId Local account identifier stored in session or profile.
     * @return [AccountIdentity] or null if the account has no identity or profile.
     */
    @Transactional(readOnly = true)
    override fun findByAccountId(accountId: UUID): AccountIdentity? {
        val profile = profileRepository.findById(accountId).orElse(null) ?: return null
        val identity = identityRepository.findByAccountId(accountId).firstOrNull()

        return AccountIdentity(
            accountId = accountId,
            subject = identity?.providerSubject ?: profile.subject,
            email = identity?.email ?: profile.subject.removePrefix("internal:"),
            deletionRequested = profile.deletionRequested,
            issuer = identity?.issuer ?: "squarewise-internal"
        )
    }

    /**
     * Resolves an account identity by authoritative issuer and provider subject.
     *
     * @param issuer Identifier of the issuing authority.
     * @param providerSubject Provider-specific unique subject string.
     * @return [AccountIdentity] or null.
     */
    @Transactional(readOnly = true)
    override fun findByIssuerAndSubject(issuer: String, providerSubject: String): AccountIdentity? {
        val identity = identityRepository.findByIssuerAndProviderSubject(issuer, providerSubject) ?: return null
        val profile = profileRepository.findById(identity.accountId).orElse(null) ?: return null

        return AccountIdentity(
            accountId = identity.accountId,
            subject = identity.providerSubject,
            email = identity.email ?: "",
            deletionRequested = profile.deletionRequested,
            issuer = identity.issuer
        )
    }

    /**
     * Resolves an active account identity by verified user email.
     *
     * @param email Normalized user email address.
     * @return [AccountIdentity] or null if no active identity exists for this email.
     */
    @Transactional(readOnly = true)
    override fun findByEmail(email: String): AccountIdentity? {
        val identity = identityRepository.findFirstByEmailIgnoreCaseAndStatus(email, "ACTIVE") ?: return null
        val profile = profileRepository.findById(identity.accountId).orElse(null) ?: return null

        return AccountIdentity(
            accountId = identity.accountId,
            subject = identity.providerSubject,
            email = identity.email ?: email,
            deletionRequested = profile.deletionRequested,
            issuer = identity.issuer
        )
    }

    /**
     * Enrolls or creates a durable identity mapping for an account.
     *
     * @param accountId Stable account identifier.
     * @param issuer Trusted authority identifier.
     * @param providerSubject Subject claim unique to that authority.
     * @param email Verified contact email.
     * @param verified Whether the email is verified.
     * @return Enrolled [AccountIdentity].
     */
    @Transactional
    override fun enrollIdentity(
        accountId: UUID,
        issuer: String,
        providerSubject: String,
        email: String,
        verified: Boolean
    ): AccountIdentity {
        val existing = identityRepository.findByIssuerAndProviderSubject(issuer, providerSubject)
        val profile = profileRepository.findById(accountId).orElse(null)

        val entity = if (existing != null) {
            existing.email = email
            existing.emailVerified = verified
            existing.updatedAt = Instant.now()
            identityRepository.save(existing)
        } else {
            val newEntity = AccountIdentityEntity(
                identityId = UUID.randomUUID(),
                accountId = accountId,
                issuer = issuer,
                providerSubject = providerSubject,
                email = email,
                emailVerified = verified,
                status = "ACTIVE",
                createdAt = Instant.now(),
                updatedAt = Instant.now()
            )
            identityRepository.save(newEntity)
        }

        return AccountIdentity(
            accountId = entity.accountId,
            subject = entity.providerSubject,
            email = entity.email ?: email,
            deletionRequested = profile?.deletionRequested ?: false,
            issuer = entity.issuer
        )
    }

    /**
     * Updates contact email on an existing identity without altering the durable subject or account ID.
     *
     * @param accountId Account whose email is being updated.
     * @param email New normalized email.
     * @param verified Verification status of the new email.
     */
    @Transactional
    override fun updateEmail(accountId: UUID, email: String, verified: Boolean): AccountIdentity {
        val identities = identityRepository.findByAccountId(accountId)
        val identity = identities.firstOrNull()
            ?: throw AccountsInputException("No identity found for account $accountId")

        identity.email = email
        identity.emailVerified = verified
        identity.updatedAt = Instant.now()
        identityRepository.save(identity)

        val profile = profileRepository.findById(accountId).orElse(null)
        return AccountIdentity(
            accountId = accountId,
            subject = identity.providerSubject,
            email = email,
            deletionRequested = profile?.deletionRequested ?: false,
            issuer = identity.issuer
        )
    }
}
