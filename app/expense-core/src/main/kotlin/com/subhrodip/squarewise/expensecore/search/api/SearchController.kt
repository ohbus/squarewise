package com.subhrodip.squarewise.expensecore.search.api

import com.subhrodip.squarewise.expensecore.groups.persistence.store.GroupStore
import com.subhrodip.squarewise.expensecore.search.persistence.SearchStore
import com.subhrodip.squarewise.expensecore.search.model.ExpenseSearch
import com.subhrodip.squarewise.expensecore.search.model.ExpenseSearchPage

import com.subhrodip.squarewise.ids.contracts.ApiEndpoints
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import com.subhrodip.squarewise.expensecore.errors.ExpenseDomainException
import com.subhrodip.squarewise.errors.catalog.ExpenseErrors
import java.security.Principal
import java.util.UUID
import com.subhrodip.squarewise.db.routing.DbContextHolder
import com.subhrodip.squarewise.db.routing.DbExecutionContext
import com.subhrodip.squarewise.db.routing.DbOperationKind
import com.subhrodip.squarewise.db.routing.ReadConsistency
import com.subhrodip.squarewise.observability.db.DbTelemetry

/**
 * Controller exposing authorized, persistent group-scoped expense search and CSV export endpoints.
 *
 * Enforces authenticated group membership before delegating search queries and safe CSV export
 * generation using [ExpenseSearch].
 */
@RestController
@RequestMapping(ApiEndpoints.ExpenseCore.V1.PATH_GROUP_BY_ID)
class SearchController(
    private val groupStore: GroupStore,
    private val searchStore: SearchStore,
    private val expenseSearch: ExpenseSearch = ExpenseSearch(),
    private val dbTelemetry: DbTelemetry = DbTelemetry()
) {

    /**
     * Searches active expenses within an authorized group with filtering, pagination cursor, and currency totals.
     *
     * @param groupId the group identifier
     * @param query optional text query matching expense description case-insensitively
     * @param currency optional 3-letter currency code filter
     * @param category optional expense category filter
     * @param cursor optional pagination cursor
     * @param limit maximum number of results to return (1..1000, default 100)
     * @param principal authenticated user principal
     * @return [ExpenseSearchPage] containing matching expenses, cursor, and currency totals
     * @throws org.springframework.web.server.ResponseStatusException if group not found or user not a member
     */
    @GetMapping(ApiEndpoints.ExpenseCore.V1.SEARCH_SUBPATH)
    fun search(
        @PathVariable groupId: UUID,
        @RequestParam(required = false) query: String?,
        @RequestParam(required = false) currency: String?,
        @RequestParam(required = false) category: String?,
        @RequestParam(required = false) cursor: String?,
        @RequestParam(defaultValue = "100") limit: Int,
        principal: Principal
    ): ExpenseSearchPage {
        ensureMembership(groupId, principal.name)
        val expenses = dbTelemetry.measureQuery("expense.search", "approved-query") {
            DbContextHolder.withContext(
                DbExecutionContext(
                    operationName = "expense.search",
                    kind = DbOperationKind.QUERY,
                    consistency = ReadConsistency.EVENTUAL,
                    readerEligible = true
                )
            ) {
                searchStore.findSearchExpenses(
                    SearchQuery(groupId, query?.trim().orEmpty(), currency?.trim()?.uppercase(), category, cursor, limit)
                )
            }
        }
        return try {
            expenseSearch.page(
                expenses = expenses,
                query = query ?: "",
                currency = currency,
                category = category,
                cursor = cursor,
                limit = limit
            )
        } catch (ex: IllegalArgumentException) {
            throw ExpenseDomainException(ExpenseErrors.EXPENSE_REQUEST_INVALID, ex.message, ex)
        }
    }

    /**
     * Generates a safe CSV file export of expenses within an authorized group.
     *
     * @param groupId the group identifier
     * @param query optional text query matching expense description case-insensitively
     * @param currency optional 3-letter currency code filter
     * @param category optional expense category filter
     * @param maxRows maximum number of export rows permitted (1..10000, default 10000)
     * @param principal authenticated user principal
     * @return [ResponseEntity] containing CSV data with Content-Disposition attachment header
     * @throws org.springframework.web.server.ResponseStatusException if group not found, user not a member, or export exceeds limit
     */
    @GetMapping(ApiEndpoints.ExpenseCore.V1.EXPORT_SUBPATH, produces = [ApiEndpoints.Headers.TEXT_CSV_UTF8])
    fun export(
        @PathVariable groupId: UUID,
        @RequestParam(required = false) query: String?,
        @RequestParam(required = false) currency: String?,
        @RequestParam(required = false) category: String?,
        @RequestParam(defaultValue = "10000") maxRows: Int,
        principal: Principal
    ): ResponseEntity<String> {
        ensureMembership(groupId, principal.name)
        val expenses = dbTelemetry.measureQuery("expense.search.export", "approved-query") {
            DbContextHolder.withContext(
                DbExecutionContext(
                    operationName = "expense.search.export",
                    kind = DbOperationKind.QUERY,
                    consistency = ReadConsistency.EVENTUAL,
                    readerEligible = true
                )
            ) {
                searchStore.findSearchExpenses(
                    SearchQuery(groupId, query?.trim().orEmpty(), currency?.trim()?.uppercase(), category, null, ExpenseSearch.MAX_EXPORT_ROWS)
                )
            }
        }
        val csvData = try {
            expenseSearch.csv(
                expenses = expenses,
                query = query ?: "",
                currency = currency,
                category = category,
                maxRows = maxRows
            )
        } catch (ex: IllegalArgumentException) {
            throw ExpenseDomainException(ExpenseErrors.EXPENSE_REQUEST_INVALID, ex.message, ex)
        }

        val filename = "expenses-$groupId.csv"
        return ResponseEntity.ok()
            .header(ApiEndpoints.Headers.CONTENT_DISPOSITION, "attachment; filename=\"$filename\"")
            .contentType(MediaType.parseMediaType(ApiEndpoints.Headers.TEXT_CSV_UTF8))
            .body(csvData)
    }

    private fun ensureMembership(groupId: UUID, subject: String) {
        val groups = groupStore.list(subject)
        if (groups.none { it.groupId == groupId }) {
            throw ExpenseDomainException(ExpenseErrors.GROUP_NOT_FOUND, "Group $groupId not found")
        }
    }
}
