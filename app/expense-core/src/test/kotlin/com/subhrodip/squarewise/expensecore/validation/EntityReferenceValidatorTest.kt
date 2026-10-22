package com.subhrodip.squarewise.expensecore.validation

import com.subhrodip.squarewise.errors.catalog.ExpenseErrors
import com.subhrodip.squarewise.errors.catalog.PlatformErrors
import com.subhrodip.squarewise.expensecore.errors.ExpenseDomainException
import com.subhrodip.squarewise.expensecore.groups.cache.GroupAccessCache
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
import org.mockito.Mockito.never
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import java.security.Principal
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
    fun `validateActiveGroup uses writer lookup even when cache is configured`() {
        val cache = GroupAccessCache()
        val cachedValidator = EntityReferenceValidator(groupRepository, membershipRepository, cache)
        val groupId = UUID.randomUUID()
        val group = GroupEntity(groupId, "Vacation", "TRIP", "EUR", status = "ACTIVE")
        `when`(groupRepository.findById(groupId)).thenReturn(Optional.of(group))

        val first = cachedValidator.validateActiveGroup(groupId)
        val second = cachedValidator.validateActiveGroup(groupId)

        assertEquals("Vacation", first.name)
        assertEquals("Vacation", second.name)
        verify(groupRepository, times(2)).findById(groupId)
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
    fun `validateActiveMembership uses the writer query even when cache says non-member`() {
        val groupId = UUID.randomUUID()
        val cache = GroupAccessCache()
        // Populate cache as non-member
        cache.isMember(groupId, "charlie") { false }
        val cachedValidator = EntityReferenceValidator(groupRepository, membershipRepository, cache)

        val ex = assertThrows(ExpenseDomainException::class.java) {
            cachedValidator.validateActiveMembership(groupId, "charlie")
        }
        assertSame(ExpenseErrors.GROUP_ACCESS_HIDDEN, ex.definition)
        verify(membershipRepository).findByGroupIdAndSubjectAndStatus(groupId, "charlie", "ACTIVE")
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

    @Test
    fun `validateActivePlaceholder returns entity when placeholder exists and active`() {
        val groupId = UUID.randomUUID()
        val placeholderId = UUID.randomUUID()
        val placeholder = GroupMembershipEntity(
            membershipId = placeholderId,
            groupId = groupId,
            subject = null,
            displayName = "Alice",
            isPlaceholder = true,
            status = "ACTIVE"
        )
        `when`(membershipRepository.findByMembershipIdAndGroupId(placeholderId, groupId)).thenReturn(placeholder)

        val result = validator.validateActivePlaceholder(groupId, placeholderId)
        assertEquals(placeholderId, result.membershipId)
    }

    @Test
    fun `validateActivePlaceholder throws when placeholder not found`() {
        val groupId = UUID.randomUUID()
        val placeholderId = UUID.randomUUID()
        `when`(membershipRepository.findByMembershipIdAndGroupId(placeholderId, groupId)).thenReturn(null)

        val ex = assertThrows(ExpenseDomainException::class.java) {
            validator.validateActivePlaceholder(groupId, placeholderId)
        }
        assertSame(ExpenseErrors.GROUP_NOT_FOUND, ex.definition)
    }

    @Test
    fun `validateActivePlaceholder throws when member is not a placeholder`() {
        val groupId = UUID.randomUUID()
        val memberId = UUID.randomUUID()
        val member = GroupMembershipEntity(
            membershipId = memberId,
            groupId = groupId,
            subject = "user-1",
            displayName = "Bob",
            isPlaceholder = false,
            status = "ACTIVE"
        )
        `when`(membershipRepository.findByMembershipIdAndGroupId(memberId, groupId)).thenReturn(member)

        val ex = assertThrows(ExpenseDomainException::class.java) {
            validator.validateActivePlaceholder(groupId, memberId)
        }
        assertSame(ExpenseErrors.EXPENSE_REQUEST_INVALID, ex.definition)
    }

    @Test
    fun `validateActivePlaceholder rejects non-active members using the writer query`() {
        val cache = GroupAccessCache()
        val cachedValidator = EntityReferenceValidator(groupRepository, membershipRepository, cache)
        val groupId = UUID.randomUUID()
        val placeholderId = UUID.randomUUID()
        val activeMemberId = UUID.randomUUID()
        cache.putActiveMemberIds(groupId, setOf(activeMemberId))

        val ex = assertThrows(ExpenseDomainException::class.java) {
            cachedValidator.validateActivePlaceholder(groupId, placeholderId)
        }
        assertSame(ExpenseErrors.GROUP_NOT_FOUND, ex.definition)
        org.mockito.Mockito.verify(membershipRepository)
            .findByMembershipIdAndGroupId(placeholderId, groupId)
    }

    @Test
    fun `isActiveMember and isActiveGroup leverage GroupAccessCache`() {
        val cache = GroupAccessCache()
        val cachedValidator = EntityReferenceValidator(groupRepository, membershipRepository, cache)
        val groupId = UUID.randomUUID()
        val subject = "cached-user"

        `when`(membershipRepository.existsByGroupIdAndSubjectAndStatus(groupId, subject, "ACTIVE")).thenReturn(true)
        `when`(groupRepository.findById(groupId)).thenReturn(Optional.of(GroupEntity(groupId, "Trip", "TRIP", "USD", status = "ACTIVE")))

        assertTrue(cachedValidator.isActiveMember(groupId, subject))
        assertTrue(cachedValidator.isActiveGroup(groupId))

        // On second invocation, cache serves without invoking repositories again
        assertTrue(cachedValidator.isActiveMember(groupId, subject))
        assertTrue(cachedValidator.isActiveGroup(groupId))
    }

    @Test
    fun `financial participant validation uses the bounded repository query even with a cache`() {
        val cache = GroupAccessCache()
        val cachedValidator = EntityReferenceValidator(groupRepository, membershipRepository, cache)
        val groupId = UUID.randomUUID()
        val p1 = UUID.randomUUID()
        val p2 = UUID.randomUUID()
        val m1 = GroupMembershipEntity(p1, groupId, "alice")
        val m2 = GroupMembershipEntity(p2, groupId, "bob")

        `when`(
            membershipRepository.findByGroupIdAndMembershipIdInAndStatus(
                groupId,
                setOf(p1, p2),
                "ACTIVE"
            )
        ).thenReturn(listOf(m1, m2))

        val participants = cachedValidator.validateFinancialParticipants(groupId, listOf(p1), listOf(p2))
        assertEquals(setOf(p1, p2), participants)

        // The writer-authoritative bounded query is repeated for each financial check.
        `when`(
            membershipRepository.findByGroupIdAndMembershipIdInAndStatus(
                groupId,
                setOf(p1),
                "ACTIVE"
            )
        ).thenReturn(listOf(m1))
        val active = cachedValidator.validateActiveParticipants(groupId, listOf(p1, p2))
        assertEquals(setOf(p1, p2), active)

        `when`(
            membershipRepository.findByGroupIdAndMembershipIdInAndStatus(
                groupId,
                setOf(p1, p2),
                "ACTIVE"
            )
        ).thenReturn(listOf(m1, m2))
        cachedValidator.validateSettlementParticipants(groupId, p1, p2)
        verify(membershipRepository, never()).findByGroupIdAndStatus(groupId, "ACTIVE")
    }

    @Test
    fun `validateFinancialParticipants and validateActiveParticipants return empty set on empty input`() {
        val groupId = UUID.randomUUID()
        assertEquals(emptySet<UUID>(), validator.validateFinancialParticipants(groupId, emptyList(), emptyList()))
        assertEquals(emptySet<UUID>(), validator.validateActiveParticipants(groupId, emptyList()))
    }

    @Test
    fun `isActiveMember and isActiveGroup function correctly without cache`() {
        val groupId = UUID.randomUUID()
        val subject = "uncached-user"
        `when`(membershipRepository.existsByGroupIdAndSubjectAndStatus(groupId, subject, "ACTIVE")).thenReturn(true)
        `when`(groupRepository.findById(groupId)).thenReturn(Optional.of(GroupEntity(groupId, "Trip", "TRIP", "USD", status = "ACTIVE")))

        assertTrue(validator.isActiveMember(groupId, subject))
        assertTrue(validator.isActiveGroup(groupId))
    }

    @Test
    fun `validateSettlementParticipants rejects when recipient counterparty is missing`() {
        val groupId = UUID.randomUUID()
        val from = UUID.randomUUID()
        val to = UUID.randomUUID()
        val mTo = GroupMembershipEntity(to, groupId, "to-user")

        `when`(membershipRepository.findByGroupIdAndMembershipIdInAndStatus(groupId, setOf(from, to), "ACTIVE"))
            .thenReturn(listOf(mTo))

        val ex = assertThrows(ExpenseDomainException::class.java) {
            validator.validateSettlementParticipants(groupId, from, to)
        }
        assertSame(ExpenseErrors.GROUP_NOT_FOUND, ex.definition)
    }

    @Test
    fun `validateSettlementParticipants rejects with a writer query when counterparty is missing`() {
        val cache = GroupAccessCache()
        val cachedValidator = EntityReferenceValidator(groupRepository, membershipRepository, cache)
        val groupId = UUID.randomUUID()
        val from = UUID.randomUUID()
        val to = UUID.randomUUID()
        val mFrom = GroupMembershipEntity(from, groupId, "alice")
        `when`(
            membershipRepository.findByGroupIdAndMembershipIdInAndStatus(
                groupId,
                setOf(from, to),
                "ACTIVE"
            )
        ).thenReturn(listOf(mFrom))

        val ex = assertThrows(ExpenseDomainException::class.java) {
            cachedValidator.validateSettlementParticipants(groupId, from, to)
        }
        assertSame(ExpenseErrors.GROUP_NOT_FOUND, ex.definition)
    }

    @Test
    fun `ensureActiveMember succeeds for active member of active group`() {
        val groupId = UUID.randomUUID()
        val principal = Principal { "alice" }
        `when`(membershipRepository.existsByGroupIdAndSubjectAndStatus(groupId, "alice", "ACTIVE")).thenReturn(true)
        `when`(groupRepository.findById(groupId)).thenReturn(Optional.of(GroupEntity(groupId, "Trip", "TRIP", "USD", status = "ACTIVE")))

        val subject = validator.ensureActiveMember(groupId, principal)
        assertEquals("alice", subject)
    }

    @Test
    fun `ensureActiveMember fails on missing or blank principal`() {
        val groupId = UUID.randomUUID()
        val exNull = assertThrows(ExpenseDomainException::class.java) {
            validator.ensureActiveMember(groupId, null)
        }
        assertSame(PlatformErrors.AUTHENTICATION_REQUIRED, exNull.definition)

        val exBlank = assertThrows(ExpenseDomainException::class.java) {
            validator.ensureActiveMember(groupId, Principal { "   " })
        }
        assertSame(PlatformErrors.AUTHENTICATION_REQUIRED, exBlank.definition)
    }

    @Test
    fun `ensureActiveMember fails when not active member`() {
        val groupId = UUID.randomUUID()
        `when`(membershipRepository.existsByGroupIdAndSubjectAndStatus(groupId, "bob", "ACTIVE")).thenReturn(false)

        val ex = assertThrows(ExpenseDomainException::class.java) {
            validator.ensureActiveMember(groupId, Principal { "bob" })
        }
        assertSame(ExpenseErrors.GROUP_ACCESS_HIDDEN, ex.definition)
    }

    @Test
    fun `ensureActiveMember fails when group is archived`() {
        val groupId = UUID.randomUUID()
        `when`(membershipRepository.existsByGroupIdAndSubjectAndStatus(groupId, "alice", "ACTIVE")).thenReturn(true)
        `when`(groupRepository.findById(groupId)).thenReturn(Optional.of(GroupEntity(groupId, "Trip", "TRIP", "USD", status = "ARCHIVED")))

        val ex = assertThrows(ExpenseDomainException::class.java) {
            validator.ensureActiveMember(groupId, Principal { "alice" })
        }
        assertSame(ExpenseErrors.GROUP_ARCHIVED, ex.definition)
    }

    @Test
    fun `ensureMembership succeeds for active member`() {
        val groupId = UUID.randomUUID()
        val principal = Principal { "alice" }
        `when`(membershipRepository.existsByGroupIdAndSubjectAndStatus(groupId, "alice", "ACTIVE")).thenReturn(true)

        val subject = validator.ensureMembership(groupId, principal)
        assertEquals("alice", subject)
    }

    @Test
    fun `ensureMembership fails on missing or blank principal`() {
        val groupId = UUID.randomUUID()
        val exNull = assertThrows(ExpenseDomainException::class.java) {
            validator.ensureMembership(groupId, null)
        }
        assertSame(PlatformErrors.AUTHENTICATION_REQUIRED, exNull.definition)

        val exBlank = assertThrows(ExpenseDomainException::class.java) {
            validator.ensureMembership(groupId, Principal { "" })
        }
        assertSame(PlatformErrors.AUTHENTICATION_REQUIRED, exBlank.definition)
    }

    @Test
    fun `ensureMembership fails when not a member`() {
        val groupId = UUID.randomUUID()
        `when`(membershipRepository.existsByGroupIdAndSubjectAndStatus(groupId, "eve", "ACTIVE")).thenReturn(false)

        val ex = assertThrows(ExpenseDomainException::class.java) {
            validator.ensureMembership(groupId, Principal { "eve" })
        }
        assertSame(ExpenseErrors.GROUP_ACCESS_HIDDEN, ex.definition)
    }

    @Test
    fun `validator requires groupRepository for membership-only checks`() {
        val standaloneValidator = EntityReferenceValidator(groupRepository, membershipRepository)
        val groupId = UUID.randomUUID()
        `when`(membershipRepository.existsByGroupIdAndSubjectAndStatus(groupId, "carol", "ACTIVE")).thenReturn(true)

        assertTrue(standaloneValidator.isActiveMember(groupId, "carol"))
        assertEquals("carol", standaloneValidator.ensureMembership(groupId, Principal { "carol" }))
    }
}

