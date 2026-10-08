package com.subhrodip.squarewise.expensecore.expenses.api.request

import com.subhrodip.squarewise.expensecore.expenses.domain.ExpenseRequestLimits

import jakarta.validation.Valid
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotEmpty
import jakarta.validation.constraints.Size

/** API command for optimistic-concurrency expense updates. */
data class UpdateExpenseRequest(
    @field:Min(1) val version: Long,
    @field:NotBlank @field:Size(min = 1, max = 240) val description: String,
    @field:Size(max = ExpenseRequestLimits.MAX_CATEGORY_LENGTH) val category: String? = "other",
    @field:Valid val amount: MoneyDto,
    @field:NotEmpty @field:Size(max = ExpenseRequestLimits.MAX_PARTICIPANTS) val payers: List<@Valid PayerDto>,
    @field:Valid val allocation: AllocationInputDto
)
