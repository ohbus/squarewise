package com.subhrodip.squarewise.expensecore.expenses.domain

/** Shared bounds for public Expense Core mutation inputs. */
object ExpenseRequestLimits {
    const val MAX_PARTICIPANTS = 100
    const val MAX_BALANCE_ROWS = 1_000
    const val MAX_CATEGORY_LENGTH = 32
    const val MAX_IDEMPOTENCY_KEY_LENGTH = 200
}
