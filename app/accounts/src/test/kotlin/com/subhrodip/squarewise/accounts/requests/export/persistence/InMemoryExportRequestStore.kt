package com.subhrodip.squarewise.accounts.requests.export.persistence

import com.subhrodip.squarewise.accounts.requests.export.model.ExportRequest
import com.subhrodip.squarewise.accounts.profile.service.ProfileRules
import com.subhrodip.squarewise.ids.generation.UuidGenerator
import java.time.Instant
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/** Thread-safe in-memory export-request adapter for local and test use. */
class InMemoryExportRequestStore(
    private val clock: () -> Instant = Instant::now
) : ExportRequestStore {
    private val requests = ConcurrentHashMap<UUID, ExportRequest>()

    override fun request(subject: String): ExportRequest {
        ProfileRules.requireSubject(subject)
        val request = ExportRequest(UuidGenerator.next(), subject, clock())
        requests[request.exportId] = request
        return request
    }

    override fun get(exportId: UUID): ExportRequest? = requests[exportId]

    override fun listBySubject(subject: String): List<ExportRequest> {
        ProfileRules.requireSubject(subject)
        return requests.values.filter { it.subject == subject }.sortedByDescending { it.requestedAt }
    }

    override fun listBySubject(subject: String, page: Int, limit: Int): List<ExportRequest> {
        require(page >= 0)
        require(limit in 1..100)
        return listBySubject(subject).drop(page * limit).take(limit)
    }
}
