@file:Suppress("CanConvertToMultiDollarString")

package com.subhrodip.squarewise.bff.transport

import com.subhrodip.squarewise.bff.transport.model.output.BffBalancesResponse
import com.subhrodip.squarewise.bff.transport.model.output.BffCreateGroup
import com.subhrodip.squarewise.bff.transport.model.output.BffExpense
import com.subhrodip.squarewise.bff.transport.model.output.BffGroup
import com.subhrodip.squarewise.bff.transport.model.output.BffMember
import com.subhrodip.squarewise.bff.transport.model.output.BffSettlement
import com.subhrodip.squarewise.bff.transport.model.output.BffSuggestedSettlement
import com.subhrodip.squarewise.bff.transport.model.input.CreateExpenseInput
import com.subhrodip.squarewise.bff.transport.model.input.RepaymentInput
import com.subhrodip.squarewise.bff.transport.model.upstream.UpstreamExpense
import com.subhrodip.squarewise.bff.transport.model.upstream.UpstreamGroup
import com.subhrodip.squarewise.bff.transport.model.upstream.UpstreamSettlement
import com.subhrodip.squarewise.bff.transport.BffGatewayFilters
import com.subhrodip.squarewise.bff.errors.UpstreamProblemDecoder
import com.subhrodip.squarewise.bff.errors.toUpstreamException
import com.subhrodip.squarewise.errors.catalog.BffErrors
import com.subhrodip.squarewise.ids.contracts.ApiEndpoints
import java.time.Duration
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import com.fasterxml.jackson.databind.ObjectMapper

