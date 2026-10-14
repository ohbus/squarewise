package com.subhrodip.squarewise.accounts.requests.deletion.persistence

import com.subhrodip.squarewise.accounts.requests.deletion.model.DeletionRequest
import com.subhrodip.squarewise.accounts.requests.deletion.model.DeletionStatus
import com.subhrodip.squarewise.accounts.profile.service.ProfileRules
import com.subhrodip.squarewise.accounts.profile.persistence.ProfileStore
import com.subhrodip.squarewise.accounts.profile.persistence.ProfileRepository
import com.subhrodip.squarewise.accounts.auth.session.AuthSessionRepository
import com.subhrodip.squarewise.db.routing.DbReader
import com.subhrodip.squarewise.db.routing.DbWriter
import org.springframework.context.annotation.Primary
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

/**
 * JPA-backed implementation of [DeletionRequestStore].
 *
 * Persists deletion requests to PostgreSQL via [DeletionRequestRepository] while updating
 * the user profile record via [ProfileStore] to preserve historical financial attribution.
 *
 * @param repository Spring Data JPA repository for deletion request entities.
 * @param profileStore Store used to flag profiles as deletion-requested.
 * @param profileRepository Writer-side profile lookup used to resolve the local
 *        account identifier without trusting the caller's subject as a session key.
 * @param authSessionRepository Writer-authoritative refresh-session revocation port.
 * @param clock Timestamp supplier, defaulting to [Instant.now].
 */
@Primary
@Service
class JpaDeletionRequestStore(
    private val repository: DeletionRequestRepository,
    private val profileStore: ProfileStore,
    private val profileRepository: ProfileRepository,
    private val authSessionRepository: AuthSessionRepository,
    private val clock: () -> Instant = Instant::now
) : DeletionRequestStore {

    /**
     * Records a new deletion request for the subject or returns an existing one.
     * Preserves profile record with `deletionRequested = true` for financial attribution.
     *
     * @param subject OIDC subject identifier.
     * @return [DeletionRequest] representing the saved or existing request.
     */
    @DbWriter("accounts.deletion.request")
    @Transactional
    override fun request(subject: String): DeletionRequest {
        ProfileRules.requireSubject(subject)
        val existing = repository.findById(subject).orElse(null)
        if (existing != null) {
            return existing.toRecord()
        }

        // Retain historical financial attribution by preserving profile record with deletionRequested = true
        val account = profileRepository.findBySubject(subject)
        profileStore.requestDeletion(subject)
        if (account != null) {
            authSessionRepository.revokeAllForAccount(account.accountId, clock())
        }

        val entity = AccountDeletionRequestEntity(
            subject = subject,
            status = DeletionStatus.REQUESTED,
            requestedAt = clock()
        )
        return repository.save(entity).toRecord()
    }

    /**
     * Retrieves the deletion request entity for the given subject.
     *
     * @param subject OIDC subject identifier.
     * @return [DeletionRequest] or null if not found.
     */
    @DbReader("accounts.deletion.get")
    @Transactional(readOnly = true)
    override fun get(subject: String): DeletionRequest? {
        ProfileRules.requireSubject(subject)
        return repository.findById(subject).map { it.toRecord() }.orElse(null)
    }

    /**
     * Cancels an existing deletion request for the given subject.
     *
     * @param subject OIDC subject identifier.
     * @return Updated [DeletionRequest] with CANCELLED status, or null if none existed.
     */
    @DbWriter("accounts.deletion.cancel")
    @Transactional
    override fun cancel(subject: String): DeletionRequest? {
        ProfileRules.requireSubject(subject)
        val entity = repository.findById(subject).orElse(null) ?: return null
        entity.status = DeletionStatus.CANCELLED
        return repository.save(entity).toRecord()
    }

    /**
     * Marks an existing deletion request as completed.
     *
     * @param subject OIDC subject identifier.
     * @return Updated [DeletionRequest] with COMPLETED status, or null if none existed.
     */
    @DbWriter("accounts.deletion.complete")
    @Transactional
    override fun complete(subject: String): DeletionRequest? {
        ProfileRules.requireSubject(subject)
        val entity = repository.findById(subject).orElse(null) ?: return null
        entity.status = DeletionStatus.COMPLETED
        return repository.save(entity).toRecord()
    }
}

/**
 * Maps [AccountDeletionRequestEntity] to [DeletionRequest] domain model.
 */
private fun AccountDeletionRequestEntity.toRecord() = DeletionRequest(
    subject = subject,
    requestedAt = requestedAt,
    status = status
)
