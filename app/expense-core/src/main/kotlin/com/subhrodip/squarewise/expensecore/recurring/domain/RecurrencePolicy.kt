package com.subhrodip.squarewise.expensecore.recurring.domain

import com.subhrodip.squarewise.expensecore.recurring.domain.RecurringExpenseSchedule

import java.time.LocalDate
import java.time.YearMonth

class RecurrencePolicy {
    fun nextAfter(date: LocalDate, schedule: RecurrenceSchedule): LocalDate =
        Companion.nextAfter(date, schedule)

    fun nextAfter(date: LocalDate, schedule: RecurringExpenseSchedule): LocalDate =
        Companion.nextAfter(date, schedule)

    companion object {
        fun nextAfter(date: LocalDate, schedule: RecurrenceSchedule): LocalDate = when (schedule.frequency) {
            RecurrenceFrequency.WEEKLY -> date.plusWeeks(1)
            RecurrenceFrequency.MONTHLY -> {
                val month = YearMonth.from(date).plusMonths(1)
                month.atDay((schedule.dayOfMonth ?: date.dayOfMonth).coerceAtMost(month.lengthOfMonth()))
            }
        }

        fun nextAfter(date: LocalDate, schedule: RecurringExpenseSchedule): LocalDate =
            nextAfter(
                date,
                RecurrenceSchedule(
                    scheduleId = schedule.scheduleId.toString(),
                    frequency = schedule.frequency,
                    dayOfMonth = schedule.dayOfMonth
                )
            )
    }
}