/** REST gateway for Expense Core group, expense, and settlement operations. */
@Component
class ExpenseCoreGateway(
    builder: WebClient.Builder,
    @Value("${'$'}{squarewise.expense-core-url}") baseUrl: String,
    @Value("${'$'}{squarewise.bff.upstream-timeout:2s}") private val timeout: Duration
) {
    private data class ExpensePage(val expenses: List<BffExpense>, val nextCursor: String?, val pageNumber: Int)

    private companion object {
        const val PAGE_SIZE = 100
        const val UPSTREAM_SOURCE = "expense-core"
        // shortcut: group details include at most 1,000 expenses; add cursor pagination before raising this cap.
        const val MAX_EXPENSE_PAGES = 10
        const val MAX_GROUPS = 100
        // shortcut: group details include at most 100 members until the REST contract has cursors.
        const val MAX_MEMBERS = 100
    }

    private val client = builder.filter(BffGatewayFilters.bearerPropagation).baseUrl(baseUrl).build()
    private val upstreamProblemDecoder = UpstreamProblemDecoder(ObjectMapper())

    fun createGroup(input: BffCreateGroup, bearer: String?): Mono<BffGroup> =
        client.post().uri(ApiEndpoints.ExpenseCore.V1.PATH_GROUPS)
            .headers { headers -> bearer?.let { headers.setBearerAuth(it) } }
            .bodyValue(input).retrieve()
            .onStatus({ it.isError }) { response -> response.toUpstreamException(upstreamProblemDecoder, UPSTREAM_SOURCE) }
            .bodyToMono(UpstreamGroup::class.java).map { it.toBffGroup() }.timeout(timeout)

    fun listGroups(bearer: String?, limit: Int = MAX_GROUPS): Mono<List<BffGroup>> =
        listGroups(bearer, limit, includeMembers = true)

    /** Loads group members only when the GraphQL selection includes that collection. */
    fun listGroups(bearer: String?, limit: Int, includeMembers: Boolean): Mono<List<BffGroup>> {
        require(limit in 1..MAX_GROUPS) { "group limit must be between 1 and $MAX_GROUPS" }
        val groups = client.get().uri { builder ->
            builder.path(ApiEndpoints.ExpenseCore.V1.PATH_GROUPS).queryParam("limit", limit).build()
        }
            .headers { headers -> bearer?.let { headers.setBearerAuth(it) } }
            .retrieve()
            .onStatus({ it.isError }) { response -> response.toUpstreamException(upstreamProblemDecoder, UPSTREAM_SOURCE) }
            .bodyToFlux(UpstreamGroup::class.java).map { it.toBffGroup() }.collectList()
        return if (includeMembers) {
            groups.flatMap { groupList ->
                Flux.fromIterable(groupList).flatMapSequential({ group ->
                    listMembers(group.groupId, bearer).map { members -> group.copy(members = members) }
                }, 4).collectList()
            }
        } else {
            groups
        }.timeout(timeout)
    }

    fun updateGroup(groupId: String, name: String, bearer: String?): Mono<BffGroup> =
        client.patch().uri(ApiEndpoints.ExpenseCore.V1.PATH_GROUP_BY_ID, groupId)
            .headers { headers -> bearer?.let { headers.setBearerAuth(it) } }
            .bodyValue(mapOf("name" to name)).retrieve()
            .onStatus({ it.isError }) { response -> response.toUpstreamException(upstreamProblemDecoder, UPSTREAM_SOURCE) }
            .bodyToMono(UpstreamGroup::class.java).map { it.toBffGroup() }.timeout(timeout)

    fun listMembers(groupId: String, bearer: String?): Mono<List<BffMember>> =
        client.get().uri { builder ->
            builder.path(ApiEndpoints.ExpenseCore.V1.PATH_GROUP_MEMBERS)
                .queryParam("limit", MAX_MEMBERS)
                .build(groupId)
        }
            .headers { headers -> bearer?.let { headers.setBearerAuth(it) } }.retrieve()
            .onStatus({ it.isError }) { response -> response.toUpstreamException(upstreamProblemDecoder, UPSTREAM_SOURCE) }
            .bodyToFlux(BffMember::class.java).collectList().timeout(timeout)

    fun getGroup(groupId: String, bearer: String?): Mono<BffGroup> {
        return getGroup(groupId, bearer, includeBalances = true, includeExpenses = true, includeMembers = true)
    }

    /** Loads only the collection fields selected by GraphQL. */
    fun getGroup(
        groupId: String,
        bearer: String?,
        includeBalances: Boolean,
        includeExpenses: Boolean,
        includeMembers: Boolean
    ): Mono<BffGroup> {
        val groupMono = client.get().uri(ApiEndpoints.ExpenseCore.V1.PATH_GROUP_BY_ID, groupId)
            .headers { headers -> bearer?.let { headers.setBearerAuth(it) } }.retrieve()
            .onStatus({ it.isError }) { response -> response.toUpstreamException(upstreamProblemDecoder, UPSTREAM_SOURCE) }
            .bodyToMono(UpstreamGroup::class.java).map { it.toBffGroup() }
        val balancesMono = if (includeBalances) client.get().uri(ApiEndpoints.ExpenseCore.V1.PATH_GROUP_BALANCES, groupId)
            .headers { headers -> bearer?.let { headers.setBearerAuth(it) } }.retrieve()
            .onStatus({ it.isError }) { response -> response.toUpstreamException(upstreamProblemDecoder, UPSTREAM_SOURCE) }
            .bodyToMono(BffBalancesResponse::class.java).map { it.balances } else Mono.just(emptyList())
        val expensesMono = if (includeExpenses) listAllExpenses(groupId, bearer) else Mono.just(emptyList())
        val membersMono = if (includeMembers) listMembers(groupId, bearer) else Mono.just(emptyList())
        return Mono.zip(groupMono, balancesMono, expensesMono, membersMono).map { tuple ->
            tuple.t1.copy(balances = tuple.t2, expenses = tuple.t3, members = tuple.t4)
        }.timeout(timeout)
    }

    private fun listAllExpenses(groupId: String, bearer: String?): Mono<List<BffExpense>> {
        fun fetch(cursor: String?, pageNumber: Int): Mono<ExpensePage> = client.get().uri { builder ->
            builder.path(ApiEndpoints.ExpenseCore.V1.PATH_GROUP_EXPENSES)
                .queryParam("limit", PAGE_SIZE)
                .apply { cursor?.let { queryParam("cursor", it) } }
                .build(groupId)
        }
            .headers { headers -> bearer?.let { headers.setBearerAuth(it) } }.retrieve()
            .onStatus({ it.isError }) { response -> response.toUpstreamException(upstreamProblemDecoder, UPSTREAM_SOURCE) }
            .bodyToFlux(UpstreamExpense::class.java).map { it.toBffExpense() }.collectList()
            .map { expenses ->
                ExpensePage(
                    expenses,
                    expenses.takeIf { it.size == PAGE_SIZE }?.lastOrNull()?.expenseId,
                    pageNumber,
                )
            }

        return fetch(null, 1)
            .expand { page ->
                page.nextCursor?.let { nextCursor ->
                    if (page.pageNumber >= MAX_EXPENSE_PAGES) {
                        fetch(nextCursor, page.pageNumber + 1).flatMap { lookahead ->
                            if (lookahead.expenses.isEmpty()) {
                                Mono.empty()
                            } else {
                                Mono.error(UpstreamServiceException(502, definition = BffErrors.UPSTREAM_PROTOCOL_INVALID))
                            }
                        }
                    } else {
                        fetch(nextCursor, page.pageNumber + 1)
                    }
                } ?: Mono.empty()
            }
            .flatMapIterable { it.expenses }
            .collectList()
    }

    fun createExpense(groupId: String, input: CreateExpenseInput, idempotencyKey: String, bearer: String?): Mono<BffExpense> =
        client.post().uri(ApiEndpoints.ExpenseCore.V1.PATH_GROUP_EXPENSES, groupId)
            .header(ApiEndpoints.Headers.IDEMPOTENCY_KEY, idempotencyKey)
            .headers { headers -> bearer?.let { headers.setBearerAuth(it) } }.bodyValue(input).retrieve()
            .onStatus({ it.isError }) { response -> response.toUpstreamException(upstreamProblemDecoder, UPSTREAM_SOURCE) }
            .bodyToMono(UpstreamExpense::class.java).map { it.toBffExpense(input.description) }.timeout(timeout)

    fun recordRepayment(groupId: String, input: RepaymentInput, bearer: String?): Mono<BffSettlement> {
        val payload = mapOf("fromParticipantId" to input.fromParticipantId, "toParticipantId" to input.toParticipantId, "amountMinor" to input.amount.minor, "currency" to input.amount.currency)
        return client.post().uri(ApiEndpoints.ExpenseCore.V1.PATH_GROUP_SETTLEMENTS, groupId)
            .header(ApiEndpoints.Headers.IDEMPOTENCY_KEY, input.idempotencyKey)
            .headers { headers -> bearer?.let { headers.setBearerAuth(it) } }.bodyValue(payload).retrieve()
            .onStatus({ it.isError }) { response -> response.toUpstreamException(upstreamProblemDecoder, UPSTREAM_SOURCE) }
            .bodyToMono(UpstreamSettlement::class.java).map { it.toBffSettlement() }.timeout(timeout)
    }

    fun getSettlementSuggestions(groupId: String, bearer: String?): Mono<List<BffSuggestedSettlement>> =
        client.get().uri(ApiEndpoints.ExpenseCore.V1.PATH_GROUP_SETTLEMENT_SUGGESTIONS, groupId)
            .headers { headers -> bearer?.let { headers.setBearerAuth(it) } }.retrieve()
            .onStatus({ it.isError }) { response -> response.toUpstreamException(upstreamProblemDecoder, UPSTREAM_SOURCE) }
            .bodyToFlux(BffSuggestedSettlement::class.java).collectList().timeout(timeout)
}
