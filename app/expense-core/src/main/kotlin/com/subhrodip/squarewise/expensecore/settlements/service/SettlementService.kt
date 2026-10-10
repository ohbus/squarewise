package com.subhrodip.squarewise.expensecore.settlements.service

import com.subhrodip.squarewise.expensecore.settlements.domain.Settlement
import com.subhrodip.squarewise.expensecore.settlements.domain.SuggestedSettlement
import com.subhrodip.squarewise.expensecore.settlements.persistence.SettlementStore

import java.util.UUID
import java.nio.charset.StandardCharsets
import org.springframework.stereotype.Service

@Service
class SettlementService(
    private val store: SettlementStore,
    private val suggestionEngine: SettlementSuggestionEngine
) {
    /**
     * Records an authenticated idempotent financial settlement payment with deterministic durable ID derivation.
     */
    fun record(
        groupId: UUID,
        from: UUID,
        to: UUID,
        amountMinor: Long,
        currency: String,
        actorSubject: String,
        idempotencyKey: String
    ): Settlement {
        require(from != to) { "Participants must differ" }
        require(amountMinor > 0) { "Repayment must be positive" }
        require(actorSubject.isNotBlank()) { "Authenticated subject is required" }
        require(idempotencyKey.length in 16..200) { "Idempotency key must be between 16 and 200 characters" }
        val durableId = UUID.nameUUIDFromBytes("settlement|$groupId|$actorSubject|$idempotencyKey".toByteArray(StandardCharsets.UTF_8))
        return store.record(groupId, Settlement(durableId, from, to, amountMinor, currency), actorSubject)
    }

    /**
     * Reverses a recorded settlement atomically.
     */
    fun reverse(groupId: UUID, id: UUID, reason: String, actorSubject: String): Settlement {
        require(reason.isNotBlank()) { "Reversal reason is required" }
        require(actorSubject.isNotBlank()) { "Authenticated subject is required" }
        return store.reverse(groupId, id, reason, actorSubject)
    }

    /**
     * Calculates suggested settlement transfers that clear debts for the given group.
     */
    fun suggestions(groupId: UUID): List<SuggestedSettlement> =
        suggestionEngine.suggestSettlements(groupId)
}
