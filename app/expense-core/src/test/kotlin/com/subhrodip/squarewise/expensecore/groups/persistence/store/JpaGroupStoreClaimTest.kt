package com.subhrodip.squarewise.expensecore.groups.persistence.store

import com.subhrodip.squarewise.errors.code.CategoryCode

import com.subhrodip.squarewise.errors.exceptions.SquarewiseException
import com.subhrodip.squarewise.expensecore.groups.domain.GroupEntity
import com.subhrodip.squarewise.expensecore.groups.domain.GroupInvitationEntity
import com.subhrodip.squarewise.expensecore.groups.domain.GroupMembershipEntity
import com.subhrodip.squarewise.expensecore.groups.api.CreateInviteRequest
import com.subhrodip.squarewise.expensecore.groups.api.UpdateGroupRequest
import com.subhrodip.squarewise.expensecore.groups.persistence.repository.GroupInvitationRepository
import com.subhrodip.squarewise.expensecore.groups.persistence.repository.GroupMembershipRepository
import com.subhrodip.squarewise.expensecore.groups.persistence.repository.GroupRepository
import com.subhrodip.squarewise.expensecore.messaging.outbox.persistence.OutboxStore
import com.subhrodip.squarewise.expensecore.sync.persistence.SynchronizationStore
import java.time.Instant
import java.util.Optional
import java.util.UUID
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock

/**
 * Unit coverage for [JpaGroupStore.claim] outcomes that require a repository race or missing row
 * and therefore cannot be created honestly through the normal JPA integration fixture.
 */
class JpaGroupStoreClaimTest {
    private val groups = mock(GroupRepository::class.java)
    private val memberships = mock(GroupMembershipRepository::class.java)
    private val invitations = mock(GroupInvitationRepository::class.java)
    private val audit = mock(GroupAuditCommandStore::class.java)
    private val synchronization = mock(SynchronizationStore::class.java)
    private val outbox = mock(OutboxStore::class.java)
    private val store = JpaGroupStore(groups, memberships, invitations, audit, synchronization, outbox)

    @Test
    fun `rejects claim when invitation group disappeared`() {
        val groupId = UUID.randomUUID()
        val token = "a".repeat(64)
        val invitation = invitation(token, groupId)
        `when`(invitations.findById(token)).thenReturn(Optional.of(invitation))
        `when`(groups.findForMembershipUpdate(groupId)).thenReturn(null)

        val error = assertThrows<SquarewiseException> {
            store.claim(token, "invitee")
        }

        assertEquals(CategoryCode.NOT_FOUND, error.definition.category)
    }

    @Test
    fun `rejects claim when targeted placeholder disappeared`() {
        val groupId = UUID.randomUUID()
        val placeholderId = UUID.randomUUID()
        val token = "b".repeat(64)
        val invitation = invitation(token, groupId, placeholderId)
        `when`(invitations.findById(token)).thenReturn(Optional.of(invitation))
        `when`(groups.findForMembershipUpdate(groupId)).thenReturn(GroupEntity(groupId, "Trip", "TRIP", "EUR"))
        `when`(memberships.findByMembershipIdAndGroupId(placeholderId, groupId)).thenReturn(null)

        val error = assertThrows<SquarewiseException> {
            store.claim(token, "invitee")
        }

        assertEquals(CategoryCode.STATE_CONFLICT, error.definition.category)
    }

    /** Verifies a normal invitation race fails before creating a membership or side effects. */
    @Test
    fun `rejects normal claim when atomic invitation update loses the race`() {
        val groupId = UUID.randomUUID()
        val token = "c".repeat(64)
        val invitation = invitation(token, groupId)
        `when`(invitations.findById(token)).thenReturn(Optional.of(invitation))
        `when`(groups.findForMembershipUpdate(groupId)).thenReturn(GroupEntity(groupId, "Trip", "TRIP", "EUR"))
        `when`(memberships.existsByGroupIdAndSubjectAndStatus(groupId, "invitee", "ACTIVE"))
            .thenReturn(false)

        val error = assertThrows<SquarewiseException> {
            store.claim(token, "invitee")
        }

        assertEquals(CategoryCode.STATE_CONFLICT, error.definition.category)
    }

