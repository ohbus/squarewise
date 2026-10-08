package com.subhrodip.squarewise.expensecore.search
import com.subhrodip.squarewise.db.routing.DbExecutionContext

import com.subhrodip.squarewise.expensecore.search.api.SearchController
import com.subhrodip.squarewise.expensecore.search.api.SearchQuery
import com.subhrodip.squarewise.expensecore.search.model.SearchExpense
import com.subhrodip.squarewise.expensecore.search.persistence.InMemorySearchStore
import com.subhrodip.squarewise.expensecore.search.persistence.SearchStore
import com.subhrodip.squarewise.expensecore.groups.api.CreateGroupRequest
import com.subhrodip.squarewise.expensecore.groups.persistence.store.InMemoryGroupStore

import com.subhrodip.squarewise.errors.http.GlobalErrorHandler
import com.subhrodip.squarewise.db.routing.DbContextHolder
import com.subhrodip.squarewise.db.routing.DbOperationKind
import com.subhrodip.squarewise.db.routing.ReadConsistency
import com.subhrodip.squarewise.expensecore.categories.ExpenseCategory
import com.subhrodip.squarewise.ids.contracts.ApiEndpoints
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.RequestPostProcessor
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.header
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import java.security.Principal

/**
 * Unit and standalone integration tests for [SearchController].
 *
 * Verifies authorization boundaries, filtering by query/currency/category,
 * cursor pagination with totals, and formula injection escaping during CSV export.
 */
class SearchControllerTest {

    private lateinit var groupStore: InMemoryGroupStore
    private lateinit var searchStore: InMemorySearchStore
    private lateinit var recordingSearchStore: RecordingSearchStore
    private lateinit var mvc: MockMvc

    private val alice = RequestPostProcessor { request -> request.userPrincipal = Principal { "alice" }; request }
    private val bob = RequestPostProcessor { request -> request.userPrincipal = Principal { "bob" }; request }

    @BeforeEach
    fun setup() {
        groupStore = InMemoryGroupStore()
        searchStore = InMemorySearchStore()
        recordingSearchStore = RecordingSearchStore(searchStore)
        val controller = SearchController(groupStore, recordingSearchStore)
        mvc = MockMvcBuilders.standaloneSetup(controller)
            .setControllerAdvice(GlobalErrorHandler())
            .build()
    }

    @Test
    fun `authorized search uses the approved eventual reader policy`() {
        val group = groupStore.create("alice", CreateGroupRequest("Reader policy", "TRIP", "EUR"))

        mvc.perform(
            get(ApiEndpoints.ExpenseCore.V1.groupSearch(group.groupId)).with(alice)
        ).andExpect(status().isOk)

        assertTrue(recordingSearchStore.context?.operationName == "expense.search")
        assertTrue(recordingSearchStore.context?.kind == DbOperationKind.QUERY)
        assertTrue(recordingSearchStore.context?.consistency == ReadConsistency.EVENTUAL)
        assertTrue(recordingSearchStore.context?.readerEligible == true)
    }

