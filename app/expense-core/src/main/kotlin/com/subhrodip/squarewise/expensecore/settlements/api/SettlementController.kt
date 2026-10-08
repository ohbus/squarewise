package com.subhrodip.squarewise.expensecore.settlements.api

import com.subhrodip.squarewise.errors.catalog.PlatformErrors
import com.subhrodip.squarewise.expensecore.settlements.domain.Settlement
import com.subhrodip.squarewise.expensecore.settlements.domain.SuggestedSettlement
import com.subhrodip.squarewise.expensecore.settlements.service.SettlementService
import com.subhrodip.squarewise.expensecore.settlements.service.SettlementSuggestionEngine
import com.subhrodip.squarewise.expensecore.groups.persistence.repository.GroupMembershipRepository

import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.util.UUID
import java.security.Principal
import com.subhrodip.squarewise.expensecore.errors.ExpenseDomainException
import com.subhrodip.squarewise.errors.catalog.ExpenseErrors

import com.subhrodip.squarewise.ids.contracts.ApiEndpoints

@RestController
@RequestMapping(ApiEndpoints.ExpenseCore.V1.PATH_GROUP_SETTLEMENTS)
class SettlementController(
    private val service: SettlementService,
    private val membershipRepository: GroupMembershipRepository,
    private val suggestionEngine: SettlementSuggestionEngine? = null
) {
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun record(@PathVariable groupId: UUID, @RequestHeader(ApiEndpoints.Headers.IDEMPOTENCY_KEY) idempotencyKey: String, @Valid @RequestBody request: RecordSettlementRequest, principal: Principal?): Settlement {
        ensureMembership(groupId, principal)
        return service.record(groupId, request.fromParticipantId, request.toParticipantId, request.amountMinor.toLong(), request.currency, principal!!.name, idempotencyKey)
    }

    @PostMapping(ApiEndpoints.ExpenseCore.V1.SETTLEMENT_REVERSAL_RELATIVE_SUBPATH)
    fun reverse(@PathVariable groupId: UUID, @PathVariable settlementId: UUID, @Valid @RequestBody request: ReverseSettlementRequest, principal: Principal?): Settlement {
        ensureMembership(groupId, principal)
        return service.reverse(groupId, settlementId, request.reason)
    }

    @GetMapping(ApiEndpoints.ExpenseCore.V1.SETTLEMENT_SUGGESTIONS_RELATIVE_SUBPATH)
    fun getSuggestions(@PathVariable groupId: UUID, principal: Principal?): List<SuggestedSettlement> {
        ensureMembership(groupId, principal)
        return suggestionEngine?.suggestSettlements(groupId) ?: service.suggestions(groupId)
    }

    private fun ensureMembership(groupId: UUID, principal: Principal?) {
        val subject = principal?.name?.takeIf { it.isNotBlank() }
            ?: throw ExpenseDomainException(PlatformErrors.AUTHENTICATION_REQUIRED, "Authenticated subject is required")
        if (!membershipRepository.existsByGroupIdAndSubject(groupId, subject)) {
            throw ExpenseDomainException(ExpenseErrors.GROUP_NOT_FOUND, "Group $groupId not found")
        }
    }
}
