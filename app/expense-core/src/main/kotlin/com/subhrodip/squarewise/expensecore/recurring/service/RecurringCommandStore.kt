package com.subhrodip.squarewise.expensecore.recurring.service

import com.subhrodip.squarewise.expensecore.recurring.api.CreateRecurringScheduleRequest
import com.subhrodip.squarewise.expensecore.recurring.api.UpdateRecurringScheduleRequest
import com.subhrodip.squarewise.expensecore.recurring.domain.RecurringExpenseSchedule
import java.time.LocalDate
import java.util.UUID

/** Writer-side recurring schedule and worker command port. */
interface RecurringCommandStore {
    fun createSchedule(groupId: UUID, request: CreateRecurringScheduleRequest): RecurringExpenseSchedule
    fun updateSchedule(groupId: UUID, scheduleId: UUID, request: UpdateRecurringScheduleRequest): RecurringExpenseSchedule
    fun pauseSchedule(scheduleId: UUID): RecurringExpenseSchedule
    fun resumeSchedule(scheduleId: UUID): RecurringExpenseSchedule
    fun processDueOccurrences(asOfDate: LocalDate = LocalDate.now(), maxCatchUpOccurrences: Int = 12): Int
}
