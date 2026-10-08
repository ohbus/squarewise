package com.subhrodip.squarewise.expensecore.settlements.persistence

import com.subhrodip.squarewise.expensecore.settlements.domain.Settlement
import com.subhrodip.squarewise.expensecore.settlements.domain.SettlementStatus
import com.subhrodip.squarewise.expensecore.expenses.persistence.entity.BalancePostingEntity
import com.subhrodip.squarewise.expensecore.expenses.persistence.repository.BalancePostingRepository
import com.subhrodip.squarewise.expensecore.groups.persistence.repository.GroupRepository

import com.subhrodip.squarewise.ids.generation.UuidGenerator
import java.util.UUID
import org.springframework.context.annotation.Primary
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import com.subhrodip.squarewise.expensecore.errors.ExpenseDomainException
import com.subhrodip.squarewise.errors.catalog.ExpenseErrors

/**
 * JPA persistence adapter implementing [SettlementStore] to record settlements and execute reversals.
 *
 * Invariants:
 * - Recording a settlement is idempotent if a settlement with the same ID and group ID already exists.
 * - Reversing a settlement acquires a pessimistic write lock and transitions the settlement status to REVERSED.
 * - Mutating settlement operations check that target group is not archived.
 */
@Primary
@Service
class JpaSettlementStore(
    private val repository: SettlementRepository,
    private val groupRepository: GroupRepository,
    private val balancePostingRepository: BalancePostingRepository
) : SettlementStore {

    /**
     * Records a new settlement or returns the existing record if already present.
     *
     * @param groupId the UUID of the group
     * @param settlement the domain settlement data to record
     * @return the persisted or existing domain settlement
     */
    @Transactional
    override fun record(groupId: UUID, settlement: Settlement): Settlement {
        val group = checkActiveGroup(groupId)
        val existing = repository.findBySettlementIdAndGroupId(settlement.id, groupId)
        if (existing != null) {
            if (existing.fromParticipantId != settlement.fromParticipantId ||
                existing.toParticipantId != settlement.toParticipantId ||
                existing.amountMinor != settlement.amountMinor
            ) {
                throw ExpenseDomainException(ExpenseErrors.GROUP_NAME_CONFLICT, "Idempotency key was already used with a different settlement")
            }
            return existing.toDomain(group.currency)
        }
        val saved = repository.save(settlement.toEntity(groupId))
        balancePostingRepository.saveAll(
            listOf(
                BalancePostingEntity(
                    postingId = UuidGenerator.next(),
                    groupId = groupId,
                    settlementId = saved.settlementId,
                    participantId = saved.fromParticipantId,
                    currency = group.currency,
                    amountMinor = saved.amountMinor
                ),
                BalancePostingEntity(
                    postingId = UuidGenerator.next(),
                    groupId = groupId,
                    settlementId = saved.settlementId,
                    participantId = saved.toParticipantId,
                    currency = group.currency,
                    amountMinor = -saved.amountMinor
                )
            )
        )
        return saved.toDomain(group.currency)
    }

    /**
     * Reverses a settlement with the given reason, acquiring a pessimistic lock.
     *
     * @param groupId the UUID of the group
     * @param settlementId the UUID of the settlement to reverse
     * @param reason explanation for the reversal
     * @return the updated domain settlement with REVERSED status
     * @throws ExpenseDomainException with [ExpenseErrors.GROUP_NOT_FOUND] if the settlement cannot be found
     */
    @Transactional
    override fun reverse(groupId: UUID, settlementId: UUID, reason: String): Settlement {
        checkActiveGroup(groupId)
        val entity = repository.findForUpdate(settlementId, groupId)
            ?: throw ExpenseDomainException(ExpenseErrors.GROUP_NOT_FOUND, "Settlement not found")
        if (entity.status == SettlementStatus.REVERSED) return entity.toDomain(checkActiveGroup(groupId).currency)
        entity.status = SettlementStatus.REVERSED
        entity.reversalReason = reason
        val postings = balancePostingRepository.findBySettlementId(settlementId)
        balancePostingRepository.saveAll(
            postings.map { posting ->
                BalancePostingEntity(
                    postingId = UuidGenerator.next(),
                    groupId = posting.groupId,
                    settlementId = settlementId,
                    participantId = posting.participantId,
                    currency = posting.currency,
                    amountMinor = -posting.amountMinor
                )
            }
        )
        return repository.save(entity).toDomain(checkActiveGroup(groupId).currency)
    }

    private fun checkActiveGroup(groupId: UUID) = groupRepository.findById(groupId).orElse(null)?.also { group ->
        if (group.status == "ARCHIVED") {
            throw ExpenseDomainException(ExpenseErrors.GROUP_NAME_CONFLICT, "Group is archived")
        }
    } ?: throw ExpenseDomainException(ExpenseErrors.GROUP_NOT_FOUND, "Group $groupId not found")
}

private fun Settlement.toEntity(groupId: UUID) = SettlementEntity(
    settlementId = id,
    groupId = groupId,
    fromParticipantId = fromParticipantId,
    toParticipantId = toParticipantId,
    amountMinor = amountMinor,
    reversalReason = reason,
    status = status
)

private fun SettlementEntity.toDomain(currency: String) = Settlement(
    id = settlementId,
    fromParticipantId = fromParticipantId,
    toParticipantId = toParticipantId,
    amountMinor = amountMinor,
    currency = currency,
    reason = reversalReason,
    status = status
)
