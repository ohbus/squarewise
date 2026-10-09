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
     * Records a financial settlement payment encapsulated as a [Settlement] model.
     *
     * @param groupId the unique identifier of the owning group
     * @param settlement the domain settlement payload
     * @param actorSubject the authenticated identity performing the action
     * @param idempotencyKey the client-provided idempotency key for durable replay protection
     * @return the recorded domain settlement
     */
    fun record(
        groupId: UUID,
        settlement: Settlement,
        actorSubject: String? = null,
        idempotencyKey: String? = null
    ): Settlement {
        if (actorSubject == null && idempotencyKey == null) {
            return record(
                groupId = groupId,
                id = settlement.id,
                from = settlement.fromParticipantId,
                to = settlement.toParticipantId,
                amountMinor = settlement.amountMinor,
                currency = settlement.currency
            )
        }
        require(!actorSubject.isNullOrBlank()) { "Authenticated subject is required" }
        require(idempotencyKey != null && idempotencyKey.length in 16..200) { "Idempotency key must be between 16 and 200 characters" }
        return record(
            groupId = groupId,
            from = settlement.fromParticipantId,
            to = settlement.toParticipantId,
            amountMinor = settlement.amountMinor,
            currency = settlement.currency,
            actorSubject = actorSubject,
            idempotencyKey = idempotencyKey
        )
    }

    /**
     * Records a financial settlement payment without external idempotency key tracking.
     */
    fun record(
        groupId: UUID,
        id: UUID,
        from: UUID,
        to: UUID,
        amountMinor: Long,
        currency: String
    ): Settlement {
        require(from != to) { "Participants must differ" }
        require(amountMinor > 0) { "Repayment must be positive" }
        return store.record(groupId, Settlement(id, from, to, amountMinor, currency))
    }

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
        return store.record(groupId, Settlement(durableId, from, to, amountMinor, currency))
    }

    /**
     * Reverses a recorded settlement atomically.
     */
    fun reverse(groupId: UUID, id: UUID, reason: String): Settlement {
        require(reason.isNotBlank()) { "Reversal reason is required" }
        return store.reverse(groupId, id, reason)
    }

    /**
     * Calculates suggested settlement transfers that clear debts for the given group.
     */
    fun suggestions(groupId: UUID): List<SuggestedSettlement> =
        suggestionEngine.suggestSettlements(groupId)
}
