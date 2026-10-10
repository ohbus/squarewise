package com.subhrodip.squarewise.accounts.requests.export.service

import com.subhrodip.squarewise.accounts.requests.export.model.ExportRequest
import com.subhrodip.squarewise.accounts.requests.export.persistence.ExportRequestStore
import java.util.UUID
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service

@Service
class ExportRequestService @Autowired constructor(
    private val store: ExportRequestStore
) {
    fun request(subject: String): ExportRequest = store.request(subject)
    fun get(exportId: UUID): ExportRequest? = store.get(exportId)
    fun listBySubject(subject: String, limit: Int = 100): List<ExportRequest> = store.listBySubject(subject, limit)
}
