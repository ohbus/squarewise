package com.subhrodip.squarewise.expensecore.recurring.domain

import com.subhrodip.squarewise.expensecore.expenses.domain.ExpenseAllocation
import com.subhrodip.squarewise.expensecore.expenses.domain.ExpensePayer

/** Durable, validated financial split stored with a recurring schedule. */
data class RecurringExpenseSpecification(
    val payers: List<ExpensePayer> = emptyList(),
    val allocations: List<ExpenseAllocation> = emptyList()
)
