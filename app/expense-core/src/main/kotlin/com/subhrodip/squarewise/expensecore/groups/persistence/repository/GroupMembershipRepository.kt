package com.subhrodip.squarewise.expensecore.groups.persistence.repository

import com.subhrodip.squarewise.expensecore.groups.domain.GroupMembershipEntity
import java.util.UUID
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import jakarta.persistence.LockModeType
import org.springframework.stereotype.Repository

/**
 * Spring Data JPA repository for managing persistence operations on [GroupMembershipEntity].
 */
@Repository
interface GroupMembershipRepository : JpaRepository<GroupMembershipEntity, UUID> {

    /**
     * Checks whether a specific user subject is an active member of the given group.
     *
     * @param groupId the UUID of the group
     * @param subject the subject identifier of the user
     * @param status the membership status (e.g. "ACTIVE")
     * @return true if the active membership exists, false otherwise
     */
    fun existsByGroupIdAndSubjectAndStatus(groupId: UUID, subject: String, status: String): Boolean

    /**
     * Convenience method to check active membership for a group and subject.
     */
    fun existsByGroupIdAndSubject(groupId: UUID, subject: String): Boolean =
        existsByGroupIdAndSubjectAndStatus(groupId, subject, "ACTIVE")

    /**
     * Finds all active group memberships for a specific user subject, ordered by membership ID.
     *
     * @param subject the subject identifier of the user
     * @param status the membership status (e.g. "ACTIVE")
     * @return list of group memberships for the user
     */
    fun findAllBySubjectAndStatusOrderByMembershipId(subject: String, status: String): List<GroupMembershipEntity>

    /**
     * Convenience method to find all active memberships for a subject.
     */
    fun findAllBySubjectOrderByMembershipId(subject: String): List<GroupMembershipEntity> =
        findAllBySubjectAndStatusOrderByMembershipId(subject, "ACTIVE")

    /**
     * Finds all member records belonging to the specified group with the given status.
     *
     * @param groupId the UUID of the group
     * @param status the membership status (e.g. "ACTIVE")
     * @return list of memberships associated with the group
     */
    fun findByGroupIdAndStatus(groupId: UUID, status: String): List<GroupMembershipEntity>

    /**
     * Convenience method to find active member records belonging to the specified group.
     */
    fun findByGroupId(groupId: UUID): List<GroupMembershipEntity> =
        findByGroupIdAndStatus(groupId, "ACTIVE")

    /**
     * Finds a membership by membership ID and group ID.
     *
     * @param membershipId the UUID of the membership
     * @param groupId the UUID of the group
     * @return the membership entity if found, null otherwise
     */
    fun findByMembershipIdAndGroupId(membershipId: UUID, groupId: UUID): GroupMembershipEntity?

    /**
     * Finds an active membership by group ID and subject.
     *
     * @param groupId the UUID of the group
     * @param subject the subject identifier of the user
     * @param status the membership status
     * @return the membership entity if found, null otherwise
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    fun findByGroupIdAndSubjectAndStatus(groupId: UUID, subject: String, status: String): GroupMembershipEntity?
}
