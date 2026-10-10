package com.subhrodip.squarewise.accounts.requests.export.persistence

import com.subhrodip.squarewise.accounts.requests.export.model.ExportStatus
import com.subhrodip.squarewise.accounts.errors.AccountsDomainException
import java.time.Instant
import java.util.Optional
import java.util.UUID
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.mockito.Mockito.doReturn
import org.mockito.Mockito.mock
import org.springframework.data.domain.PageRequest

/** Verifies the JPA export adapter validates subjects and maps bounded repository results. */
class JpaExportRequestStoreTest {
    private val repository = mock(ExportRequestRepository::class.java)
    private val request = AccountExportRequestEntity(
        exportId = UUID.randomUUID(),
        subject = "alice",
        status = ExportStatus.REQUESTED,
        requestedAt = Instant.EPOCH,
    )
    private val store = JpaExportRequestStore(repository) { Instant.EPOCH }

    @Test
    fun `lists maps and retrieves requests for a subject`() {
        doReturn(listOf(request)).`when`(repository).findBySubjectOrderByRequestedAtDesc("alice", PageRequest.of(0, 100))
        doReturn(listOf(request)).`when`(repository).findBySubjectOrderByRequestedAtDesc("alice", PageRequest.of(0, 1))
        doReturn(Optional.of(request)).`when`(repository).findById(request.exportId)
        val missingId = UUID.randomUUID()
        doReturn(Optional.empty<AccountExportRequestEntity>()).`when`(repository).findById(missingId)

        assertEquals(listOf(request.exportId), store.listBySubject("alice").map { it.exportId })
        assertEquals(listOf(request.exportId), store.listBySubject("alice", 1).map { it.exportId })
        assertEquals(request.exportId, store.get(request.exportId)?.exportId)
        assertNull(store.get(missingId))
    }

    @Test
    fun `rejects invalid subjects and page sizes before repository access`() {
        assertThrows(AccountsDomainException::class.java) { store.listBySubject(" ") }
        assertThrows(IllegalArgumentException::class.java) { store.listBySubject("alice", 0) }
        assertThrows(IllegalArgumentException::class.java) { store.listBySubject("alice", 101) }
    }
}
