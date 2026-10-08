package com.subhrodip.squarewise.expensecore.recurring.persistence

import com.subhrodip.squarewise.expensecore.recurring.domain.RecurringExpenseOccurrence
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.time.LocalDate
import java.util.UUID

/**
 * Spring Data JPA repository for managing [RecurringExpenseOccurrence] entities.
 */
@Repository
interface RecurringExpenseOccurrenceRepository : JpaRepository<RecurringExpenseOccurrence, UUID> {

    /**
     * Finds all recorded occurrences belonging to the specified [scheduleId].
     *
     * @param scheduleId the unique identifier of the recurring schedule.
     * @return a list of occurrences for the schedule.
     */
    fun findByScheduleId(scheduleId: UUID): List<RecurringExpenseOccurrence>

    /**
     * Checks whether an occurrence record already exists for the given [scheduleId] and [occurrenceDate].
     *
     * @param scheduleId the unique identifier of the recurring schedule.
     * @param occurrenceDate the target occurrence date.
     * @return true if an occurrence exists, false otherwise.
     */
    fun existsByScheduleIdAndOccurrenceDate(scheduleId: UUID, occurrenceDate: LocalDate): Boolean

    /**
     * Finds an occurrence record matching the given [scheduleId] and [occurrenceDate], if any.
     *
     * @param scheduleId the unique identifier of the recurring schedule.
     * @param occurrenceDate the target occurrence date.
     * @return the matching [RecurringExpenseOccurrence] or null if not found.
     */
    fun findByScheduleIdAndOccurrenceDate(scheduleId: UUID, occurrenceDate: LocalDate): RecurringExpenseOccurrence?
}
