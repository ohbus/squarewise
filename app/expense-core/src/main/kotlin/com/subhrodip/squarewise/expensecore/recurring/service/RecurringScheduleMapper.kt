package com.subhrodip.squarewise.expensecore.recurring.service

import com.subhrodip.squarewise.expensecore.expenses.api.request.MoneyDto
import com.subhrodip.squarewise.expensecore.recurring.api.RecurringScheduleResponse
import com.subhrodip.squarewise.expensecore.recurring.domain.RecurringExpenseSchedule
/** Maps recurring schedule domain entities to API responses. */
fun RecurringExpenseSchedule.toResponse(): RecurringScheduleResponse = RecurringScheduleResponse(
    scheduleId = scheduleId,
    groupId = groupId,
    description = description,
    amount = MoneyDto(currency, amountMinor.toString()),
    frequency = frequency,
    dayOfMonth = dayOfMonth,
    startDate = startDate,
    endDate = endDate,
    nextOccurrenceDate = nextOccurrenceDate,
    paused = paused,
    createdAt = createdAt,
    version = version
)
