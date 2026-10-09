package com.subhrodip.squarewise.expensecore.settlements.persistence

import com.subhrodip.squarewise.errors.catalog.PlatformErrors
import com.subhrodip.squarewise.expensecore.errors.ExpenseDomainException
import com.subhrodip.squarewise.expensecore.settlements.domain.Settlement
import com.subhrodip.squarewise.expensecore.settlements.domain.SettlementStatus

import com.subhrodip.squarewise.errors.exceptions.SquarewiseException

import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Thread-safe in-memory implementation of [SettlementStore] used for unit testing.
 */
class InMemorySettlementStore : SettlementStore {
    private val settlements = ConcurrentHashMap<Pair<UUID, UUID>, Settlement>()

    override fun record(groupId: UUID, settlement: Settlement): Settlement {
        val key = groupId to settlement.id
        val existing = settlements[key]
        if (existing != null) {
            if (existing.fromParticipantId != settlement.fromParticipantId ||
                existing.toParticipantId != settlement.toParticipantId ||
                existing.amountMinor != settlement.amountMinor ||
                existing.currency != settlement.currency
            ) {
                throw ExpenseDomainException(PlatformErrors.RESOURCE_CONFLICT, "Idempotency key was already used with a different settlement")
            }
            return existing
        }
        return settlements.computeIfAbsent(key) { settlement }
    }

    @Synchronized
    override fun reverse(groupId: UUID, settlementId: UUID, reason: String): Settlement {
        val key = groupId to settlementId
        val settlement = settlements[key]
            ?: throw ExpenseDomainException(PlatformErrors.RESOURCE_NOT_FOUND, "Settlement not found")
        if (settlement.status == SettlementStatus.REVERSED) return settlement
        return settlement.copy(reason = reason, status = SettlementStatus.REVERSED).also {
            settlements[key] = it
        }
    }
}
