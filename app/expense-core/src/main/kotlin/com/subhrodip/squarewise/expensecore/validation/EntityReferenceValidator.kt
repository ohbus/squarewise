package com.subhrodip.squarewise.expensecore.validation

import com.subhrodip.squarewise.errors.catalog.ExpenseErrors
import com.subhrodip.squarewise.errors.catalog.PlatformErrors
import com.subhrodip.squarewise.expensecore.errors.ExpenseDomainException
import com.subhrodip.squarewise.expensecore.groups.domain.GroupEntity
import com.subhrodip.squarewise.expensecore.groups.domain.GroupMembershipEntity
import com.subhrodip.squarewise.expensecore.groups.persistence.repository.GroupMembershipRepository
import com.subhrodip.squarewise.expensecore.groups.persistence.repository.GroupRepository
import com.subhrodip.squarewise.db.routing.DbContextHolder
import com.subhrodip.squarewise.expensecore.groups.cache.GroupAccessCache
import org.springframework.stereotype.Component
import java.security.Principal
import java.util.UUID

/**
 * Standardized application-level foreign key and entity referential integrity validator.
 *
 * Ensures that all parent relationships (groups, memberships, financial participants)
 * are validated before dependent records are persisted, while database constraints
 * remain the durable referential-integrity guard.
 */
