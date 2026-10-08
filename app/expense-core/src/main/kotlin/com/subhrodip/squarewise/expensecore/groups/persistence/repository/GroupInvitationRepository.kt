package com.subhrodip.squarewise.expensecore.groups.persistence.repository

import com.subhrodip.squarewise.expensecore.groups.domain.GroupInvitationEntity
import java.time.Instant
import java.util.UUID
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository

/**
 * Spring Data JPA repository for managing persistence operations on [GroupInvitationEntity].
 */
@Repository
interface GroupInvitationRepository : JpaRepository<GroupInvitationEntity, String> {

    /**
     * Atomically claims an unclaimed, unrevoked, unexpired invitation token for the given user subject.
     *
     * @param token the 64-character invitation token string
     * @param subject the subject identifier of the claiming user
     * @param claimedAt the timestamp at which the claim occurred
     * @return the number of updated rows (1 if claimed successfully, 0 if expired, invalid, revoked, or already claimed)
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(
        """
        update GroupInvitationEntity invitation
        set invitation.claimedAt = :claimedAt, invitation.claimedBy = :subject
        where invitation.token = :token
          and invitation.claimedAt is null
          and invitation.revokedAt is null
          and invitation.expiresAt > :claimedAt
        """
    )
    fun claimIfAvailable(
        @Param("token") token: String,
        @Param("subject") subject: String,
        @Param("claimedAt") claimedAt: Instant
    ): Int

    /**
     * Atomically revokes an unclaimed invitation token for the specified group.
     *
     * @param token the 64-character invitation token string
     * @param groupId the group UUID owning the invitation
     * @param revokedAt the timestamp at which the revocation occurred
     * @return the number of updated rows (1 if revoked successfully, 0 if already claimed/revoked)
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(
        """
        update GroupInvitationEntity invitation
        set invitation.revokedAt = :revokedAt
        where invitation.token = :token
          and invitation.groupId = :groupId
          and invitation.claimedAt is null
          and invitation.revokedAt is null
        """
    )
    fun revokeIfAvailable(
        @Param("token") token: String,
        @Param("groupId") groupId: UUID,
        @Param("revokedAt") revokedAt: Instant
    ): Int
}
