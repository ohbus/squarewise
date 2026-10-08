package com.subhrodip.squarewise.expensecore.settlements.persistence

import com.subhrodip.squarewise.expensecore.settlements.domain.Settlement

import java.util.UUID

/** Writer-only settlement persistence port. */
interface SettlementCommandStore {
    /** Records a new settlement or returns the existing record idempotently. */
    fun record(groupId: UUID, settlement: Settlement): Settlement

    /** Atomically reverses a recorded settlement. */
    fun reverse(groupId: UUID, settlementId: UUID, reason: String): Settlement
}
