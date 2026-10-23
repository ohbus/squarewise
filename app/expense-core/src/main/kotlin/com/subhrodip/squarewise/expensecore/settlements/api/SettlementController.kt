package com.subhrodip.squarewise.expensecore.settlements.api

import com.subhrodip.squarewise.errors.catalog.PlatformErrors
import com.subhrodip.squarewise.expensecore.settlements.domain.Settlement
import com.subhrodip.squarewise.expensecore.settlements.domain.SuggestedSettlement
import com.subhrodip.squarewise.expensecore.settlements.service.SettlementService
import com.subhrodip.squarewise.expensecore.settlements.service.SettlementSuggestionEngine
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
import com.subhrodip.squarewise.db.routing.DbReader
import com.subhrodip.squarewise.db.routing.DbWriter
import com.subhrodip.squarewise.expensecore.errors.ExpenseDomainException
import com.subhrodip.squarewise.errors.catalog.ExpenseErrors

import com.subhrodip.squarewise.ids.contracts.ApiEndpoints
import com.subhrodip.squarewise.expensecore.validation.EntityReferenceValidator

@RestController
@RequestMapping(ApiEndpoints.ExpenseCore.V1.PATH_GROUP_SETTLEMENTS)
class SettlementController(
    private val service: SettlementService,
    private val validator: EntityReferenceValidator
) {

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @DbWriter("settlement.record")
    fun record(@PathVariable groupId: UUID, @RequestHeader(ApiEndpoints.Headers.IDEMPOTENCY_KEY) idempotencyKey: String, @Valid @RequestBody request: RecordSettlementRequest, principal: Principal?): Settlement {
        val subject = ensureMembership(groupId, principal)
        return service.record(groupId, request.fromParticipantId, request.toParticipantId, parseAmount(request.amountMinor), request.currency, subject, idempotencyKey)
    }

    @PostMapping(ApiEndpoints.ExpenseCore.V1.SETTLEMENT_REVERSAL_RELATIVE_SUBPATH)
    @DbWriter("settlement.reverse")
    fun reverse(@PathVariable groupId: UUID, @PathVariable settlementId: UUID, @Valid @RequestBody request: ReverseSettlementRequest, principal: Principal?): Settlement {
        val subject = ensureMembership(groupId, principal)
        return service.reverse(groupId, settlementId, request.reason, subject)
    }

    @GetMapping(ApiEndpoints.ExpenseCore.V1.SETTLEMENT_SUGGESTIONS_RELATIVE_SUBPATH)
    @DbWriter("settlement.suggestions")
    fun getSuggestions(@PathVariable groupId: UUID, principal: Principal?): List<SuggestedSettlement> {
        ensureMembership(groupId, principal)
        return service.suggestions(groupId)
    }

    private fun ensureMembership(groupId: UUID, principal: Principal?): String =
        validator.ensureMembership(groupId, principal)

    private fun parseAmount(amountMinor: String): Long =
        amountMinor.toLongOrNull()
            ?: throw ExpenseDomainException(ExpenseErrors.EXPENSE_REQUEST_INVALID, "amountMinor must be a valid integer")
}
