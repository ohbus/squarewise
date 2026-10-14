package com.subhrodip.squarewise.accounts.requests.export.persistence

import com.subhrodip.squarewise.accounts.requests.export.model.ExportRequest
import com.subhrodip.squarewise.accounts.requests.export.model.ExportStatus
import com.subhrodip.squarewise.accounts.profile.service.ProfileRules
import com.subhrodip.squarewise.ids.generation.UuidGenerator
import org.springframework.context.annotation.Primary
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

import com.subhrodip.squarewise.accounts.validation.AccountReferenceValidator
import org.springframework.beans.factory.annotation.Autowired

/**
 * JPA-backed implementation of [ExportRequestStore].
 *
 * Persists data export requests to PostgreSQL via [ExportRequestRepository].
 *
 * @param repository Spring Data JPA repository for export requests.
 * @param referenceValidator Application-layer referential validator ensuring account active status.
 * @param clock Timestamp supplier, defaulting to [Instant.now].
 */
@Primary
@Service
class JpaExportRequestStore(
    private val repository: ExportRequestRepository,
    @Autowired(required = false)
    private val referenceValidator: AccountReferenceValidator? = null,
    private val clock: () -> Instant = Instant::now
) : ExportRequestStore {

    /**
     * Creates and persists a new export request with a newly generated UUID.
     *
     * @param subject OIDC subject identifier.
     * @return Saved [ExportRequest].
     */
    @Transactional
    override fun request(subject: String): ExportRequest {
        ProfileRules.requireSubject(subject)
        referenceValidator?.validateActiveSubject(subject)
        val entity = AccountExportRequestEntity(
            exportId = UuidGenerator.next(),
            subject = subject,
            status = ExportStatus.REQUESTED,
            requestedAt = clock()
        )
        return repository.save(entity).toRecord()
    }

    /**
     * Retrieves an export request by ID.
     *
     * @param exportId Unique identifier of the export request.
     * @return [ExportRequest] or null if not found.
     */
    @Transactional(readOnly = true)
    override fun get(exportId: UUID): ExportRequest? {
        return repository.findById(exportId).map { it.toRecord() }.orElse(null)
    }

    /**
     * Retrieves all export requests for a subject ordered by requested timestamp descending.
     *
     * @param subject OIDC subject identifier.
     * @return List of matching [ExportRequest]s.
     */
    @Transactional(readOnly = true)
    override fun listBySubject(subject: String): List<ExportRequest> {
        ProfileRules.requireSubject(subject)
        return repository.findBySubjectOrderByRequestedAtDesc(subject, PageRequest.of(0, 100)).map { it.toRecord() }
    }

    /** Reads a bounded newest-first export history page directly from the database. */
    @Transactional(readOnly = true)
    override fun listBySubject(subject: String, limit: Int): List<ExportRequest> {
        return listBySubject(subject, 0, limit)
    }

    /** Reads a bounded newest-first export history page directly from the database. */
    @Transactional(readOnly = true)
    override fun listBySubject(subject: String, page: Int, limit: Int): List<ExportRequest> {
        require(page >= 0) { "export history page must not be negative" }
        require(limit in 1..100) { "export history limit must be between 1 and 100" }
        ProfileRules.requireSubject(subject)
        return repository.findBySubjectOrderByRequestedAtDesc(subject, PageRequest.of(page, limit)).map { it.toRecord() }
    }
}

/**
 * Maps [AccountExportRequestEntity] to [ExportRequest] domain model.
 */
private fun AccountExportRequestEntity.toRecord() = ExportRequest(
    exportId = exportId,
    subject = subject,
    requestedAt = requestedAt,
    status = status
)
