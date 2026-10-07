package com.subhrodip.squarewise.accounts

import com.subhrodip.squarewise.accounts.requests.deletion.persistence.DeletionRequestRepository
import com.subhrodip.squarewise.accounts.requests.deletion.persistence.JpaDeletionRequestStore
import com.subhrodip.squarewise.accounts.requests.deletion.model.DeletionStatus
import com.subhrodip.squarewise.accounts.requests.export.persistence.ExportRequestRepository
import com.subhrodip.squarewise.accounts.requests.export.model.ExportStatus
import com.subhrodip.squarewise.accounts.requests.export.persistence.JpaExportRequestStore
import com.subhrodip.squarewise.accounts.profile.api.ProfilePatchRequest
import com.subhrodip.squarewise.accounts.profile.persistence.JpaProfileStore
import com.subhrodip.squarewise.accounts.profile.persistence.ProfileRepository
import com.subhrodip.squarewise.accounts.auth.session.AuthSessionEntity
import com.subhrodip.squarewise.accounts.auth.session.AuthSessionRepository
import com.subhrodip.squarewise.errors.exceptions.SquarewiseException

import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.transaction.annotation.Transactional

@SpringBootTest
@Transactional
class JpaRequestStoresTest @Autowired constructor(
    private val deletionStore: JpaDeletionRequestStore,
    private val exportStore: JpaExportRequestStore,
    private val profileStore: JpaProfileStore,
    private val profileRepository: ProfileRepository,
    private val authSessionRepository: AuthSessionRepository,
    private val deletionRepository: DeletionRequestRepository,
    private val exportRepository: ExportRequestRepository
) {

    @Test
    fun `persists deletion request idempotently and preserves historical financial profile reference`() {
        val subject = "oidc|user-${UUID.randomUUID()}"

        // Explicit profile creation
        val profile = profileStore.create(UUID.randomUUID(), subject, subject)
        assertEquals(subject, profile.displayName)

        val now = Instant.now()
        authSessionRepository.save(AuthSessionEntity(
            sessionId = UUID.randomUUID(),
            accountId = profile.accountId,
            familyId = UUID.randomUUID(),
            refreshTokenDigest = byteArrayOf(21, 22, 23),
            createdAt = now,
            lastUsedAt = now,
            expiresAt = now.plus(30, ChronoUnit.DAYS),
            absoluteExpiresAt = now.plus(90, ChronoUnit.DAYS),
            clientKind = "NATIVE"
        ))

        // Request deletion
        val req1 = deletionStore.request(subject)
        assertEquals(subject, req1.subject)
        assertEquals(DeletionStatus.REQUESTED, req1.status)
        assertNotNull(authSessionRepository.findAll().single().revokedAt)

        // Verify entity in database
        val entity = deletionRepository.findById(subject).orElse(null)
        assertNotNull(entity)
        assertEquals(DeletionStatus.REQUESTED, entity?.status)

        // Verify profile record is preserved for financial attribution but marked deletion_requested = true
        val storedProfile = profileRepository.findBySubject(subject)
        assertNotNull(storedProfile)
        assertEquals(profile.accountId, storedProfile?.accountId)
        assertTrue(storedProfile?.deletionRequested == true)

        // Idempotency: duplicate request returns existing record without duplicating or throwing
        val req2 = deletionStore.request(subject)
        assertEquals(req1.subject, req2.subject)
        assertEquals(req1.requestedAt, req2.requestedAt)
        assertEquals(req1.status, req2.status)
        assertEquals(1, deletionRepository.findAll().count { it.subject == subject })

        // Lifecycle transitions: cancel
        val cancelled = deletionStore.cancel(subject)
        assertEquals(DeletionStatus.CANCELLED, cancelled?.status)
        assertEquals(DeletionStatus.CANCELLED, deletionRepository.findById(subject).get().status)

        // Lifecycle transitions: complete
        val completed = deletionStore.complete(subject)
        assertEquals(DeletionStatus.COMPLETED, completed?.status)
        assertEquals(DeletionStatus.COMPLETED, deletionRepository.findById(subject).get().status)
    }

    @Test
    fun `persists and queries export requests by id and subject`() {
        val subject = "oidc|user-${UUID.randomUUID()}"

        val exp1 = exportStore.request(subject)
        assertNotNull(exp1.exportId)
        assertEquals(subject, exp1.subject)
        assertEquals(ExportStatus.REQUESTED, exp1.status)

        val exp2 = exportStore.request(subject)
        assertNotNull(exp2.exportId)
        assertTrue(exp1.exportId != exp2.exportId)

        // Query by id
        val retrieved = exportStore.get(exp1.exportId)
        assertNotNull(retrieved)
        assertEquals(exp1.exportId, retrieved?.exportId)
        assertEquals(ExportStatus.REQUESTED, retrieved?.status)

        // Non-existent id
        assertNull(exportStore.get(UUID.randomUUID()))

        // List by subject
        val list = exportStore.listBySubject(subject)
        assertEquals(2, list.size)
        assertEquals(listOf(exp2.exportId, exp1.exportId), list.map { it.exportId })
    }

    @Test
    fun `finds profile by account id`() {
        val subject = "oidc|user-${UUID.randomUUID()}"
        val profile = profileStore.create(UUID.randomUUID(), subject, subject)

        val found = profileStore.findById(profile.accountId)
        assertNotNull(found)
        assertEquals(profile.accountId, found?.accountId)
        assertEquals(profile.displayName, found?.displayName)
        assertEquals(profile.timezone, found?.timezone)
        assertEquals(profile.defaultCurrency, found?.defaultCurrency)

        val notFound = profileStore.findById(UUID.randomUUID())
        assertNull(notFound)
    }

    @Test
    fun `finds profiles in batch by account ids`() {
        val subject1 = "oidc|user-${UUID.randomUUID()}"
        val subject2 = "oidc|user-${UUID.randomUUID()}"
        val p1 = profileStore.create(UUID.randomUUID(), subject1, subject1)
        val p2 = profileStore.create(UUID.randomUUID(), subject2, subject2)

        val batch = profileStore.findByIds(listOf(p1.accountId, p2.accountId, UUID.randomUUID()))
        assertEquals(2, batch.size)
        val ids = batch.map { it.accountId }.toSet()
        assertTrue(ids.contains(p1.accountId))
        assertTrue(ids.contains(p2.accountId))
    }

    @Test
    fun `gets profiles without provisioning and rejects invalid subjects`() {
        val subject = "oidc|user-${UUID.randomUUID()}"
        val profile = profileStore.create(UUID.randomUUID(), subject, "Alice")

        val found = profileStore.get(subject)
        assertNotNull(found)
        assertEquals(profile.accountId, found?.accountId)
        assertNull(profileStore.get("oidc|missing-${UUID.randomUUID()}"))
        assertThrows(SquarewiseException::class.java) { profileStore.get(" ") }
        assertEquals(1, profileRepository.findAll().count { it.subject == subject })
    }

    @Test
    fun `updates each profile field and fails closed for missing profiles`() {
        val subject = "oidc|user-${UUID.randomUUID()}"
        val profile = profileStore.create(UUID.randomUUID(), subject, "Alice")

        val updated = profileStore.update(
            subject,
            ProfilePatchRequest(displayName = "Alicia", timezone = "Europe/Vienna", defaultCurrency = "USD")
        )
        assertEquals("Alicia", updated.displayName)
        assertEquals("Europe/Vienna", updated.timezone)
        assertEquals("USD", updated.defaultCurrency)

        val timezoneOnly = profileStore.update(subject, ProfilePatchRequest(timezone = "UTC"))
        assertEquals("Alicia", timezoneOnly.displayName)
        assertEquals("UTC", timezoneOnly.timezone)
        assertEquals("USD", timezoneOnly.defaultCurrency)

        val displayNameOnly = profileStore.update(subject, ProfilePatchRequest(displayName = "Alice Again"))
        assertEquals("Alice Again", displayNameOnly.displayName)
        assertEquals("UTC", displayNameOnly.timezone)
        assertEquals("USD", displayNameOnly.defaultCurrency)

        assertThrows(SquarewiseException::class.java) {
            profileStore.update(subject, ProfilePatchRequest(timezone = "Not/AZone"))
        }
        assertThrows(SquarewiseException::class.java) {
            profileStore.update(" ", ProfilePatchRequest(displayName = "Ignored"))
        }
        assertThrows(SquarewiseException::class.java) {
            profileStore.update("oidc|missing-${UUID.randomUUID()}", ProfilePatchRequest(displayName = "Missing"))
        }
        assertEquals(profile.accountId, profileStore.get(subject)?.accountId)
    }

    @Test
    fun `marks an existing profile for deletion and rejects missing profiles`() {
        val subject = "oidc|user-${UUID.randomUUID()}"
        val profile = profileStore.create(UUID.randomUUID(), subject, "Alice")

        profileStore.requestDeletion(subject)
        assertTrue(profileRepository.findById(profile.accountId).orElseThrow().deletionRequested)

        assertThrows(SquarewiseException::class.java) { profileStore.requestDeletion(" ") }
        assertThrows(SquarewiseException::class.java) {
            profileStore.requestDeletion("oidc|missing-${UUID.randomUUID()}")
        }
    }

    @Test
    fun `returns null for missing deletion records and rejects blank subjects`() {
        val missingSubject = "oidc|missing-${UUID.randomUUID()}"

        assertNull(deletionStore.get(missingSubject))
        assertNull(deletionStore.cancel(missingSubject))
        assertNull(deletionStore.complete(missingSubject))
        assertThrows(SquarewiseException::class.java) { deletionStore.get(" ") }
        assertThrows(SquarewiseException::class.java) { deletionStore.cancel(" ") }
        assertThrows(SquarewiseException::class.java) { deletionStore.complete(" ") }
    }
}