    @Test
    fun `search returns matching expenses for authorized group member`() {
        val group = groupStore.create("alice", CreateGroupRequest("Berlin Flat", "HOUSEHOLD", "EUR"))
        val expense1 = SearchExpense("00000000-0000-0000-0000-000000000001", "Grocery haul", "EUR", "4500", ExpenseCategory.FOOD)
        val expense2 = SearchExpense("00000000-0000-0000-0000-000000000002", "Subway ticket", "EUR", "300", ExpenseCategory.TRANSPORT)
        val expense3 = SearchExpense("00000000-0000-0000-0000-000000000003", "Electricity bill", "USD", "12000", ExpenseCategory.BILLS)
        searchStore.saveExpenses(group.groupId, listOf(expense1, expense2, expense3))

        mvc.perform(
            get(ApiEndpoints.ExpenseCore.V1.groupSearch(group.groupId))
                .with(alice)
                .param("query", "subway")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.expenses.length()").value(1))
            .andExpect(jsonPath("$.expenses[0].expenseId").value("00000000-0000-0000-0000-000000000002"))
            .andExpect(jsonPath("$.expenses[0].description").value("Subway ticket"))
            .andExpect(jsonPath("$.totals[0].currency").value("EUR"))
            .andExpect(jsonPath("$.totals[0].amountMinor").value("300"))
    }

    @Test
    fun `search filters by currency and category with pagination cursor and totals`() {
        val group = groupStore.create("alice", CreateGroupRequest("Trip", "TRIP", "EUR"))
        val expense1 = SearchExpense("00000000-0000-0000-0000-000000000001", "Dinner at Bistro", "EUR", "2500", ExpenseCategory.FOOD)
        val expense2 = SearchExpense("00000000-0000-0000-0000-000000000002", "Lunch at Cafe", "EUR", "1500", ExpenseCategory.FOOD)
        val expense3 = SearchExpense("00000000-0000-0000-0000-000000000003", "Hotel Stay", "EUR", "8000", ExpenseCategory.LODGING)
        searchStore.saveExpenses(group.groupId, listOf(expense1, expense2, expense3))

        // Page 1 with limit 1 and category food
        val result = mvc.perform(
            get(ApiEndpoints.ExpenseCore.V1.groupSearch(group.groupId))
                .with(alice)
                .param("category", "food")
                .param("limit", "1")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.expenses.length()").value(1))
            .andExpect(jsonPath("$.expenses[0].expenseId").value("00000000-0000-0000-0000-000000000001"))
            .andExpect(jsonPath("$.hasMore").value(true))
            .andExpect(jsonPath("$.totals[0].amountMinor").value("2500"))
            .andReturn()

        val json = result.response.contentAsString
        val cursor = Regex("\"nextCursor\":\"([^\"]+)\"").find(json)!!.groupValues[1]

        // Page 2 using cursor
        mvc.perform(
            get(ApiEndpoints.ExpenseCore.V1.groupSearch(group.groupId))
                .with(alice)
                .param("category", "food")
                .param("cursor", cursor)
                .param("limit", "1")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.expenses.length()").value(1))
            .andExpect(jsonPath("$.expenses[0].expenseId").value("00000000-0000-0000-0000-000000000002"))
            .andExpect(jsonPath("$.hasMore").value(false))
            .andExpect(jsonPath("$.totals[0].amountMinor").value("1500"))
    }

    @Test
    fun `search rejects non-members with 404`() {
        val group = groupStore.create("alice", CreateGroupRequest("Trip", "TRIP", "EUR"))
        mvc.perform(
            get(ApiEndpoints.ExpenseCore.V1.groupSearch(group.groupId))
                .with(bob)
        )
            .andExpect(status().isNotFound)
    }

    @Test
    fun `search rejects invalid limit with 400`() {
        val group = groupStore.create("alice", CreateGroupRequest("Trip", "TRIP", "EUR"))
        mvc.perform(
            get(ApiEndpoints.ExpenseCore.V1.groupSearch(group.groupId))
                .with(alice)
                .param("limit", "0")
        )
            .andExpect(status().isUnprocessableContent)
            .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
    }

    @Test
    fun `search rejects malformed cursor with 400`() {
        val group = groupStore.create("alice", CreateGroupRequest("Trip", "TRIP", "EUR"))

        mvc.perform(
            get(ApiEndpoints.ExpenseCore.V1.groupSearch(group.groupId))
                .with(alice)
                .param("cursor", "%%%invalid%%")
        )
            .andExpect(status().isUnprocessableContent)
            .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
    }

    @Test
    fun `export returns CSV with formula injection protection and attachment header`() {
        val group = groupStore.create("alice", CreateGroupRequest("Trip", "TRIP", "EUR"))
        val maliciousExpense = SearchExpense(
            "00000000-0000-0000-0000-000000000001",
            "=1+1; cmd|'/C calc'!A0",
            "EUR",
            "1000",
            ExpenseCategory.OTHER
        )
        val normalExpense = SearchExpense(
            "00000000-0000-0000-0000-000000000002",
            "Dinner, drinks & snacks",
            "EUR",
            "3000",
            ExpenseCategory.FOOD
        )
        searchStore.saveExpenses(group.groupId, listOf(maliciousExpense, normalExpense))

        val response = mvc.perform(get(ApiEndpoints.ExpenseCore.V1.groupExport(group.groupId)).with(alice))
            .andExpect(status().isOk)
            .andExpect(header().string("Content-Disposition", "attachment; filename=\"expenses-" + group.groupId + ".csv\""))
            .andExpect(content().contentType(ApiEndpoints.Headers.TEXT_CSV_UTF8))
            .andReturn().response.contentAsString

        // Formula injection neutralized with leading quote
        assertTrue(response.contains("'=1+1; cmd|'/C calc'!A0"))
        // Comma-containing description properly enclosed in double quotes
        assertTrue(response.contains("\"Dinner, drinks & snacks\""))
        assertTrue(response.startsWith("expenseId,description,currency,amountMinor,category"))
    }

    /** Verifies export query, currency, and category filters are forwarded and applied together. */
    @Test
    fun `export applies optional search filters`() {
        val group = groupStore.create("alice", CreateGroupRequest("Filtered export", "TRIP", "EUR"))
        searchStore.saveExpenses(
            group.groupId,
            listOf(
                SearchExpense("00000000-0000-0000-0000-000000000011", "Dinner", "EUR", "2500", ExpenseCategory.FOOD),
                SearchExpense("00000000-0000-0000-0000-000000000012", "Dinner", "USD", "2500", ExpenseCategory.FOOD),
                SearchExpense("00000000-0000-0000-0000-000000000013", "Dinner", "EUR", "2500", ExpenseCategory.LODGING)
            )
        )

        val response = mvc.perform(
            get(ApiEndpoints.ExpenseCore.V1.groupExport(group.groupId))
                .with(alice)
                .param("query", "dinner")
                .param("currency", "eur")
                .param("category", "food")
        )
            .andExpect(status().isOk)
            .andReturn().response.contentAsString

        assertTrue(response.contains("00000000-0000-0000-0000-000000000011,Dinner,EUR,2500,food"))
        assertTrue(!response.contains("00000000-0000-0000-0000-000000000012"))
        assertTrue(!response.contains("00000000-0000-0000-0000-000000000013"))
    }

    @Test
    fun `export rejects when row count exceeds maxRows`() {
        val group = groupStore.create("alice", CreateGroupRequest("Large Group", "TRIP", "EUR"))
        val expenses = (1..5).map {
            SearchExpense("00000000-0000-0000-0000-00000000000" + it, "Expense " + it, "EUR", "100")
        }
        searchStore.saveExpenses(group.groupId, expenses)

        mvc.perform(
            get(ApiEndpoints.ExpenseCore.V1.groupExport(group.groupId))
                .with(alice)
                .param("maxRows", "2")
        )
            .andExpect(status().isUnprocessableContent)
            .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
    }

    @Test
    fun `export rejects non-positive maxRows as validation failure`() {
        val group = groupStore.create("alice", CreateGroupRequest("Bounded Export", "TRIP", "EUR"))

        mvc.perform(
            get(ApiEndpoints.ExpenseCore.V1.groupExport(group.groupId))
                .with(alice)
                .param("maxRows", "0")
        )
            .andExpect(status().isUnprocessableContent)
            .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
    }

    @Test
    fun `export rejects an incompatible accept header with 406`() {
        val group = groupStore.create("alice", CreateGroupRequest("Trip", "TRIP", "EUR"))

        mvc.perform(
            get(ApiEndpoints.ExpenseCore.V1.groupExport(group.groupId))
                .with(alice)
                .header(ApiEndpoints.Headers.ACCEPT, ApiEndpoints.Headers.APPLICATION_JSON)
        )
            .andExpect(status().isNotAcceptable)
    }
}

private class RecordingSearchStore(private val delegate: SearchStore) : SearchStore {
    var context: DbExecutionContext? = null

    override fun findSearchExpenses(query: SearchQuery): List<SearchExpense> {
        context = DbContextHolder.current()
        return delegate.findSearchExpenses(query)
    }
}
