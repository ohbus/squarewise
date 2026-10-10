package com.subhrodip.squarewise.accounts.requests.export.persistence

import com.subhrodip.squarewise.accounts.requests.export.model.ExportRequest
import java.util.UUID

/**
 * Port interface for managing and persisting account data export requests.
 */
interface ExportRequestStore {
    /**
     * Creates and stores a new export request for the given subject.
     *
     * @param subject OIDC subject identifier.
     * @return [ExportRequest] representing the newly created request.
     */
    fun request(subject: String): ExportRequest

    /**
     * Retrieves an export request by its unique identifier.
     *
     * @param exportId Unique identifier of the export request.
     * @return [ExportRequest] or null if not found.
     */
    fun get(exportId: UUID): ExportRequest?

    /**
     * Lists all export requests for a subject, sorted in reverse chronological order.
     *
     * @param subject OIDC subject identifier.
     * @return List of [ExportRequest]s.
     */
    fun listBySubject(subject: String): List<ExportRequest>

    /** Lists at most [limit] newest requests without loading unbounded history. */
    fun listBySubject(subject: String, limit: Int): List<ExportRequest> = listBySubject(subject).take(limit)
}
