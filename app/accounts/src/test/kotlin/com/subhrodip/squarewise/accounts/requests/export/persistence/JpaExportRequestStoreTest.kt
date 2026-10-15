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
        doReturn(listOf(request)).`when`(repository).findBySubjectOrderByRequestedAtDesc("alice", PageRequest.of(1, 1))
        doReturn(Optional.of(request)).`when`(repository).findById(request.exportId)
        val missingId = UUID.randomUUID()
        doReturn(Optional.empty<AccountExportRequestEntity>()).`when`(repository).findById(missingId)

        assertEquals(listOf(request.exportId), store.listBySubject("alice").map { it.exportId })
        assertEquals(listOf(request.exportId), store.listBySubject("alice", 1).map { it.exportId })
        assertEquals(listOf(request.exportId), store.listBySubject("alice", 1, 1).map { it.exportId })
        assertEquals(request.exportId, store.get(request.exportId)?.exportId)
        assertNull(store.get(missingId))
    }

    @Test
    fun `rejects invalid subjects and page sizes before repository access`() {
        assertThrows(AccountsDomainException::class.java) { store.listBySubject(" ") }
        assertThrows(IllegalArgumentException::class.java) { store.listBySubject("alice", 0) }
        assertThrows(IllegalArgumentException::class.java) { store.listBySubject("alice", 101) }
        assertThrows(IllegalArgumentException::class.java) { store.listBySubject("alice", -1, 1) }
    }

    @Test
    fun `default store interface implementations delegate correctly`() {
        val domainRequest = com.subhrodip.squarewise.accounts.requests.export.model.ExportRequest(
            exportId = request.exportId,
            subject = "alice",
            requestedAt = Instant.EPOCH,
            status = ExportStatus.REQUESTED
        )
        val simpleStore = object : ExportRequestStore {
            override fun request(subject: String) = domainRequest
            override fun get(exportId: UUID) = domainRequest
            override fun listBySubject(subject: String) = listOf(domainRequest, domainRequest)
        }
        val defaultImplsClass = Class.forName("com.subhrodip.squarewise.accounts.requests.export.persistence.ExportRequestStore\$DefaultImpls")
        val listLimitMethod = defaultImplsClass.getMethod("listBySubject", ExportRequestStore::class.java, String::class.java, Int::class.javaPrimitiveType)
        val listPageMethod = defaultImplsClass.getMethod("listBySubject", ExportRequestStore::class.java, String::class.java, Int::class.javaPrimitiveType, Int::class.javaPrimitiveType)

        assertEquals(1, (listLimitMethod.invoke(null, simpleStore, "alice", 1) as List<*>).size)
        assertEquals(1, (listPageMethod.invoke(null, simpleStore, "alice", 0, 1) as List<*>).size)
    }

    @Test
    fun `request succeeds and persists when subject is active`() {
        val validator = mock(com.subhrodip.squarewise.accounts.validation.AccountReferenceValidator::class.java)
        val validatedStore = JpaExportRequestStore(repository, validator) { Instant.EPOCH }
        val savedEntity = AccountExportRequestEntity(
            exportId = UUID.randomUUID(),
            subject = "alice",
            status = ExportStatus.REQUESTED,
            requestedAt = Instant.EPOCH
        )
        doReturn(savedEntity).`when`(repository).save(org.mockito.ArgumentMatchers.any(AccountExportRequestEntity::class.java))

        val result = validatedStore.request("alice")

        assertEquals("alice", result.subject)
        assertEquals(ExportStatus.REQUESTED, result.status)
        org.mockito.Mockito.verify(validator).validateActiveSubject("alice")
    }

    @Test
    fun `request propagates exception when validator rejects inactive subject`() {
        val validator = mock(com.subhrodip.squarewise.accounts.validation.AccountReferenceValidator::class.java)
        val validatedStore = JpaExportRequestStore(repository, validator) { Instant.EPOCH }
        org.mockito.Mockito.`when`(validator.validateActiveSubject("deleted"))
            .thenThrow(AccountsDomainException(com.subhrodip.squarewise.errors.catalog.AccountsErrors.PROFILE_REQUEST_INVALID))

        val ex = assertThrows(AccountsDomainException::class.java) {
            validatedStore.request("deleted")
        }
        assertEquals(com.subhrodip.squarewise.errors.catalog.AccountsErrors.PROFILE_REQUEST_INVALID, ex.definition)
    }

    @Test
    fun `request succeeds without referenceValidator`() {
        val savedEntity = AccountExportRequestEntity(
            exportId = UUID.randomUUID(),
            subject = "alice",
            status = ExportStatus.REQUESTED,
            requestedAt = Instant.EPOCH
        )
        doReturn(savedEntity).`when`(repository).save(org.mockito.ArgumentMatchers.any(AccountExportRequestEntity::class.java))

        val result = store.request("alice")

        assertEquals("alice", result.subject)
    }
}
