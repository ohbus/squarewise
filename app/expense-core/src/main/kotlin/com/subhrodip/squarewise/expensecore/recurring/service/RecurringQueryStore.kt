package com.subhrodip.squarewise.expensecore.recurring.service

import com.subhrodip.squarewise.expensecore.recurring.domain.RecurringExpenseOccurrence
import com.subhrodip.squarewise.expensecore.recurring.domain.RecurringExpenseSchedule
import java.util.UUID

/** Reader-side recurring schedule and occurrence query port. */
interface RecurringQueryStore {
    fun getSchedule(scheduleId: UUID): RecurringExpenseSchedule?

    /**
     * Returns at most [limit] schedules for a group.
     *
     * @param groupId group whose schedules are read.
     * @param limit maximum number of schedules to materialize, from 1 through 100.
     * @return the first bounded schedule page.
     */
    fun listSchedules(groupId: UUID, limit: Int = 100): List<RecurringExpenseSchedule>

    /**
     * Returns at most [limit] historical occurrences for a schedule.
     *
     * @param scheduleId schedule whose occurrences are read.
     * @param limit maximum number of occurrences to materialize, from 1 through 100.
     * @return the first bounded occurrence page.
     */
    fun getOccurrences(scheduleId: UUID, limit: Int = 100): List<RecurringExpenseOccurrence>
}
