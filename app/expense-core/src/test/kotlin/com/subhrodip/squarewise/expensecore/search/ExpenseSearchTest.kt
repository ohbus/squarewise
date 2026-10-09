package com.subhrodip.squarewise.expensecore.search

import com.subhrodip.squarewise.errors.code.CategoryCode
import com.subhrodip.squarewise.errors.exceptions.SquarewiseException

import com.subhrodip.squarewise.expensecore.search.model.ExpenseSearch
import com.subhrodip.squarewise.expensecore.search.model.SearchExpense
import com.subhrodip.squarewise.expensecore.search.persistence.decodeSearchCursor
import com.subhrodip.squarewise.expensecore.categories.ExpenseCategory
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.Base64

class ExpenseSearchTest {
    @Test
    fun `filters case insensitively with stable bounded results and escapes formulas`() {
        val search = ExpenseSearch()
        val data = listOf(SearchExpense("2", "Dinner", "EUR", "1000"), SearchExpense("1", "dinner taxi", "EUR", "2000"))
        assertEquals(listOf("1", "2"), search.filter(data, "DINNER").map { it.expenseId })
        assertEquals(listOf("1", "2"), search.filter(data, "   ").map { it.expenseId })
        assertTrue(search.filter(data, "breakfast").isEmpty())
        assertEquals("'=SUM(A1)", search.csvCell("=SUM(A1)"))
    }

    @Test
    fun `escapes every formula prefix and quotes csv delimiters`() {
        val search = ExpenseSearch()

        assertEquals("'+value", search.csvCell("+value"))
        assertEquals("'-value", search.csvCell("-value"))
        assertEquals("'@value", search.csvCell("@value"))
        assertEquals("\"'@a,\"\"b\"\"\"", search.csvCell("@a,\"b\""))
        assertEquals("\"a\"\"b\"", search.csvCell("a\"b"))
        assertEquals("\"line\nvalue\"", search.csvCell("line\nvalue"))
        assertEquals("\"line\rvalue\"", search.csvCell("line\rvalue"))
    }

    @Test
    fun `csv cell preserves an empty value`() {
        assertEquals("", ExpenseSearch().csvCell(""))
    }

    @Test
    fun `pages with an opaque stable cursor and per currency totals`() {
        val search = ExpenseSearch()
        val data = listOf(
            SearchExpense("3", "Hotel", "USD", "500"),
            SearchExpense("1", "Train", "EUR", "200"),
            SearchExpense("2", "Meal", "EUR", "300")
        )
        val first = search.page(data, limit = 2)
        assertEquals(listOf("1", "2"), first.expenses.map { it.expenseId })
        assertEquals(listOf("500"), first.totals.map { it.amountMinor })
        val second = search.page(data, cursor = first.nextCursor, limit = 2)
        assertEquals(listOf("3"), second.expenses.map { it.expenseId })
        assertEquals(null, second.nextCursor)
    }

    @Test
    fun `rejects malformed cursors instead of silently changing the page`() {
        val search = ExpenseSearch()
        val error = assertThrows(SquarewiseException::class.java) {
            search.page(listOf(SearchExpense("1", "Dinner", "EUR", "100")), cursor = "%%%invalid%%%")
        }
        assertEquals(CategoryCode.VALIDATION_ERROR, error.definition.category)
    }

    /** Verifies a syntactically valid cursor cannot decode to a blank continuation key. */
    @Test
    fun `rejects cursors that decode to blank values`() {
        val blankCursor = Base64.getUrlEncoder().withoutPadding()
            .encodeToString(" ".toByteArray())

        val error = assertThrows(SquarewiseException::class.java) {
            ExpenseSearch().page(listOf(SearchExpense("1", "Dinner", "EUR", "100")), cursor = blankCursor)
        }

        assertEquals(CategoryCode.VALIDATION_ERROR, error.definition.category)
    }

