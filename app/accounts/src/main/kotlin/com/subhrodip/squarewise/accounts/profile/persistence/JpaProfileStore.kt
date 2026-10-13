package com.subhrodip.squarewise.accounts.profile.persistence

import com.subhrodip.squarewise.accounts.profile.api.ProfilePatchRequest
import com.subhrodip.squarewise.accounts.profile.api.ProfileResponse
import com.subhrodip.squarewise.accounts.profile.service.ProfileRules
import com.subhrodip.squarewise.accounts.errors.AccountsDomainException
import com.subhrodip.squarewise.errors.catalog.AccountsErrors
import com.subhrodip.squarewise.accounts.profile.cache.ProfileCache
import org.springframework.beans.factory.annotation.Autowired
import java.util.UUID
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionSynchronization
import org.springframework.transaction.support.TransactionSynchronizationManager

/**
 * JPA-backed implementation of [ProfileStore] for managing user account profiles.
 *
 * Interacts with PostgreSQL through [ProfileRepository]. Strictly enforces read/write
 * separation: queries never provision or mutate profiles on lookup (closing SEC-002).
 */
@Service
class JpaProfileStore(
    private val repository: ProfileRepository,
    @Autowired(required = false)
    private val profileCache: ProfileCache? = null
) : ProfileStore {

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
        return if (profileCache != null) {
            profileCache.getBySubject(subject) { repository.findBySubject(subject)?.toResponse() }
        } else {
            repository.findBySubject(subject)?.toResponse()
        }
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
        if (profileCache != null) {
            profileCache.getByAccountId(accountId) { repository.findById(accountId).orElse(null)?.toResponse() }
        } else {
            repository.findById(accountId).orElse(null)?.toResponse()
        }

    /**
     * Finds profiles by a batch of account IDs.
     */
    @Transactional(readOnly = true)
    override fun findByIds(accountIds: List<UUID>): List<ProfileResponse> {
        if (accountIds.isEmpty()) return emptyList()
        val distinctIds = accountIds.distinct()
        val cache = profileCache
        val byId = if (cache == null) {
            repository.findAllById(distinctIds).associate { it.accountId to it.toResponse() }
        } else {
            val result = mutableMapOf<UUID, ProfileResponse>()
            val missing = mutableListOf<UUID>()
            for (id in distinctIds) {
                val cached = cache.peekByAccountId(id)
                if (cached != null) {
                    result[id] = cached
                } else {
                    missing.add(id)
                }
            }
            if (missing.isNotEmpty()) {
                val loaded = repository.findAllById(missing).map { it.toResponse() }
                loaded.forEach {
                    cache.put(it)
                    result[it.accountId] = it
                }
            }
            result
        }
        return distinctIds.mapNotNull { byId[it] }
    }

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
        val saved = repository.save(entity).toResponse()
        invalidateAfterCommit(subject, entity.accountId)
        return saved
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
        invalidateAfterCommit(subject, entity.accountId)
    }

    private fun invalidateAfterCommit(subject: String, accountId: UUID) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(
                object : TransactionSynchronization {
                    override fun afterCommit() {
                        profileCache?.invalidate(subject, accountId)
                    }
                }
            )
        } else {
            profileCache?.invalidate(subject, accountId)
        }
    }
}

/**
 * Maps [ProfileEntity] to its public [ProfileResponse] representation.
 */
private fun ProfileEntity.toResponse() = ProfileResponse(accountId, displayName, timezone, defaultCurrency)
