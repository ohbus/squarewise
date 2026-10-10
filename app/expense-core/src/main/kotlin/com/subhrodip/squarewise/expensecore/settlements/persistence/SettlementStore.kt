package com.subhrodip.squarewise.expensecore.settlements.persistence

import com.subhrodip.squarewise.expensecore.settlements.domain.Settlement
import java.util.UUID

/**
 * Settlement persistence port supporting atomic recording and reversal of financial settlements.
 */
interface SettlementStore {
    /**
     * Records a new settlement or returns the existing record idempotently.
     *
     * @param groupId the UUID of the group
     * @param settlement the settlement domain model to record
     * @return the persisted or existing settlement domain model
     */
    fun record(groupId: UUID, settlement: Settlement, actorSubject: String): Settlement

    /**
     * Atomically reverses a recorded settlement.
     *
     * @param groupId the UUID of the group
     * @param settlementId the UUID of the settlement to reverse
     * @param reason the human-readable explanation for reversal
     * @return the reversed settlement domain model
     */
    fun reverse(groupId: UUID, settlementId: UUID, reason: String, actorSubject: String): Settlement
}
