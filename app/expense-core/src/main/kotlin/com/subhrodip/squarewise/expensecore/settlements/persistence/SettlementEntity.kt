package com.subhrodip.squarewise.expensecore.settlements.persistence

import com.subhrodip.squarewise.expensecore.settlements.domain.SettlementStatus

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.util.UUID

/**
 * Persistent JPA entity representing a settlement between two group participants.
 *
 * Invariants:
 * - [settlementId] is a unique primary key UUID.
 * - [groupId] references the owning group for the settlement.
 * - [fromParticipantId] and [toParticipantId] represent the paying and receiving participants respectively.
 * - [amountMinor] represents the non-negative transfer amount in minor currency units (e.g. cents).
 * - [status] tracks the lifecycle state (RECORDED or REVERSED).
 * - [reversalReason] documents why the settlement was reversed if status is REVERSED.
 */
@Entity
@Table(name = "settlements")
class SettlementEntity(
    @Id
    @Column(name = "settlement_id", nullable = false)
    var settlementId: UUID,
    @Column(name = "group_id", nullable = false)
    var groupId: UUID,
    @Column(name = "from_participant_id", nullable = false)
    var fromParticipantId: UUID,
    @Column(name = "to_participant_id", nullable = false)
    var toParticipantId: UUID,
    @Column(name = "amount_minor", nullable = false)
    var amountMinor: Long,
    @Column(name = "reversal_reason", length = 240)
    var reversalReason: String? = null,
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    var status: SettlementStatus = SettlementStatus.RECORDED
)
