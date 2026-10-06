package com.subhrodip.squarewise.accounts.profile.persistence

import com.subhrodip.squarewise.accounts.profile.api.ProfilePatchRequest
import com.subhrodip.squarewise.accounts.profile.api.ProfileResponse
import com.subhrodip.squarewise.accounts.profile.service.ProfileRules
import com.subhrodip.squarewise.accounts.errors.AccountsDomainException
import com.subhrodip.squarewise.errors.catalog.AccountsErrors
import java.util.UUID
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * JPA-backed implementation of [ProfileStore] for managing user account profiles.
 *
 * Interacts with PostgreSQL through [ProfileRepository]. Strictly enforces read/write
 * separation: queries never provision or mutate profiles on lookup (closing SEC-002).
 */
@Service
class JpaProfileStore(private val repository: ProfileRepository) : ProfileStore {

    /**
     * Retrieves the profile associated with the given subject, returning null if absent.
     * Never mutates or provisions a profile as a side effect of read lookup.
     *
     * @param subject OIDC subject identifier. Must not be blank.
     * @return [ProfileResponse] or null if not found.
     */
    @Transactional(readOnly = true)
    override fun get(subject: String): ProfileResponse? {
        ProfileRules.requireSubject(subject)
        return repository.findBySubject(subject)?.toResponse()
    }

    /**
     * Explicitly provisions a new profile for an account.
     */
    @Transactional
    override fun create(
        accountId: UUID,
        subject: String,
        displayName: String,
        timezone: String,
        defaultCurrency: String
    ): ProfileResponse {
        ProfileRules.requireSubject(subject)
        ProfileRules.requireTimezone(timezone)
        val entity = ProfileEntity(
            accountId = accountId,
            subject = subject,
            displayName = displayName,
            timezone = timezone,
            defaultCurrency = defaultCurrency
        )
        return repository.save(entity).toResponse()
    }

    /**
     * Finds a profile by account ID.
     */
    @Transactional(readOnly = true)
    override fun findById(accountId: UUID): ProfileResponse? =
        repository.findById(accountId).orElse(null)?.toResponse()

    /**
     * Finds profiles by a batch of account IDs.
     */
    @Transactional(readOnly = true)
    override fun findByIds(accountIds: List<UUID>): List<ProfileResponse> =
        repository.findAllById(accountIds).map { it.toResponse() }

    /**
     * Updates an existing profile. Fails closed if the profile does not exist.
     */
    @Transactional
    override fun update(subject: String, patch: ProfilePatchRequest): ProfileResponse {
        ProfileRules.requireSubject(subject)
        val entity = repository.findBySubject(subject)
            ?: throw AccountsDomainException(AccountsErrors.AUTHENTICATED_PROFILE_NOT_FOUND)
        entity.displayName = patch.displayName ?: entity.displayName
        entity.timezone = patch.timezone?.also(ProfileRules::requireTimezone) ?: entity.timezone
        entity.defaultCurrency = patch.defaultCurrency ?: entity.defaultCurrency
        return repository.save(entity).toResponse()
    }

    /**
     * Marks an account profile for deletion while preserving historical financial attribution.
     */
    @Transactional
    override fun requestDeletion(subject: String) {
        ProfileRules.requireSubject(subject)
        val entity = repository.findBySubject(subject)
            ?: throw AccountsDomainException(AccountsErrors.AUTHENTICATED_PROFILE_NOT_FOUND)
        entity.deletionRequested = true
        repository.save(entity)
    }
}

/**
 * Maps [ProfileEntity] to its public [ProfileResponse] representation.
 */
private fun ProfileEntity.toResponse() = ProfileResponse(accountId, displayName, timezone, defaultCurrency)
