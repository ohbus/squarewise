package com.subhrodip.squarewise.accounts.validation

import com.subhrodip.squarewise.accounts.auth.session.AuthSessionEntity
import com.subhrodip.squarewise.accounts.auth.session.AuthSessionRepository
import com.subhrodip.squarewise.accounts.errors.AccountsDomainException
import com.subhrodip.squarewise.accounts.profile.persistence.ProfileEntity
import com.subhrodip.squarewise.accounts.profile.persistence.ProfileRepository
import com.subhrodip.squarewise.errors.catalog.AccountsErrors
import com.subhrodip.squarewise.accounts.profile.cache.ProfileCache
import com.subhrodip.squarewise.accounts.profile.api.ProfileResponse
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import java.time.Instant
import java.util.UUID

/**
 * Standardized application-level foreign key and account entity referential integrity validator.
 *
 * Validates account profile and session existence in application memory and single bounded queries
 * prior to persisting dependent identities, tokens, and credentials, eliminating database writer lock
 * contention on parent account and session tables.
 */
@Component
class AccountReferenceValidator(
    private val profileRepository: ProfileRepository,
    private val sessionRepository: AuthSessionRepository,
    @Autowired(required = false)
    private val profileCache: ProfileCache? = null
) {

    /**
     * Checks whether the specified account exists and has no deletion requested.
     *
     * @param accountId the unique identifier of the target account profile.
     * @return true if account exists and is active, false otherwise.
     */
    fun isActiveAccount(accountId: UUID): Boolean {
        val profile = profileRepository.findById(accountId).orElse(null) ?: return false
        val active = !profile.deletionRequested
        if (active) {
            profileCache?.put(ProfileResponse(profile.accountId, profile.displayName, profile.timezone, profile.defaultCurrency))
        }
        return active
    }

    /**
     * Verifies that the specified account profile exists and is active.
     *
     * @param accountId the unique identifier of the target account profile.
     * @return the resolved [ProfileEntity].
     * @throws AccountsDomainException if the account does not exist or has deletion requested.
     */
    fun validateActiveAccount(accountId: UUID): ProfileEntity {
        val profile = profileRepository.findById(accountId).orElse(null)
            ?: throw AccountsDomainException(AccountsErrors.AUTHENTICATED_PROFILE_NOT_FOUND)
        if (profile.deletionRequested) {
            throw AccountsDomainException(AccountsErrors.PROFILE_REQUEST_INVALID)
        }
        profileCache?.put(ProfileResponse(profile.accountId, profile.displayName, profile.timezone, profile.defaultCurrency))
        return profile
    }

    /**
     * Verifies that the specified subject corresponds to an active account profile (not deleted).
     *
     * @param subject the unique user subject identifier.
     * @return the resolved [ProfileEntity].
     * @throws AccountsDomainException if the profile does not exist or has deletion requested.
     */
    fun validateActiveSubject(subject: String): ProfileEntity {
        val profile = profileRepository.findBySubject(subject)
            ?: throw AccountsDomainException(AccountsErrors.AUTHENTICATED_PROFILE_NOT_FOUND)
        if (profile.deletionRequested) {
            throw AccountsDomainException(AccountsErrors.PROFILE_REQUEST_INVALID)
        }
        profileCache?.put(ProfileResponse(profile.accountId, profile.displayName, profile.timezone, profile.defaultCurrency))
        return profile
    }

    /**
     * Verifies that the specified auth session exists and is currently active (not revoked or expired).
     *
     * @param sessionId the unique identifier of the target session.
     * @param now the current instant for checking expiry.
     * @return the resolved [AuthSessionEntity].
     * @throws AccountsDomainException if the session does not exist, is revoked, or is expired.
     */
    fun validateActiveSession(sessionId: UUID, now: Instant = Instant.now()): AuthSessionEntity {
        val session = sessionRepository.findById(sessionId).orElse(null)
            ?: throw AccountsDomainException(AccountsErrors.REFRESH_TOKEN_INVALID)
        if (session.revokedAt != null) {
            throw AccountsDomainException(AccountsErrors.SESSION_REVOKED)
        }
        if (!session.expiresAt.isAfter(now) || !session.absoluteExpiresAt.isAfter(now)) {
            throw AccountsDomainException(AccountsErrors.SESSION_EXPIRED)
        }
        return session
    }
}
