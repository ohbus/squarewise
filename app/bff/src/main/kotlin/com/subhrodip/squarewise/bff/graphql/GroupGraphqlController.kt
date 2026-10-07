package com.subhrodip.squarewise.bff.graphql
import com.subhrodip.squarewise.bff.transport.model.output.BffCreateGroup
import com.subhrodip.squarewise.bff.transport.model.output.BffExpense
import com.subhrodip.squarewise.bff.transport.model.output.BffGroup
import com.subhrodip.squarewise.bff.transport.model.output.BffSettlement
import com.subhrodip.squarewise.bff.transport.model.output.BffSuggestedSettlement
import com.subhrodip.squarewise.bff.transport.model.input.CreateExpenseInput
import com.subhrodip.squarewise.bff.transport.model.input.CreateGroupInput
import com.subhrodip.squarewise.bff.transport.model.input.RepaymentInput

import com.subhrodip.squarewise.bff.transport.ExpenseCoreGateway
import com.subhrodip.squarewise.bff.errors.BffDomainException
import com.subhrodip.squarewise.errors.catalog.BffErrors
import com.subhrodip.squarewise.errors.catalog.PlatformErrors
import com.subhrodip.squarewise.bff.realtime.GroupInvalidation
import com.subhrodip.squarewise.bff.realtime.LiveUpdateFanout
import org.springframework.graphql.data.method.annotation.Argument
import org.springframework.graphql.data.method.annotation.MutationMapping
import org.springframework.graphql.data.method.annotation.QueryMapping
import org.springframework.graphql.data.method.annotation.SubscriptionMapping
import org.springframework.stereotype.Controller
import org.springframework.security.core.annotation.AuthenticationPrincipal
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

@Controller
class GroupGraphqlController(
    private val gateway: ExpenseCoreGateway,
    private val liveFanout: LiveUpdateFanout
) {
    fun emitInvalidation(groupId: String, revision: Long): GroupInvalidation =
        liveFanout.emitInvalidation(groupId, revision)

    fun emitInvalidation(groupId: String, revision: Long, changeId: String): GroupInvalidation =
        liveFanout.emitInvalidation(groupId, revision, changeId)

    /**
     * Authorizes and admits a bounded group subscription, releasing its slot when
     * the reactive stream is cancelled or terminates.
     *
     * SEC-003: The event stream is composed with [LiveUpdateFanout.revocationSignal] via
     * `takeUntilOther` so that the Flux terminates immediately when the subscriber loses
     * group membership (e.g., on a `member.removed` event processed by [BffEventConsumer]).
     */
    @SubscriptionMapping
    fun groupChanged(@Argument groupId: String, @AuthenticationPrincipal principal: Any?): Flux<GroupInvalidation> =
        Flux.using(
            {
                if (groupId.isBlank()) {
                    throw BffDomainException(BffErrors.GRAPHQL_INPUT_INVALID, "groupId must not be blank")
                }
                liveFanout.subscribe(
                    authenticatedSubject(principal)
                        ?: throw BffDomainException(PlatformErrors.AUTHENTICATION_REQUIRED, "authenticated subject is required"),
                    groupId
                )
            },
            { subscription ->
                gateway.getGroup(groupId, bearerToken(principal))
                    .flatMapMany {
                        liveFanout.invalidations()
                            .filter { it.groupId == groupId }
                            .takeUntilOther(liveFanout.revocationSignal(subscription.id))
                    }
            },
            { subscription -> liveFanout.unsubscribe(subscription.id) }
        )


    @QueryMapping
    fun groups(@AuthenticationPrincipal(expression = "tokenValue") principal: Any?): Mono<List<BffGroup>> = gateway.listGroups(bearerToken(principal))

    @QueryMapping
    fun group(@Argument id: String, @AuthenticationPrincipal(expression = "tokenValue") principal: Any?): Mono<BffGroup> =
        gateway.getGroup(id, bearerToken(principal))

    @QueryMapping
    fun settlementSuggestions(@Argument groupId: String, @AuthenticationPrincipal(expression = "tokenValue") principal: Any?): Mono<List<BffSuggestedSettlement>> =
        gateway.getSettlementSuggestions(groupId, bearerToken(principal))

    @MutationMapping
    fun createGroup(@Argument input: CreateGroupInput, @AuthenticationPrincipal(expression = "tokenValue") principal: Any?): Mono<BffGroup> =
        gateway.createGroup(BffCreateGroup(input.name, input.kind, input.currency), bearerToken(principal))

    @MutationMapping
    fun updateGroup(
        @Argument groupId: String,
        @Argument name: String,
        @AuthenticationPrincipal(expression = "tokenValue") principal: Any?
    ): Mono<BffGroup> =
        gateway.updateGroup(groupId, name, bearerToken(principal))
            .doOnSuccess { group ->
                if (group != null) {
                    emitInvalidation(groupId, group.revision)
                }
            }

    @MutationMapping
    fun createExpense(
        @Argument groupId: String,
        @Argument input: CreateExpenseInput,
        @Argument idempotencyKey: String,
        @AuthenticationPrincipal(expression = "tokenValue") principal: Any?
    ): Mono<BffExpense> =
        gateway.createExpense(groupId, input, idempotencyKey, bearerToken(principal))
            .doOnSuccess { expense ->
                if (expense != null) {
                    emitInvalidation(groupId, expense.version)
                }
            }

    @MutationMapping
    fun recordRepayment(
        @Argument input: RepaymentInput,
        @AuthenticationPrincipal(expression = "tokenValue") principal: Any?
    ): Mono<BffSettlement> {
        val groupId = input.groupId
            ?: return Mono.error(BffDomainException(BffErrors.GRAPHQL_INPUT_INVALID, "groupId is required for recording a repayment"))
        return gateway.recordRepayment(groupId, input, bearerToken(principal))
            .doOnSuccess { settlement ->
                if (settlement != null) {
                    emitInvalidation(groupId, 1L)
                }
            }
    }
}
