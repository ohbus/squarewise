/**
 * Utility functions for validating and converting expense request payloads.
 * Centralises duplicated validation logic from `ExpenseController` to adhere to DRY principles.
 */
package com.subhrodip.squarewise.expensecore.expenses.service
import com.subhrodip.squarewise.expensecore.expenses.api.ExpenseController
import com.subhrodip.squarewise.expensecore.expenses.api.request.PayerDto
import com.subhrodip.squarewise.expensecore.expenses.domain.ExpenseAllocation
import com.subhrodip.squarewise.expensecore.expenses.domain.ExpensePayer

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

    /** Validate payer amounts and currency match */
    fun validatePayers(payers: List<PayerDto>, expenseCurrency: String, fieldPrefix: String = "payer"): Long {
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
            sum += pAmount
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
