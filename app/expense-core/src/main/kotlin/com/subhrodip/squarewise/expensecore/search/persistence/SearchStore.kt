package com.subhrodip.squarewise.expensecore.search.persistence

import com.subhrodip.squarewise.expensecore.search.api.SearchQuery
import com.subhrodip.squarewise.expensecore.search.model.SearchExpense
import java.nio.charset.StandardCharsets
import java.util.Base64
import com.subhrodip.squarewise.expensecore.errors.ExpenseDomainException
import com.subhrodip.squarewise.errors.catalog.ExpenseErrors

/**
 * Domain port for durable search and retrieval of expenses within an authorized group.
 */
interface SearchStore {
    /**
     * Finds all active (non-deleted) expenses belonging to the specified group.
     *
     * @param query bounded group-scoped search request
     * @return sequence or list of domain [SearchExpense] records
     */
    fun findSearchExpenses(query: SearchQuery): List<SearchExpense>
}

/** Normalized bounded search request passed to the read adapter. */
/** Decodes the public opaque cursor before it reaches SQL or an in-memory adapter. */
fun decodeSearchCursor(cursor: String?): String? = cursor?.let {
    runCatching {
        String(Base64.getUrlDecoder().decode(it), StandardCharsets.UTF_8).also { value -> require(value.isNotBlank()) }
    }.getOrElse { throw ExpenseDomainException(ExpenseErrors.ERR_02, "Invalid search cursor", it) }
}
