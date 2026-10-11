/**
 * Utility functions for validating and converting expense request payloads.
 * Centralises duplicated validation logic from `ExpenseController` to adhere to DRY principles.
 */
package com.subhrodip.squarewise.expensecore.expenses.service

import com.subhrodip.squarewise.expensecore.expenses.api.ExpenseController
import com.subhrodip.squarewise.expensecore.expenses.api.request.PayerDto
import com.subhrodip.squarewise.expensecore.expenses.domain.ExpenseAllocation
import com.subhrodip.squarewise.expensecore.expenses.domain.ExpensePayer
import com.subhrodip.squarewise.expensecore.expenses.domain.FinancialArithmetic

import com.subhrodip.squarewise.expensecore.errors.ExpenseDomainException
import com.subhrodip.squarewise.errors.catalog.ExpenseErrors
import java.util.UUID

object ExpenseValidator {
    /** Parse and validate the amount minor unit */
    fun parseAndValidateAmount(minorStr: String, fieldName: String = "amount.minor"): Long {
        val totalMinor = minorStr.toLongOrNull()
            ?: throw ExpenseDomainException(ExpenseErrors.EXPENSE_REQUEST_INVALID, "$fieldName must be a valid integer")
        if (totalMinor <= 0) {
            throw ExpenseDomainException(ExpenseErrors.EXPENSE_REQUEST_INVALID, "$fieldName must be greater than zero")
        }
        return totalMinor
    }

    /**
     * Validates payer amounts, currency match, participant uniqueness, and computes total payer sum.
     * Uses checked financial arithmetic to reject signed 64-bit integer overflow.
     *
     * @param payers List of payer DTOs
     * @param expenseCurrency Required ISO-4217 currency code
     * @param fieldPrefix Error message field prefix for reporting
     * @return Sum of payer amounts in minor units
     * @throws ExpenseDomainException if any payer amount is invalid, currency mismatches, IDs duplicate, or sum overflows
     */
    fun validatePayers(payers: List<PayerDto>, expenseCurrency: String, fieldPrefix: String = "payer"): Long {
        val participantIds = payers.map { it.participantId }
        if (participantIds.distinct().size != participantIds.size) {
            throw ExpenseDomainException(ExpenseErrors.EXPENSE_REQUEST_INVALID, "$fieldPrefix participant IDs must be unique")
        }
        var sum = 0L
        payers.forEach { payer ->
            val pAmount = payer.amount.minor.toLongOrNull()
                ?: throw ExpenseDomainException(ExpenseErrors.EXPENSE_REQUEST_INVALID, "$fieldPrefix amount.minor must be a valid integer")
            if (pAmount <= 0) {
                throw ExpenseDomainException(ExpenseErrors.EXPENSE_REQUEST_INVALID, "$fieldPrefix amount.minor must be positive")
            }
            if (payer.amount.currency != expenseCurrency) {
                throw ExpenseDomainException(ExpenseErrors.EXPENSE_REQUEST_INVALID, "$fieldPrefix currency must match expense currency")
            }
            sum = try {
                FinancialArithmetic.add(sum, pAmount)
            } catch (e: IllegalArgumentException) {
                throw ExpenseDomainException(ExpenseErrors.EXPENSE_REQUEST_INVALID, "Sum of $fieldPrefix amounts exceeds maximum allowed value", e)
            }
        }
        return sum
    }

    /** Map payer DTOs to domain objects */
    fun mapDomainPayers(payers: List<PayerDto>): List<ExpensePayer> =
        payers.map {
            ExpensePayer(
                participantId = UUID.fromString(it.participantId),
                amountMinor = it.amount.minor.toLong()
            )
        }

    /** Map allocation map to domain objects */
    fun mapDomainAllocations(allocationMap: Map<String, Long>): List<ExpenseAllocation> =
        allocationMap.map { (participantIdStr, allocatedMinor) ->
            ExpenseAllocation(
                participantId = UUID.fromString(participantIdStr),
                allocatedMinor = allocatedMinor
            )
        }
}