    /** Verifies a targeted placeholder race fails before binding the placeholder. */
    @Test
    fun `rejects targeted claim when atomic invitation update loses the race`() {
        val groupId = UUID.randomUUID()
        val placeholderId = UUID.randomUUID()
        val token = "d".repeat(64)
        val invitation = invitation(token, groupId, placeholderId)
        `when`(invitations.findById(token)).thenReturn(Optional.of(invitation))
        `when`(groups.findForMembershipUpdate(groupId)).thenReturn(GroupEntity(groupId, "Trip", "TRIP", "EUR"))
        `when`(memberships.findByMembershipIdAndGroupId(placeholderId, groupId)).thenReturn(
            GroupMembershipEntity(
                membershipId = placeholderId,
                groupId = groupId,
                subject = null,
                isPlaceholder = true,
                status = "ACTIVE"
            )
        )

        val error = assertThrows<SquarewiseException> {
            store.claim(token, "invitee")
        }

        assertEquals(CategoryCode.STATE_CONFLICT, error.definition.category)
    }

    @Test
    fun `rejects revocation when atomic invitation update loses the race`() {
        val groupId = UUID.randomUUID()
        val token = "e".repeat(64)
        `when`(memberships.existsByGroupIdAndSubjectAndStatus(groupId, "owner", "ACTIVE"))
            .thenReturn(true)
        `when`(groups.findForMembershipUpdate(groupId))
            .thenReturn(GroupEntity(groupId, "Trip", "TRIP", "EUR"))
        `when`(invitations.revokeIfAvailable(token, groupId, Instant.now()))
            .thenReturn(0)

        val error = assertThrows<SquarewiseException> {
            store.revokeInvite(groupId, "owner", token)
        }

        assertEquals(CategoryCode.STATE_CONFLICT, error.definition.category)
    }

    @Test
    fun `rejects an already invalid invitation before group lookup`() {
        val groupId = UUID.randomUUID()
        val token = "f".repeat(64)
        val invitation = invitation(token, groupId).apply { revokedAt = Instant.now() }
        `when`(invitations.findById(token)).thenReturn(Optional.of(invitation))

        val error = assertThrows<SquarewiseException> {
            store.claim(token, "invitee")
        }

        assertEquals(CategoryCode.STATE_CONFLICT, error.definition.category)
    }

    @Test
    fun `rejects claim when targeted placeholder is no longer available`() {
        val groupId = UUID.randomUUID()
        val placeholderId = UUID.randomUUID()
        val token = "1".repeat(64)
        `when`(invitations.findById(token)).thenReturn(Optional.of(invitation(token, groupId, placeholderId)))
        `when`(groups.findForMembershipUpdate(groupId)).thenReturn(GroupEntity(groupId, "Trip", "TRIP", "EUR"))
        `when`(memberships.findByMembershipIdAndGroupId(placeholderId, groupId)).thenReturn(
            GroupMembershipEntity(
                membershipId = placeholderId,
                groupId = groupId,
                subject = "already-bound",
                isPlaceholder = true,
                status = "ACTIVE"
            )
        )

        val error = assertThrows<SquarewiseException> {
            store.claim(token, "invitee")
        }

        assertEquals(CategoryCode.STATE_CONFLICT, error.definition.category)
    }

    @Test
    fun `rejects group mutation when subject is not an active member`() {
        val groupId = UUID.randomUUID()
        `when`(memberships.existsByGroupIdAndSubjectAndStatus(groupId, "outsider", "ACTIVE"))
            .thenReturn(false)

        val error = assertThrows<SquarewiseException> {
            store.update(groupId, "outsider", UpdateGroupRequest("Renamed"))
        }

        assertEquals(CategoryCode.NOT_FOUND, error.definition.category)
    }

    private fun invitation(token: String, groupId: UUID, placeholderId: UUID? = null): GroupInvitationEntity =
        GroupInvitationEntity(
            token = token,
            groupId = groupId,
            expiresAt = Instant.now().plusSeconds(3600),
            placeholderId = placeholderId
        )
}
