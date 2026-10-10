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
import com.subhrodip.squarewise.bff.transport.UpstreamServiceException

import com.subhrodip.squarewise.bff.transport.BffGatewayFilters
import com.subhrodip.squarewise.ids.contracts.ApiEndpoints
import java.time.Duration
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

/** REST gateway for Expense Core group, expense, and settlement operations. */
@Component
class ExpenseCoreGateway(
    builder: WebClient.Builder,
    @Value("${'$'}{squarewise.expense-core-url}") baseUrl: String,
    @Value("${'$'}{squarewise.bff.upstream-timeout:2s}") private val timeout: Duration
) {
    private val client = builder.filter(BffGatewayFilters.bearerPropagation).baseUrl(baseUrl).build()

    fun createGroup(input: BffCreateGroup, bearer: String?): Mono<BffGroup> =
        client.post().uri(ApiEndpoints.ExpenseCore.V1.PATH_GROUPS)
            .headers { headers -> bearer?.let { headers.setBearerAuth(it) } }
            .bodyValue(input).retrieve()
            .onStatus({ it.isError }) { response -> Mono.error(UpstreamServiceException(response.statusCode().value(), "Expense Core returned HTTP ${response.statusCode().value()}")) }
            .bodyToMono(UpstreamGroup::class.java).map { it.toBffGroup() }.timeout(timeout)

    fun listGroups(bearer: String?): Mono<List<BffGroup>> =
        client.get().uri(ApiEndpoints.ExpenseCore.V1.PATH_GROUPS)
            .headers { headers -> bearer?.let { headers.setBearerAuth(it) } }
            .retrieve()
            .onStatus({ it.isError }) { response -> Mono.error(UpstreamServiceException(response.statusCode().value(), "Expense Core returned HTTP ${response.statusCode().value()}")) }
            .bodyToFlux(UpstreamGroup::class.java).map { it.toBffGroup() }.collectList()
            .flatMap { groups ->
                Flux.fromIterable(groups).flatMapSequential({ group ->
                    listMembers(group.groupId, bearer).map { members -> group.copy(members = members) }
                }, 4).collectList()
            }.timeout(timeout)

    fun updateGroup(groupId: String, name: String, bearer: String?): Mono<BffGroup> =
        client.patch().uri(ApiEndpoints.ExpenseCore.V1.PATH_GROUP_BY_ID, groupId)
            .headers { headers -> bearer?.let { headers.setBearerAuth(it) } }
            .bodyValue(mapOf("name" to name)).retrieve()
            .onStatus({ it.isError }) { response -> Mono.error(UpstreamServiceException(response.statusCode().value(), "Expense Core returned HTTP ${response.statusCode().value()}")) }
            .bodyToMono(UpstreamGroup::class.java).map { it.toBffGroup() }.timeout(timeout)

    fun listMembers(groupId: String, bearer: String?): Mono<List<BffMember>> =
        client.get().uri(ApiEndpoints.ExpenseCore.V1.PATH_GROUP_MEMBERS, groupId)
            .headers { headers -> bearer?.let { headers.setBearerAuth(it) } }.retrieve()
            .onStatus({ it.isError }) { response -> Mono.error(UpstreamServiceException(response.statusCode().value(), "Expense Core returned HTTP ${response.statusCode().value()}")) }
            .bodyToFlux(BffMember::class.java).collectList().timeout(timeout)

    fun getGroup(groupId: String, bearer: String?): Mono<BffGroup> {
        val groupMono = client.get().uri(ApiEndpoints.ExpenseCore.V1.PATH_GROUP_BY_ID, groupId)
            .headers { headers -> bearer?.let { headers.setBearerAuth(it) } }.retrieve()
            .onStatus({ it.isError }) { response -> Mono.error(UpstreamServiceException(response.statusCode().value(), "Expense Core returned HTTP ${response.statusCode().value()}")) }
            .bodyToMono(UpstreamGroup::class.java).map { it.toBffGroup() }
        val balancesMono = client.get().uri(ApiEndpoints.ExpenseCore.V1.PATH_GROUP_BALANCES, groupId)
            .headers { headers -> bearer?.let { headers.setBearerAuth(it) } }.retrieve()
            .onStatus({ it.isError }) { response -> Mono.error(UpstreamServiceException(response.statusCode().value(), "Expense Core returned HTTP ${response.statusCode().value()}")) }
            .bodyToMono(BffBalancesResponse::class.java).map { it.balances }
        val expensesMono = listAllExpenses(groupId, bearer)
        return Mono.zip(groupMono, balancesMono, expensesMono, listMembers(groupId, bearer)).map { tuple ->
            tuple.t1.copy(balances = tuple.t2, expenses = tuple.t3, members = tuple.t4)
        }.timeout(timeout)
    }

    private fun listAllExpenses(groupId: String, bearer: String?, cursor: String? = null): Mono<List<BffExpense>> {
        val page = client.get().uri { builder ->
            builder.path(ApiEndpoints.ExpenseCore.V1.PATH_GROUP_EXPENSES)
                .queryParam("limit", 100)
                .apply { cursor?.let { queryParam("cursor", it) } }
                .build(groupId)
        }
            .headers { headers -> bearer?.let { headers.setBearerAuth(it) } }.retrieve()
            .onStatus({ it.isError }) { response -> Mono.error(UpstreamServiceException(response.statusCode().value(), "Expense Core returned HTTP ${response.statusCode().value()}")) }
            .bodyToFlux(UpstreamExpense::class.java).map { it.toBffExpense() }.collectList()
        return page.flatMap { expenses ->
            if (expenses.size < 100) Mono.just(expenses)
            else listAllExpenses(groupId, bearer, expenses.last().expenseId).map { expenses + it }
        }
    }

    fun createExpense(groupId: String, input: CreateExpenseInput, idempotencyKey: String, bearer: String?): Mono<BffExpense> =
        client.post().uri(ApiEndpoints.ExpenseCore.V1.PATH_GROUP_EXPENSES, groupId)
            .header(ApiEndpoints.Headers.IDEMPOTENCY_KEY, idempotencyKey)
            .headers { headers -> bearer?.let { headers.setBearerAuth(it) } }.bodyValue(input).retrieve()
            .onStatus({ it.isError }) { response -> Mono.error(UpstreamServiceException(response.statusCode().value(), "Expense Core returned HTTP ${response.statusCode().value()}")) }
            .bodyToMono(UpstreamExpense::class.java).map { it.toBffExpense(input.description) }.timeout(timeout)

    fun recordRepayment(groupId: String, input: RepaymentInput, bearer: String?): Mono<BffSettlement> {
        val payload = mapOf("fromParticipantId" to input.fromParticipantId, "toParticipantId" to input.toParticipantId, "amountMinor" to input.amount.minor, "currency" to input.amount.currency)
        return client.post().uri(ApiEndpoints.ExpenseCore.V1.PATH_GROUP_SETTLEMENTS, groupId)
            .header(ApiEndpoints.Headers.IDEMPOTENCY_KEY, input.idempotencyKey)
            .headers { headers -> bearer?.let { headers.setBearerAuth(it) } }.bodyValue(payload).retrieve()
            .onStatus({ it.isError }) { response -> Mono.error(UpstreamServiceException(response.statusCode().value(), "Expense Core returned HTTP ${response.statusCode().value()}")) }
            .bodyToMono(UpstreamSettlement::class.java).map { it.toBffSettlement() }.timeout(timeout)
    }

    fun getSettlementSuggestions(groupId: String, bearer: String?): Mono<List<BffSuggestedSettlement>> =
        client.get().uri(ApiEndpoints.ExpenseCore.V1.PATH_GROUP_SETTLEMENT_SUGGESTIONS, groupId)
            .headers { headers -> bearer?.let { headers.setBearerAuth(it) } }.retrieve()
            .onStatus({ it.isError }) { response -> Mono.error(UpstreamServiceException(response.statusCode().value(), "Expense Core returned HTTP ${response.statusCode().value()}")) }
            .bodyToFlux(BffSuggestedSettlement::class.java).collectList().timeout(timeout)
}
