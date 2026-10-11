package com.subhrodip.squarewise.expensecore.recurring.persistence

import com.subhrodip.squarewise.expensecore.recurring.domain.RecurringExpenseSchedule
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.time.LocalDate
import java.util.UUID

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
        value = """
            SELECT * FROM recurring_expense_schedules
            WHERE paused = false AND next_occurrence_date <= :asOfDate
            ORDER BY next_occurrence_date ASC
            LIMIT 100 FOR UPDATE SKIP LOCKED
        """,
        nativeQuery = true
    )
    fun findDueSchedules(@Param("asOfDate") asOfDate: LocalDate): List<RecurringExpenseSchedule>

    /** Re-locks one claimed schedule inside its isolated occurrence transaction. */
    @Query("SELECT * FROM recurring_expense_schedules WHERE schedule_id = :scheduleId FOR UPDATE", nativeQuery = true)
    fun findForUpdate(@Param("scheduleId") scheduleId: UUID): RecurringExpenseSchedule?
}
