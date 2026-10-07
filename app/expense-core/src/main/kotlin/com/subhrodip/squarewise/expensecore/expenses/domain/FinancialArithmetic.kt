package com.subhrodip.squarewise.expensecore.expenses.domain

import com.subhrodip.squarewise.expensecore.errors.ExpenseInputException

/** Checked arithmetic for signed 64-bit minor-unit calculations. */
object FinancialArithmetic {
    /** Adds two amounts and converts numeric overflow into a domain validation error. */
    fun add(left: Long, right: Long): Long = try {
        Math.addExact(left, right)
    } catch (exception: ArithmeticException) {
        throw ExpenseInputException("financial amount exceeds supported range", exception)
    }

    /** Multiplies two amounts and converts numeric overflow into a domain validation error. */
    fun multiply(left: Long, right: Long): Long = try {
        Math.multiplyExact(left, right)
    } catch (exception: ArithmeticException) {
        throw ExpenseInputException("financial amount exceeds supported range", exception)
    }

    /** Negates an amount and rejects the one signed value that cannot be negated. */
    fun negate(value: Long): Long = try {
        Math.negateExact(value)
    } catch (exception: ArithmeticException) {
        throw ExpenseInputException("financial amount exceeds supported range", exception)
    }
}
