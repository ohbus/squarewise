package com.subhrodip.squarewise.expensecore.validation

import com.subhrodip.squarewise.errors.catalog.ExpenseErrors
import com.subhrodip.squarewise.expensecore.errors.ExpenseDomainException
import com.subhrodip.squarewise.expensecore.groups.domain.GroupEntity
import com.subhrodip.squarewise.expensecore.groups.domain.GroupMembershipEntity
import com.subhrodip.squarewise.expensecore.groups.persistence.repository.GroupMembershipRepository
import com.subhrodip.squarewise.expensecore.groups.persistence.repository.GroupRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import java.util.Optional
import java.util.UUID

/** Unit tests verifying [EntityReferenceValidator] referential integrity enforcement. */
class EntityReferenceValidatorTest {

    private val groupRepository = mock(GroupRepository::class.java)
    private val membershipRepository = mock(GroupMembershipRepository::class.java)
    private val validator = EntityReferenceValidator(groupRepository, membershipRepository)

    @Test
    fun `validateActiveGroup succeeds for active group`() {
        val groupId = UUID.randomUUID()
        val group = GroupEntity(groupId, "Trip", "TRIP", "USD", status = "ACTIVE")
        `when`(groupRepository.findById(groupId)).thenReturn(Optional.of(group))

        val resolved = validator.validateActiveGroup(groupId)
        assertEquals(groupId, resolved.groupId)
        assertEquals("ACTIVE", resolved.status)
    }

    @Test
    fun `validateActiveGroup fails when group is missing`() {
        val groupId = UUID.randomUUID()
        `when`(groupRepository.findById(groupId)).thenReturn(Optional.empty())

        val ex = assertThrows(ExpenseDomainException::class.java) {
            validator.validateActiveGroup(groupId)
        }
        assertSame(ExpenseErrors.GROUP_NOT_FOUND, ex.definition)
    }

    @Test
    fun `validateActiveGroup fails when group is archived`() {
        val groupId = UUID.randomUUID()
        val group = GroupEntity(groupId, "Trip", "TRIP", "USD", status = "ARCHIVED")
        `when`(groupRepository.findById(groupId)).thenReturn(Optional.of(group))

        val ex = assertThrows(ExpenseDomainException::class.java) {
            validator.validateActiveGroup(groupId)
        }
        assertSame(ExpenseErrors.GROUP_ARCHIVED, ex.definition)
    }

    @Test
    fun `validateActiveMembership succeeds for active membership`() {
        val groupId = UUID.randomUUID()
        val membership = GroupMembershipEntity(UUID.randomUUID(), groupId, "alice")
        `when`(membershipRepository.findByGroupIdAndSubjectAndStatus(groupId, "alice", "ACTIVE")).thenReturn(membership)

        val resolved = validator.validateActiveMembership(groupId, "alice")
        assertEquals("alice", resolved.subject)
    }

    @Test
    fun `validateActiveMembership fails when not active`() {
        val groupId = UUID.randomUUID()
        `when`(membershipRepository.findByGroupIdAndSubjectAndStatus(groupId, "bob", "ACTIVE")).thenReturn(null)

        val ex = assertThrows(ExpenseDomainException::class.java) {
            validator.validateActiveMembership(groupId, "bob")
        }
        assertSame(ExpenseErrors.GROUP_ACCESS_HIDDEN, ex.definition)
    }

    @Test
    fun `validateActiveParticipants succeeds for distinct active participants`() {
        val groupId = UUID.randomUUID()
        val p1 = UUID.randomUUID()
        val p2 = UUID.randomUUID()
        val m1 = GroupMembershipEntity(p1, groupId, "alice")
        val m2 = GroupMembershipEntity(p2, groupId, "bob")

        `when`(membershipRepository.findByGroupIdAndMembershipIdInAndStatus(groupId, setOf(p1, p2), "ACTIVE"))
            .thenReturn(listOf(m1, m2))

        val verified = validator.validateActiveParticipants(groupId, listOf(p1, p2))
        assertEquals(setOf(p1, p2), verified)
    }

    @Test
    fun `validateActiveParticipants rejects duplicates in input list`() {
        val groupId = UUID.randomUUID()
        val p1 = UUID.randomUUID()

        val ex = assertThrows(ExpenseDomainException::class.java) {
            validator.validateActiveParticipants(groupId, listOf(p1, p1))
        }
        assertSame(ExpenseErrors.EXPENSE_REQUEST_INVALID, ex.definition)
    }

