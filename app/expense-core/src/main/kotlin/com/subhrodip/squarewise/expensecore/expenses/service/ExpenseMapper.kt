package com.subhrodip.squarewise.expensecore.expenses.service

import com.subhrodip.squarewise.expensecore.expenses.domain.ExpenseAllocation
import com.subhrodip.squarewise.expensecore.expenses.domain.ExpensePayer
import com.subhrodip.squarewise.expensecore.expenses.domain.ExpenseRecord
import com.subhrodip.squarewise.expensecore.expenses.persistence.entity.ExpenseEntity

/**
 * Mapping functions to transform persistence entities to domain models.
 */

/**
 * Maps an [ExpenseEntity] to its domain [ExpenseRecord] representation.
 */
fun ExpenseEntity.toRecord(): ExpenseRecord = ExpenseRecord(
    expenseId = expenseId,
    groupId = groupId,
    description = description,
    category = category,
    currency = currency,
    amountMinor = amountMinor,
    version = version,
    allocationMode = allocationMode,
    createdAt = createdAt,
    payers = payers.map { ExpensePayer(it.participantId, it.amountMinor) },
    allocations = allocations.map { ExpenseAllocation(it.participantId, it.allocatedMinor) },
    deleted = deleted,
    updatedAt = updatedAt
)
