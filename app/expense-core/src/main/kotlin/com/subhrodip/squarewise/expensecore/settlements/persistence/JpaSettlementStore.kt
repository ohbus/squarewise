package com.subhrodip.squarewise.expensecore.settlements.persistence

import com.subhrodip.squarewise.expensecore.settlements.domain.Settlement
import com.subhrodip.squarewise.expensecore.settlements.domain.SettlementStatus
import com.subhrodip.squarewise.expensecore.expenses.persistence.entity.BalancePostingEntity
import com.subhrodip.squarewise.expensecore.expenses.persistence.repository.BalancePostingRepository
import com.subhrodip.squarewise.expensecore.groups.persistence.repository.GroupRepository
import com.subhrodip.squarewise.expensecore.groups.persistence.repository.GroupMembershipRepository
import com.subhrodip.squarewise.expensecore.groups.persistence.store.GroupAuditCommandStore
import com.subhrodip.squarewise.expensecore.groups.domain.GroupAuditEntity
import com.subhrodip.squarewise.expensecore.groups.domain.GroupEntity
import com.subhrodip.squarewise.expensecore.sync.persistence.SynchronizationStore
import com.subhrodip.squarewise.expensecore.messaging.outbox.model.OutboxMessage
import com.subhrodip.squarewise.expensecore.messaging.outbox.persistence.OutboxStore

import com.subhrodip.squarewise.ids.generation.UuidGenerator
import java.util.UUID
import java.time.Instant
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
    private val membershipRepository: GroupMembershipRepository,
    private val balancePostingRepository: BalancePostingRepository,
    private val audit: GroupAuditCommandStore,
    private val synchronization: SynchronizationStore,
    private val outbox: OutboxStore
) : SettlementStore {

    /**
     * Records a new settlement or returns the existing record if already present.
     *
     * @param groupId the UUID of the group
     * @param settlement the domain settlement data to record
     * @param actorSubject the authenticated subject recording the settlement
     * @return the persisted or existing domain settlement
     */
    @Transactional
    override fun record(groupId: UUID, settlement: Settlement, actorSubject: String): Settlement {
        val group = lockActiveGroup(groupId, actorSubject)
        val existing = repository.findBySettlementIdAndGroupId(settlement.id, groupId)
        if (existing != null) {
            if (existing.fromParticipantId != settlement.fromParticipantId ||
                existing.toParticipantId != settlement.toParticipantId ||
                existing.amountMinor != settlement.amountMinor ||
                existing.currency != settlement.currency
            ) {
                throw ExpenseDomainException(ExpenseErrors.EXPENSE_IDEMPOTENCY_CONFLICT, "Idempotency key was already used with a different settlement")
            }
            return existing.toDomain()
        }
        validateParticipants(groupId, settlement)
        val saved = repository.save(settlement.toEntity(groupId))
        balancePostingRepository.saveAll(
            listOf(
                BalancePostingEntity(
                    postingId = UuidGenerator.next(),
                    groupId = groupId,
                    settlementId = saved.settlementId,
                    participantId = saved.fromParticipantId,
                    currency = saved.currency,
                    amountMinor = saved.amountMinor
                ),
                BalancePostingEntity(
                    postingId = UuidGenerator.next(),
                    groupId = groupId,
                    settlementId = saved.settlementId,
                    participantId = saved.toParticipantId,
                    currency = saved.currency,
                    amountMinor = -saved.amountMinor
                )
            )
        )
        recordMutation(group, actorSubject, "settlement.recorded", saved.toDomain())
        return saved.toDomain()
    }

    /**
     * Reverses a settlement with the given reason, acquiring a pessimistic lock.
     *
     * @param groupId the UUID of the group
     * @param settlementId the UUID of the settlement to reverse
     * @param reason explanation for the reversal
     * @param actorSubject the authenticated subject reversing the settlement
     * @return the updated domain settlement with REVERSED status
     * @throws ExpenseDomainException with [ExpenseErrors.SETTLEMENT_NOT_FOUND] if the settlement cannot be found
     */
    @Transactional
    override fun reverse(groupId: UUID, settlementId: UUID, reason: String, actorSubject: String): Settlement {
        val group = lockActiveGroup(groupId, actorSubject)
        val entity = repository.findForUpdate(settlementId, groupId)
            ?: throw ExpenseDomainException(ExpenseErrors.SETTLEMENT_NOT_FOUND, "Settlement $settlementId not found")
        if (entity.status == SettlementStatus.REVERSED) return entity.toDomain()
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
        val reversed = repository.save(entity).toDomain()
        recordMutation(group, actorSubject, "settlement.reversed", reversed)
        return reversed
    }

    private fun lockActiveGroup(groupId: UUID, actorSubject: String) = groupRepository.findForMembershipUpdate(groupId)?.also { group ->
        if (group.status == "ARCHIVED") throw ExpenseDomainException(ExpenseErrors.GROUP_ARCHIVED, "Group is archived")
        if (!membershipRepository.existsByGroupIdAndSubjectAndStatus(groupId, actorSubject, "ACTIVE")) {
            throw ExpenseDomainException(ExpenseErrors.GROUP_ACCESS_HIDDEN, "Group $groupId not found")
        }
    } ?: throw ExpenseDomainException(ExpenseErrors.GROUP_NOT_FOUND, "Group $groupId not found")

    private fun validateParticipants(groupId: UUID, settlement: Settlement) {
        require(settlement.fromParticipantId != settlement.toParticipantId) { "Participants must differ" }
        if (membershipRepository.findByMembershipIdAndGroupIdAndStatus(settlement.fromParticipantId, groupId, "ACTIVE") == null ||
            membershipRepository.findByMembershipIdAndGroupIdAndStatus(settlement.toParticipantId, groupId, "ACTIVE") == null
        ) {
            throw ExpenseDomainException(ExpenseErrors.GROUP_NOT_FOUND, "Settlement participants must be active group members")
        }
    }

    private fun recordMutation(group: GroupEntity, actorSubject: String, action: String, settlement: Settlement) {
        group.revision += 1
        groupRepository.save(group)
        val occurredAt = Instant.now()
        val payload = mapOf(
            "groupId" to group.groupId.toString(),
            "settlementId" to settlement.id.toString(),
            "fromParticipantId" to settlement.fromParticipantId.toString(),
            "toParticipantId" to settlement.toParticipantId.toString(),
            "amountMinor" to settlement.amountMinor,
            "currency" to settlement.currency,
            "status" to settlement.status.name,
            "subject" to actorSubject,
            "changedBy" to actorSubject,
            "revision" to group.revision
        )
        audit.save(GroupAuditEntity(UuidGenerator.next(), group.groupId, actorSubject, action, group.revision, payload.toString(), occurredAt))
        synchronization.append(group.groupId.toString(), settlement.id.toString(), payload.toString())
        outbox.append(OutboxMessage(UuidGenerator.next(), "settlement.${action.removePrefix("settlement.")}.v1", settlement.id, group.groupId, group.revision, occurredAt, payload))
    }
}

private fun Settlement.toEntity(groupId: UUID) = SettlementEntity(
    settlementId = id,
    groupId = groupId,
    fromParticipantId = fromParticipantId,
    toParticipantId = toParticipantId,
    amountMinor = amountMinor,
    currency = currency,
    reversalReason = reason,
    status = status
)

private fun SettlementEntity.toDomain() = Settlement(
    id = settlementId,
    fromParticipantId = fromParticipantId,
    toParticipantId = toParticipantId,
    amountMinor = amountMinor,
    currency = currency,
    reason = reversalReason,
    status = status
)
