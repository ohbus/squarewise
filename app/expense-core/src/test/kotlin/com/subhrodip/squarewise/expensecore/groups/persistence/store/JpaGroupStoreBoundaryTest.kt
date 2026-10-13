package com.subhrodip.squarewise.expensecore.groups.persistence.store

import com.subhrodip.squarewise.errors.code.CategoryCode
import com.subhrodip.squarewise.errors.exceptions.SquarewiseException
import com.subhrodip.squarewise.expensecore.groups.api.CreateInviteRequest
import com.subhrodip.squarewise.expensecore.groups.api.CreatePlaceholderRequest
import com.subhrodip.squarewise.expensecore.groups.api.UpdateGroupRequest
import com.subhrodip.squarewise.expensecore.groups.domain.GroupEntity
import com.subhrodip.squarewise.expensecore.groups.domain.GroupMembershipEntity
import com.subhrodip.squarewise.expensecore.groups.persistence.repository.GroupInvitationRepository
import com.subhrodip.squarewise.expensecore.groups.persistence.repository.GroupMembershipRepository
import com.subhrodip.squarewise.expensecore.groups.persistence.repository.GroupRepository
import com.subhrodip.squarewise.expensecore.messaging.outbox.persistence.OutboxStore
import com.subhrodip.squarewise.expensecore.sync.persistence.SynchronizationStore
import java.util.Optional
import java.util.UUID
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock

/** Covers repository-race and state boundaries in the JPA group adapter. */
class JpaGroupStoreBoundaryTest {
    private val groups = mock(GroupRepository::class.java)
    private val memberships = mock(GroupMembershipRepository::class.java)
    private val invitations = mock(GroupInvitationRepository::class.java)
    private val audit = mock(GroupAuditCommandStore::class.java)
    private val synchronization = mock(SynchronizationStore::class.java)
    private val outbox = mock(OutboxStore::class.java)
    private val store = JpaGroupStore(groups, memberships, invitations, audit, synchronization, outbox)

    @Test
    fun `mutation guards hide missing locked groups`() {
        val groupId = UUID.randomUUID()
        `when`(memberships.existsByGroupIdAndSubjectAndStatus(groupId, "alice", "ACTIVE")).thenReturn(true)
        `when`(groups.findForMembershipUpdate(groupId)).thenReturn(null)

        assertNotFound { store.update(groupId, "alice", UpdateGroupRequest("Renamed")) }
        assertNotFound { store.archive(groupId, "alice") }
        assertNotFound { store.addPlaceholder(groupId, "alice", CreatePlaceholderRequest("Placeholder")) }
        val memberId = UUID.randomUUID()
        `when`(memberships.findByMembershipIdAndGroupId(memberId, groupId)).thenReturn(
            GroupMembershipEntity(memberId, groupId, subject = "bob", status = "ACTIVE")
        )
        assertNotFound { store.removeMember(groupId, "alice", memberId) }
        assertNotFound { store.revokeInvite(groupId, "alice", "a".repeat(64)) }
        `when`(groups.findById(groupId)).thenReturn(Optional.empty())
        assertNotFound { store.invite(groupId, "alice", CreateInviteRequest(24)) }
    }

    @Test
    fun `member reads reject missing and archived groups`() {
        val groupId = UUID.randomUUID()
        `when`(memberships.existsByGroupIdAndSubjectAndStatus(groupId, "alice", "ACTIVE")).thenReturn(true)
        `when`(groups.findById(groupId)).thenReturn(Optional.empty())
        assertNotFound { store.listMembers(groupId, "alice", 100) }

        `when`(groups.findById(groupId)).thenReturn(Optional.of(GroupEntity(groupId, "Trip", "TRIP", "EUR", "ARCHIVED")))
        assertNotFound { store.listMembers(groupId, "alice", 100) }
    }

    @Test
    fun `member removal rejects missing and already removed targets`() {
        val groupId = UUID.randomUUID()
        val membershipId = UUID.randomUUID()
        `when`(memberships.existsByGroupIdAndSubjectAndStatus(groupId, "alice", "ACTIVE")).thenReturn(true)
        `when`(groups.findForMembershipUpdate(groupId)).thenReturn(GroupEntity(groupId, "Trip", "TRIP", "EUR"))
        `when`(memberships.findByMembershipIdAndGroupId(membershipId, groupId)).thenReturn(null)
        assertNotFound { store.removeMember(groupId, "alice", membershipId) }

        `when`(memberships.findByMembershipIdAndGroupId(membershipId, groupId)).thenReturn(
            GroupMembershipEntity(membershipId, groupId, subject = "bob", status = "REMOVED")
        )
        val error = assertThrows<SquarewiseException> { store.removeMember(groupId, "alice", membershipId) }
        assertEquals(CategoryCode.STATE_CONFLICT, error.definition.category)
    }

    @Test
    fun `invite rejects missing and unavailable placeholders`() {
        val groupId = UUID.randomUUID()
        val placeholderId = UUID.randomUUID()
        `when`(memberships.existsByGroupIdAndSubjectAndStatus(groupId, "alice", "ACTIVE")).thenReturn(true)
        `when`(groups.findById(groupId)).thenReturn(Optional.empty())
        assertNotFound { store.invite(groupId, "alice", CreateInviteRequest(24)) }

        `when`(groups.findById(groupId)).thenReturn(Optional.of(GroupEntity(groupId, "Trip", "TRIP", "EUR")))
        `when`(memberships.findByMembershipIdAndGroupId(placeholderId, groupId)).thenReturn(
            GroupMembershipEntity(placeholderId, groupId, subject = "already-bound", isPlaceholder = true)
        )
        val error = assertThrows<SquarewiseException> {
            store.invite(groupId, "alice", CreateInviteRequest(24, placeholderId))
        }
        assertEquals(CategoryCode.STATE_CONFLICT, error.definition.category)
    }

    @Test
    fun `claim rejects malformed and missing invitations before mutation`() {
        val malformed = assertThrows<SquarewiseException> { store.claim("not-a-token", "alice") }
        assertEquals(CategoryCode.STATE_CONFLICT, malformed.definition.category)

        val token = "a".repeat(64)
        `when`(invitations.findById(token)).thenReturn(Optional.empty())
        val missing = assertThrows<SquarewiseException> { store.claim(token, "alice") }
        assertEquals(CategoryCode.STATE_CONFLICT, missing.definition.category)
    }

    private fun assertNotFound(action: () -> Unit) {
        val error = assertThrows<SquarewiseException>(action)
        assertEquals(CategoryCode.NOT_FOUND, error.definition.category)
    }
}
