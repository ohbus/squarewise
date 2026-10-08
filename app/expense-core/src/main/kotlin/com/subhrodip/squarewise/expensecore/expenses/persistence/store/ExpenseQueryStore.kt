package com.subhrodip.squarewise.expensecore.expenses.persistence.store

import com.subhrodip.squarewise.expensecore.expenses.api.response.GroupBalanceItem
import com.subhrodip.squarewise.expensecore.expenses.domain.ExpenseRecord

import java.util.UUID

/** Query-side persistence port for expense and balance reads. */
interface ExpenseQueryStore {
    fun findById(expenseId: UUID): ExpenseRecord?
    fun list(groupId: UUID, category: String? = null, cursor: String? = null, limit: Int = 50): List<ExpenseRecord>
    fun balances(groupId: UUID): List<GroupBalanceItem>
}
