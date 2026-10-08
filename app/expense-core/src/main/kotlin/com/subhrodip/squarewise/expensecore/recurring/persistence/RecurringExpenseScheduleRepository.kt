package com.subhrodip.squarewise.expensecore.recurring.persistence

import com.subhrodip.squarewise.expensecore.recurring.domain.RecurringExpenseSchedule
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.time.LocalDate
import java.util.UUID
import jakarta.persistence.LockModeType

/**
 * Spring Data JPA repository for managing [RecurringExpenseSchedule] entities.
 */
@Repository
interface RecurringExpenseScheduleRepository : JpaRepository<RecurringExpenseSchedule, UUID> {

    /**
     * Finds all recurring expense schedules associated with the given [groupId].
     *
     * @param groupId the unique identifier of the group.
     * @return a list of schedules for the group.
     */
    fun findByGroupId(groupId: UUID): List<RecurringExpenseSchedule>

    /**
     * Retrieves all active (non-paused) schedules that have a next occurrence date
     * on or before the provided [asOfDate], ordered chronologically by next occurrence date.
     *
     * @param asOfDate the cutoff evaluation date.
     * @return a list of due schedules ready for processing.
     */
    @Query(
        """
        SELECT s FROM RecurringExpenseSchedule s
        WHERE s.paused = false
          AND s.nextOccurrenceDate <= :asOfDate
        ORDER BY s.nextOccurrenceDate ASC
        """
    )
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    fun findDueSchedules(@Param("asOfDate") asOfDate: LocalDate): List<RecurringExpenseSchedule>
}
