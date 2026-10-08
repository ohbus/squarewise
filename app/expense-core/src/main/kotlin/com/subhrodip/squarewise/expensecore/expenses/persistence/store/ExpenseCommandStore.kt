package com.subhrodip.squarewise.expensecore.expenses.persistence.store

import com.subhrodip.squarewise.expensecore.expenses.domain.ExpenseRecord

import java.util.UUID

/** Command-side persistence port for expense mutations. */
interface ExpenseCommandStore {
    fun create(groupId: UUID, expense: ExpenseRecord, idempotencyKey: String, actorSubject: String? = null): ExpenseRecord
    fun update(groupId: UUID, expenseId: UUID, update: ExpenseRecord, actorSubject: String? = null): ExpenseRecord
    fun delete(groupId: UUID, expenseId: UUID, version: Long?, actorSubject: String? = null)
}