    @Test
    fun `filters currency and quotes csv fields while bounding export`() {
        val search = ExpenseSearch()
        val data = listOf(SearchExpense("1", "Lunch, team", "eur", "200"), SearchExpense("2", "Dinner", "USD", "100"))
        assertEquals(listOf("1"), search.page(data, currency = "EUR").expenses.map { it.expenseId })
        assertEquals("expenseId,description,currency,amountMinor,category\n1,\"Lunch, team\",eur,200,other\n", search.csv(data, currency = "EUR"))
        assertThrows(IllegalArgumentException::class.java) { search.csv(data, maxRows = 1) }
    }

    /** Verifies CSV export rejects a non-positive row bound before reading expense data. */
    @Test
    fun `rejects non-positive csv row bounds`() {
        assertThrows(IllegalArgumentException::class.java) {
            ExpenseSearch().csv(emptyList(), maxRows = 0)
        }
    }

    /** Verifies CSV export rejects a row bound above the documented maximum. */
    @Test
    fun `rejects csv row bounds above the maximum`() {
        assertThrows(IllegalArgumentException::class.java) {
            ExpenseSearch().csv(emptyList(), maxRows = ExpenseSearch.MAX_EXPORT_ROWS + 1)
        }
    }

    /** Verifies an empty result still produces a valid header-only CSV export. */
    @Test
    fun `exports a header when no expenses match`() {
        val csv = ExpenseSearch().csv(
            listOf(SearchExpense("1", "Dinner", "EUR", "100")),
            query = "breakfast"
        )

        assertEquals("expenseId,description,currency,amountMinor,category\n", csv)
    }

    @Test
    fun `filters by category and defaults legacy records to other`() {
        val search = ExpenseSearch()
        val data = listOf(
            SearchExpense("1", "Dinner", "EUR", "100", ExpenseCategory.FOOD),
            SearchExpense("2", "Hotel", "EUR", "200", ExpenseCategory.LODGING),
            SearchExpense("3", "Misc", "EUR", "300")
        )
        assertEquals(listOf("1"), search.page(data, category = "food").expenses.map { it.expenseId })
        assertEquals(listOf("3"), search.page(data, category = "OTHER").expenses.map { it.expenseId })
    }

    @Test
    fun `rejects unknown category`() {
        val err = assertThrows(SquarewiseException::class.java) { ExpenseCategory.fromKey("travel") }
        assertEquals(CategoryCode.VALIDATION_ERROR, err.definition.category)
    }

    @Test
    fun `rejects unbounded pages and malformed currency filters`() {
        val search = ExpenseSearch()
        val data = listOf(SearchExpense("1", "Dinner", "EUR", "100"))

        assertThrows(IllegalArgumentException::class.java) { search.page(data, limit = 0) }
        assertThrows(IllegalArgumentException::class.java) { search.page(data, limit = ExpenseSearch.MAX_LIMIT + 1) }
        assertThrows(IllegalArgumentException::class.java) { search.page(data, currency = "EURO") }
        assertThrows(IllegalArgumentException::class.java) { search.page(data, currency = "12") }
    }

    @Test
    fun `decodes valid search cursors and rejects blank or malformed values`() {
        val cursor = Base64.getUrlEncoder().withoutPadding()
            .encodeToString("expense-42".toByteArray())

        assertEquals(null, decodeSearchCursor(null))
        assertEquals("expense-42", decodeSearchCursor(cursor))

        listOf("%%%invalid%%%", Base64.getUrlEncoder().withoutPadding().encodeToString(" ".toByteArray())).forEach { value ->
            val error = assertThrows(SquarewiseException::class.java) {
                decodeSearchCursor(value)
            }
            assertEquals(CategoryCode.VALIDATION_ERROR, error.definition.category)
        }
    }

    @Test
    fun `exports datasets larger than MAX_LIMIT without overflow when maxRows allows`() {
        val largeData = (1..1500).map { i ->
            SearchExpense("exp-$i", "Item $i", "EUR", "100")
        }
        val csv = ExpenseSearch().csv(largeData, maxRows = 2000)
        val lines = csv.trim().lines()
        assertEquals(1501, lines.size) // header + 1500 items
        assertEquals("expenseId,description,currency,amountMinor,category", lines.first())
    }
}
