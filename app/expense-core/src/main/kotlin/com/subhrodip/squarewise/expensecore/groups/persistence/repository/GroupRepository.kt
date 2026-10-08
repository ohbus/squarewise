package com.subhrodip.squarewise.expensecore.groups.persistence.repository

import com.subhrodip.squarewise.expensecore.groups.domain.GroupEntity
import jakarta.persistence.LockModeType
import java.util.UUID
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository

/**
 * Spring Data JPA repository for managing persistence operations on [GroupEntity].
 */
@Repository
interface GroupRepository : JpaRepository<GroupEntity, UUID> {

    /**
     * Acquires a pessimistic write lock on the target group by [groupId] for membership or revision updates.
     *
     * @param groupId the UUID of the group to lock and retrieve
     * @return the locked [GroupEntity], or null if not found
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select group from GroupEntity group where group.groupId = :groupId")
    fun findForMembershipUpdate(@Param("groupId") groupId: UUID): GroupEntity?
}
