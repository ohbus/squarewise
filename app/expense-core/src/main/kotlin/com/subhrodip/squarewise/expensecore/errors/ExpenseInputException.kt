package com.subhrodip.squarewise.expensecore.errors

/** Typed invalid-input failure retaining the financial arithmetic API contract. */
class ExpenseInputException(
    message: String,
    cause: Throwable? = null,
) : IllegalArgumentException(message, cause)