@Component
class EntityReferenceValidator(
    private val groupRepository: GroupRepository,
    private val membershipRepository: GroupMembershipRepository,
    @Suppress("unused") private val groupAccessCache: GroupAccessCache? = null
) {

    /**
     * Asserts that the authenticated principal is present and is an active member of an active group.
     *
     * Uses a writer-authoritative membership check even when called from a reader-scoped query.
     *
     * @param groupId unique group identifier.
     * @param principal authenticated user principal.
     * @return validated non-blank user subject string.
     * @throws ExpenseDomainException if unauthenticated, not an active member, or if group is archived.
     */
    fun ensureActiveMember(groupId: UUID, principal: Principal?): String {
        val subject = principal?.name?.trim()?.takeIf { it.isNotEmpty() }
            ?: throw ExpenseDomainException(PlatformErrors.AUTHENTICATION_REQUIRED, "Authenticated subject is required")
        if (!isActiveMember(groupId, subject)) {
            throw ExpenseDomainException(ExpenseErrors.GROUP_ACCESS_HIDDEN, "Group $groupId not found")
        }
        if (!isActiveGroup(groupId)) {
            throw ExpenseDomainException(ExpenseErrors.GROUP_ARCHIVED, "Group $groupId is archived")
        }
        return subject
    }

    /**
     * Asserts that the authenticated principal is present and holds active membership in the group.
     *
     * Uses a writer-authoritative membership check even when called from a reader-scoped query.
     *
     * @param groupId unique group identifier.
     * @param principal authenticated user principal.
     * @return validated non-blank user subject string.
     * @throws ExpenseDomainException if unauthenticated or not an active member.
     */
    fun ensureMembership(groupId: UUID, principal: Principal?): String {
        val subject = principal?.name?.trim()?.takeIf { it.isNotEmpty() }
            ?: throw ExpenseDomainException(PlatformErrors.AUTHENTICATION_REQUIRED, "Authenticated subject is required")
        if (!isActiveMember(groupId, subject)) {
            throw ExpenseDomainException(ExpenseErrors.GROUP_ACCESS_HIDDEN, "Group $groupId not found")
        }
        return subject
    }

    /**
     * Checks whether the specified subject holds an active membership on the writer.
     *
     * @param groupId unique group identifier.
     * @param actorSubject user subject.
     * @return true if active member, false otherwise.
     */
    fun isActiveMember(groupId: UUID, actorSubject: String): Boolean {
        return DbContextHolder.withWriter(operation = "group.membership.authorize") {
            membershipRepository.existsByGroupIdAndSubjectAndStatus(groupId, actorSubject, "ACTIVE")
        }
    }

    /**
     * Checks whether the specified group exists and is active on the writer.
     *
     * @param groupId unique group identifier.
     * @return true if group exists and is active, false otherwise.
     */
    fun isActiveGroup(groupId: UUID): Boolean {
        val status = DbContextHolder.withWriter(operation = "group.status.authorize") {
            groupRepository.findById(groupId).map { it.status }.orElse(null)
        }
        return status == "ACTIVE"
    }

    /**
     * Verifies that the specified group exists and is currently active on the writer.
     *
     * This method is used immediately before persisting rows with a group foreign key,
     * so it deliberately bypasses the eventually consistent access cache.
     *
     * @param groupId the unique identifier of the target group.
     * @return the resolved [GroupEntity].
     * @throws ExpenseDomainException if the group does not exist or has been archived.
     */
    fun validateActiveGroup(groupId: UUID): GroupEntity {
        val group = groupRepository.findById(groupId).orElse(null)
            ?: throw ExpenseDomainException(ExpenseErrors.GROUP_NOT_FOUND, "Group $groupId not found")
        if (group.status == "ARCHIVED") {
            throw ExpenseDomainException(ExpenseErrors.GROUP_ARCHIVED, "Group $groupId is archived")
        }
        return group
    }

    /**
     * Verifies that the caller subject holds an active membership in the target group.
     *
     * @param groupId the unique identifier of the target group.
     * @param actorSubject the authenticated identity subject.
     * @return the resolved active [GroupMembershipEntity].
     * @throws ExpenseDomainException if the caller is not an active member.
     */
    fun validateActiveMembership(groupId: UUID, actorSubject: String): GroupMembershipEntity {
        return DbContextHolder.withWriter(operation = "group.membership.validate") {
            membershipRepository.findByGroupIdAndSubjectAndStatus(groupId, actorSubject, "ACTIVE")
                ?: throw ExpenseDomainException(ExpenseErrors.GROUP_ACCESS_HIDDEN, "Group $groupId not found")
        }
    }

    /**
     * Verifies financial participants across payers and allocations.
     * Enforces that payer IDs are distinct among payers, allocation IDs are distinct among allocations,
     * and every financial participant holds active membership in the group.
     */
    fun validateFinancialParticipants(
        groupId: UUID,
        payerIds: Collection<UUID>,
        allocationIds: Collection<UUID>
    ): Set<UUID> {
        if (payerIds.size != payerIds.toSet().size || allocationIds.size != allocationIds.toSet().size) {
            throw ExpenseDomainException(
                ExpenseErrors.EXPENSE_REQUEST_INVALID,
                "Participant IDs must be unique within payers and allocations"
            )
        }
        val allParticipantIds = (payerIds + allocationIds).toSet()
        if (allParticipantIds.isEmpty()) return emptySet()

        val activeMembers = findActiveParticipantIds(groupId, allParticipantIds)

        if (!activeMembers.containsAll(allParticipantIds)) {
            throw ExpenseDomainException(
                ExpenseErrors.PARTICIPANT_SET_INVALID,
                "Every financial participant must be an active group member"
            )
        }
        return activeMembers.filter { allParticipantIds.contains(it) }.toSet()
    }

    /**
     * Verifies that every participant in the given set belongs to the group and has ACTIVE membership.
     *
     * Executes a single bounded query with an IN clause rather than N separate queries.
     *
     * @param groupId the unique identifier of the target group.
     * @param participantIds all participant membership UUIDs to validate.
     * @return the verified set of active participant UUIDs.
     * @throws ExpenseDomainException if any participant ID is invalid, duplicated, or non-active.
     */
    fun validateActiveParticipants(groupId: UUID, participantIds: Collection<UUID>): Set<UUID> {
        val distinctIds = participantIds.toSet()
        if (distinctIds.size != participantIds.size) {
            throw ExpenseDomainException(
                ExpenseErrors.EXPENSE_REQUEST_INVALID,
                "Participant IDs must be unique within payers and allocations"
            )
        }
        if (distinctIds.isEmpty()) return emptySet()

        val activeMembers = findActiveParticipantIds(groupId, distinctIds)

        if (!activeMembers.containsAll(distinctIds)) {
            throw ExpenseDomainException(
                ExpenseErrors.PARTICIPANT_SET_INVALID,
                "Every financial participant must be an active group member"
            )
        }
        return activeMembers.filter { distinctIds.contains(it) }.toSet()
    }

    /**
     * Validates settlement counterparties, verifying distinctness and active membership for both sides.
     *
     * @param groupId the group identifier.
     * @param fromParticipantId payer participant membership ID.
     * @param toParticipantId payee participant membership ID.
     * @throws ExpenseDomainException if counterparties are identical or any counterparty is inactive.
     */
    fun validateSettlementParticipants(groupId: UUID, fromParticipantId: UUID, toParticipantId: UUID) {
        require(fromParticipantId != toParticipantId) { "Participants must differ" }
        val activeMembers = findActiveParticipantIds(groupId, setOf(fromParticipantId, toParticipantId))
        if (!activeMembers.contains(fromParticipantId) || !activeMembers.contains(toParticipantId)) {
            throw ExpenseDomainException(
                ExpenseErrors.GROUP_NOT_FOUND,
                "Settlement participants must be active group members"
            )
        }
    }

    /**
     * Reads participant membership from the writer-authoritative bounded query.
     *
     * Financial authorization must not use the eventually consistent membership
     * cache because a removal on another node could otherwise be accepted.
     */
    private fun findActiveParticipantIds(groupId: UUID, participantIds: Set<UUID>): Set<UUID> =
        DbContextHolder.withWriter(operation = "group.participants.validate") {
            membershipRepository.findByGroupIdAndMembershipIdInAndStatus(
                groupId,
                participantIds,
                "ACTIVE"
            ).map { it.membershipId }.toSet()
        }

    /**
     * Verifies that the specified placeholder member belongs to the group and has active status.
     *
     * @param groupId the unique identifier of the target group.
     * @param placeholderId the unique identifier of the placeholder member.
     * @return the resolved active placeholder [GroupMembershipEntity].
     * @throws ExpenseDomainException if placeholder not found, not active, or not in group.
     */
    fun validateActivePlaceholder(groupId: UUID, placeholderId: UUID): GroupMembershipEntity {
        return DbContextHolder.withWriter(operation = "group.placeholder.validate") {
            val member = membershipRepository.findByMembershipIdAndGroupId(placeholderId, groupId)
                ?: throw ExpenseDomainException(ExpenseErrors.GROUP_NOT_FOUND, "Placeholder $placeholderId not found in group $groupId")
            if (member.status != "ACTIVE" || !member.isPlaceholder) {
                throw ExpenseDomainException(ExpenseErrors.EXPENSE_REQUEST_INVALID, "Member $placeholderId is not an active placeholder")
            }
            member
        }
    }
}
