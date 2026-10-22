package com.subhrodip.squarewise.expensecore.expenses.persistence.store

import com.subhrodip.squarewise.expensecore.expenses.domain.ExpenseRecord

import com.subhrodip.squarewise.db.routing.DbWriter
import java.util.UUID

/** Command-side persistence port for expense mutations. */
@DbWriter("expenses.command")
interface ExpenseCommandStore {
    /**
     * Creates an expense and optionally validates every financial participant.
     *
     * Internal writers must set [validateParticipants] explicitly; actor-backed
     * requests retain the default validation behavior.
     */
    fun create(
        groupId: UUID,
        expense: ExpenseRecord,
        idempotencyKey: String,
        actorSubject: String? = null,
        validateParticipants: Boolean = actorSubject != null
    ): ExpenseRecord
    fun update(groupId: UUID, expenseId: UUID, update: ExpenseRecord, actorSubject: String? = null): ExpenseRecord
    fun delete(groupId: UUID, expenseId: UUID, version: Long?, actorSubject: String? = null)
}
