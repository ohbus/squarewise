package com.subhrodip.squarewise.expensecore.expenses.persistence.store

import com.subhrodip.squarewise.expensecore.expenses.domain.ExpenseRecord

import com.subhrodip.squarewise.db.routing.DbWriter
import java.util.UUID

/** Command-side persistence port for expense mutations. */
@DbWriter("expenses.command")
interface ExpenseCommandStore {
    /**
     * Creates an expense and validates all financial participants by default.
     *
     * @param groupId group containing the expense.
     * @param expense expense payload to persist.
     * @param idempotencyKey caller-provided duplicate-submission key.
     * @param actorSubject authenticated actor, or null for trusted internal work.
     * @param validateParticipants whether participant membership must be checked; production callers must retain the default.
     * @return the persisted expense record.
     */
    fun create(
        groupId: UUID,
        expense: ExpenseRecord,
        idempotencyKey: String,
        actorSubject: String? = null,
        validateParticipants: Boolean = true
    ): ExpenseRecord
    fun update(groupId: UUID, expenseId: UUID, update: ExpenseRecord, actorSubject: String? = null): ExpenseRecord
    fun delete(groupId: UUID, expenseId: UUID, version: Long?, actorSubject: String? = null)
}
