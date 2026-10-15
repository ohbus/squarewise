package com.subhrodip.squarewise.accounts.requests.export

import com.subhrodip.squarewise.accounts.requests.export.model.ExportStatus
import com.subhrodip.squarewise.accounts.requests.export.service.ExportRequestService
import com.subhrodip.squarewise.accounts.requests.export.persistence.InMemoryExportRequestStore

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.Instant

class ExportRequestServiceTest {
    @Test
    fun `creates export request with stable id and requested status`() {
        val service = ExportRequestService(InMemoryExportRequestStore { Instant.EPOCH })
        val request = service.request("alice")
        assertEquals("alice", request.subject)
        assertEquals(ExportStatus.REQUESTED, request.status)
        assertEquals(request, service.get(request.exportId))
        assertEquals(listOf(request), service.listBySubject("alice"))
        assertEquals(listOf(request), service.listBySubject("alice", 10))
        assertEquals(listOf(request), service.listBySubject("alice", 0, 10))
    }
}