    @Test
    fun `validateActiveParticipants fails when any participant is inactive`() {
        val groupId = UUID.randomUUID()
        val p1 = UUID.randomUUID()
        val p2 = UUID.randomUUID()
        val m1 = GroupMembershipEntity(p1, groupId, "alice")

        `when`(membershipRepository.findByGroupIdAndMembershipIdInAndStatus(groupId, setOf(p1, p2), "ACTIVE"))
            .thenReturn(listOf(m1))

        val ex = assertThrows(ExpenseDomainException::class.java) {
            validator.validateActiveParticipants(groupId, listOf(p1, p2))
        }
        assertSame(ExpenseErrors.PARTICIPANT_SET_INVALID, ex.definition)
    }

    @Test
    fun `validateFinancialParticipants succeeds when member is both payer and allocation recipient`() {
        val groupId = UUID.randomUUID()
        val aliceId = UUID.randomUUID()
        val bobId = UUID.randomUUID()
        val m1 = GroupMembershipEntity(aliceId, groupId, "alice")
        val m2 = GroupMembershipEntity(bobId, groupId, "bob")

        `when`(membershipRepository.findByGroupIdAndMembershipIdInAndStatus(groupId, setOf(aliceId, bobId), "ACTIVE"))
            .thenReturn(listOf(m1, m2))

        val verified = validator.validateFinancialParticipants(
            groupId = groupId,
            payerIds = listOf(aliceId),
            allocationIds = listOf(aliceId, bobId)
        )
        assertEquals(setOf(aliceId, bobId), verified)
    }

    @Test
    fun `validateFinancialParticipants rejects duplicate payers`() {
        val groupId = UUID.randomUUID()
        val aliceId = UUID.randomUUID()

        val ex = assertThrows(ExpenseDomainException::class.java) {
            validator.validateFinancialParticipants(
                groupId = groupId,
                payerIds = listOf(aliceId, aliceId),
                allocationIds = listOf(aliceId)
            )
        }
        assertSame(ExpenseErrors.EXPENSE_REQUEST_INVALID, ex.definition)
    }

    @Test
    fun `validateFinancialParticipants rejects duplicate allocations`() {
        val groupId = UUID.randomUUID()
        val aliceId = UUID.randomUUID()

        val ex = assertThrows(ExpenseDomainException::class.java) {
            validator.validateFinancialParticipants(
                groupId = groupId,
                payerIds = listOf(aliceId),
                allocationIds = listOf(aliceId, aliceId)
            )
        }
        assertSame(ExpenseErrors.EXPENSE_REQUEST_INVALID, ex.definition)
    }

    @Test
    fun `validateSettlementParticipants succeeds for distinct active counterparties`() {
        val groupId = UUID.randomUUID()
        val from = UUID.randomUUID()
        val to = UUID.randomUUID()
        val mFrom = GroupMembershipEntity(from, groupId, "from-user")
        val mTo = GroupMembershipEntity(to, groupId, "to-user")

        `when`(membershipRepository.findByGroupIdAndMembershipIdInAndStatus(groupId, setOf(from, to), "ACTIVE"))
            .thenReturn(listOf(mFrom, mTo))

        validator.validateSettlementParticipants(groupId, from, to)
    }

    @Test
    fun `validateSettlementParticipants rejects identical counterparties`() {
        val groupId = UUID.randomUUID()
        val same = UUID.randomUUID()

        assertThrows(IllegalArgumentException::class.java) {
            validator.validateSettlementParticipants(groupId, same, same)
        }
    }

    @Test
    fun `validateSettlementParticipants rejects missing counterparty`() {
        val groupId = UUID.randomUUID()
        val from = UUID.randomUUID()
        val to = UUID.randomUUID()
        val mFrom = GroupMembershipEntity(from, groupId, "from-user")

        `when`(membershipRepository.findByGroupIdAndMembershipIdInAndStatus(groupId, setOf(from, to), "ACTIVE"))
            .thenReturn(listOf(mFrom))

        val ex = assertThrows(ExpenseDomainException::class.java) {
            validator.validateSettlementParticipants(groupId, from, to)
        }
        assertSame(ExpenseErrors.GROUP_NOT_FOUND, ex.definition)
    }
}
