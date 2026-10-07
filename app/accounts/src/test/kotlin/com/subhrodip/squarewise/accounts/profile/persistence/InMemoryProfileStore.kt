package com.subhrodip.squarewise.accounts.profile.persistence

import com.subhrodip.squarewise.accounts.errors.AccountsDomainException
import com.subhrodip.squarewise.accounts.profile.model.StoredProfile
import com.subhrodip.squarewise.accounts.auth.identity.AccountIdentity
import com.subhrodip.squarewise.accounts.auth.identity.AccountIdentityStore
import com.subhrodip.squarewise.accounts.profile.api.ProfileResponse
import com.subhrodip.squarewise.accounts.profile.service.ProfileRules
import com.subhrodip.squarewise.accounts.profile.api.ProfilePatchRequest
import com.subhrodip.squarewise.errors.catalog.AccountsErrors
import com.subhrodip.squarewise.errors.exceptions.SquarewiseException

import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * In-memory thread-safe implementation of [ProfileStore] and [AccountIdentityStore] used for testing.
 */
class InMemoryProfileStore : ProfileStore, AccountIdentityStore {
    private val profiles = ConcurrentHashMap<String, StoredProfile>()
    private val identities = ConcurrentHashMap<UUID, AccountIdentity>()

    override fun get(subject: String): ProfileResponse? {
        ProfileRules.requireSubject(subject)
        return profiles[subject]?.response
    }

    override fun create(
        accountId: UUID,
        subject: String,
        displayName: String,
        timezone: String,
        defaultCurrency: String
    ): ProfileResponse {
        ProfileRules.requireSubject(subject)
        ProfileRules.requireTimezone(timezone)
        val response = ProfileResponse(accountId, displayName, timezone, defaultCurrency)
        profiles[subject] = StoredProfile(response)
        return response
    }

    override fun findById(accountId: UUID): ProfileResponse? =
        profiles.values.firstOrNull { it.response.accountId == accountId }?.response

    override fun findByIds(accountIds: List<UUID>): List<ProfileResponse> =
        profiles.values.filter { accountIds.contains(it.response.accountId) }.map { it.response }

    override fun findByAccountId(accountId: UUID): AccountIdentity? =
        identities[accountId] ?: profiles.entries.firstOrNull { it.value.response.accountId == accountId }?.let { (subject, profile) ->
            AccountIdentity(
                accountId = accountId,
                subject = subject,
                email = subject.removePrefix("internal:"),
                deletionRequested = profile.deletionRequested
            )
        }

    override fun findByIssuerAndSubject(issuer: String, providerSubject: String): AccountIdentity? =
        identities.values.firstOrNull { it.issuer == issuer && it.subject == providerSubject }

    override fun findByEmail(email: String): AccountIdentity? =
        identities.values.firstOrNull { it.email.equals(email, ignoreCase = true) }

    override fun enrollIdentity(
        accountId: UUID,
        issuer: String,
        providerSubject: String,
        email: String,
        verified: Boolean
    ): AccountIdentity {
        val identity = AccountIdentity(
            accountId = accountId,
            subject = providerSubject,
            email = email,
            deletionRequested = false,
            issuer = issuer
        )
        identities[accountId] = identity
        return identity
    }

    override fun updateEmail(accountId: UUID, email: String, verified: Boolean): AccountIdentity {
        val current = findByAccountId(accountId) ?: throw IllegalArgumentException("Account not found")
        val updated = current.copy(email = email)
        identities[accountId] = updated
        return updated
    }

    override fun update(subject: String, patch: ProfilePatchRequest): ProfileResponse {
        val current = get(subject) ?: throw AccountsDomainException(AccountsErrors.AUTHENTICATED_PROFILE_NOT_FOUND)
        val updated = current.copy(
            displayName = patch.displayName ?: current.displayName,
            timezone = patch.timezone?.also(ProfileRules::requireTimezone) ?: current.timezone,
            defaultCurrency = patch.defaultCurrency ?: current.defaultCurrency
        )
        profiles[subject] = StoredProfile(updated)
        return updated
    }

    override fun requestDeletion(subject: String) {
        val current = profiles[subject] ?: throw AccountsDomainException(AccountsErrors.AUTHENTICATED_PROFILE_NOT_FOUND)
        profiles[subject] = current.copy(deletionRequested = true)
    }

    /** Helper for tests to pre-seed profiles. */
    fun seed(subject: String, accountId: UUID = UUID.randomUUID()): ProfileResponse {
        return create(accountId, subject, subject, "UTC", "EUR")
    }
}
