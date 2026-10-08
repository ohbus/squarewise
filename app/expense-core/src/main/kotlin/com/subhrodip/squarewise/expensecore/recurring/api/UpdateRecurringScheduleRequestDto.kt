package com.subhrodip.squarewise.expensecore.recurring.api

import com.subhrodip.squarewise.expensecore.expenses.api.request.MoneyDto
import com.subhrodip.squarewise.expensecore.recurring.domain.RecurrenceFrequency
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size
import java.time.LocalDate

/** API request for updating a recurring schedule. */
data class UpdateRecurringScheduleRequestDto(
    @field:NotBlank @field:Size(min = 1, max = 240) val description: String,
    @field:Valid val amount: MoneyDto,
    @field:NotNull val frequency: RecurrenceFrequency,
    val dayOfMonth: Int? = null,
    @field:NotNull val startDate: LocalDate,
    val endDate: LocalDate? = null,
    val payers: List<@Valid ExpensePayerDto>? = null,
    val allocations: List<@Valid ExpenseAllocationItemDto>? = null
)
