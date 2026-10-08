package com.subhrodip.squarewise.expensecore.recurring.api

import com.subhrodip.squarewise.expensecore.expenses.api.request.MoneyDto
import com.subhrodip.squarewise.expensecore.recurring.domain.RecurrenceFrequency
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

/** API response for a recurring schedule. */
data class RecurringScheduleResponse(
    val scheduleId: UUID,
    val groupId: UUID,
    val description: String,
    val amount: MoneyDto,
    val frequency: RecurrenceFrequency,
    val dayOfMonth: Int?,
    val startDate: LocalDate,
    val endDate: LocalDate?,
    val nextOccurrenceDate: LocalDate,
    val paused: Boolean,
    val createdAt: Instant,
    val version: Long
)
