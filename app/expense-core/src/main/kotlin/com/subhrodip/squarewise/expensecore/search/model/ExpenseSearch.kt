package com.subhrodip.squarewise.expensecore.search.model

import com.subhrodip.squarewise.expensecore.errors.ExpenseDomainException
import com.subhrodip.squarewise.expensecore.categories.ExpenseCategory
import com.subhrodip.squarewise.errors.catalog.ExpenseErrors
import java.math.BigInteger
import java.nio.charset.StandardCharsets
import java.util.Base64

/**
 * Search and export domain service providing deterministic filtering, stable cursor pagination,
 * currency subtotal calculations, and CSV serialization with spreadsheet formula escaping.
 */
class ExpenseSearch {
    companion object {
        /** Default page size limit for search requests. */
        const val DEFAULT_LIMIT = 100
        /** Maximum allowable limit per search query. */
        const val MAX_LIMIT = 1000
        /** Maximum rows permitted in a single CSV export file. */
        const val MAX_EXPORT_ROWS = 10_000
    }

    /**
     * Filters expenses by text query with an optional limit.
     *
     * @param expenses collection of candidate search expenses
     * @param query search text substring
     * @param limit maximum matching expenses to return
     * @return filtered list of expenses
     */
    fun filter(expenses: Iterable<SearchExpense>, query: String, limit: Int = DEFAULT_LIMIT): List<SearchExpense> =
        page(expenses, query = query, limit = limit).expenses

    /**
     * Pages matching expenses with optional query, currency, category filtering, cursor, and limit.
     *
     * @param expenses collection of search expenses
     * @param query optional text query matching expense description case-insensitively
     * @param currency optional 3-letter uppercase currency code
     * @param category optional category key
     * @param cursor opaque cursor representing the last seen expense ID
     * @param limit maximum number of expenses to include in the page
     * @return [ExpenseSearchPage] containing results and currency subtotals
     */
    fun page(
        expenses: Iterable<SearchExpense>,
        query: String = "",
        currency: String? = null,
        category: String? = null,
        cursor: String? = null,
        limit: Int = DEFAULT_LIMIT
    ): ExpenseSearchPage {
        require(limit in 1..MAX_LIMIT)
        val normalized = query.trim().lowercase()
        val normalizedCurrency = currency?.trim()?.uppercase()?.also { require(it.matches(Regex("[A-Z]{3}"))) }
        val normalizedCategory = category?.let(ExpenseCategory::fromKey)
        val startAfter = cursor?.let(::decodeCursor)
        val matches = expenses.asSequence()
            .filter { normalized.isEmpty() || it.description.lowercase().contains(normalized) }
            .filter { normalizedCurrency == null || it.currency.uppercase() == normalizedCurrency }
            .filter { normalizedCategory == null || it.category == normalizedCategory }
            .sortedBy { it.expenseId }
            .filter { startAfter == null || it.expenseId > startAfter }
            .toList()
        val page = matches.take(limit)
        val hasMore = matches.size > limit
        return ExpenseSearchPage(
            expenses = page,
            nextCursor = if (hasMore) encodeCursor(page.last().expenseId) else null,
            hasMore = hasMore,
            totals = totals(page)
        )
    }

    /**
     * Serializes matching expenses into standard CSV format with formula injection protection.
     *
     * @param expenses collection of candidate search expenses
     * @param query optional query filter
     * @param currency optional currency code filter
     * @param category optional category filter
     * @param maxRows maximum permitted rows in the export
     * @return CSV formatted text string
     */
    fun csv(
        expenses: Iterable<SearchExpense>,
        query: String = "",
        currency: String? = null,
        category: String? = null,
        maxRows: Int = MAX_EXPORT_ROWS
    ): String {
        require(maxRows in 1..MAX_EXPORT_ROWS)
        val normalized = query.trim().lowercase()
        val normalizedCurrency = currency?.trim()?.uppercase()?.also { require(it.matches(Regex("[A-Z]{3}"))) }
        val normalizedCategory = category?.let(ExpenseCategory::fromKey)
        val matches = expenses.asSequence()
            .filter { normalized.isEmpty() || it.description.lowercase().contains(normalized) }
            .filter { normalizedCurrency == null || it.currency.uppercase() == normalizedCurrency }
            .filter { normalizedCategory == null || it.category == normalizedCategory }
            .sortedBy { it.expenseId }
            .toList()
        require(matches.size <= maxRows) { "Export exceeds the maximum row limit" }
        return buildString {
            appendLine("expenseId,description,currency,amountMinor,category")
            matches.forEach { expense ->
                appendLine(listOf(expense.expenseId, expense.description, expense.currency, expense.amountMinor, expense.category.key)
                    .joinToString(",", transform = ::csvCell))
            }
        }
    }

    private fun totals(expenses: List<SearchExpense>): List<CurrencyTotal> = expenses.groupBy { it.currency.uppercase() }
        .toSortedMap().map { (currency, values) ->
            CurrencyTotal(currency, values.fold(BigInteger.ZERO) { total, expense -> total + expense.amountMinor.toBigInteger() }.toString())
        }

    /**
     * Escapes a single CSV cell value, prefixing formula injection triggers (=, +, -, @) with a single quote.
     *
     * @param value raw cell string
     * @return safe, escaped CSV cell representation
     */
    fun csvCell(value: String): String {
        val safe = if (value.firstOrNull() in setOf('=', '+', '-', '@')) "'$value" else value
        return if (safe.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) "\"${safe.replace("\"", "\"\"")}\"" else safe
    }

    private fun encodeCursor(expenseId: String): String = Base64.getUrlEncoder().withoutPadding()
        .encodeToString(expenseId.toByteArray(StandardCharsets.UTF_8))

    private fun decodeCursor(cursor: String): String = runCatching {
        String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8).also { require(it.isNotBlank()) }
    }.getOrElse { throw ExpenseDomainException(ExpenseErrors.EXPENSE_REQUEST_INVALID, "Invalid search cursor") }
}
