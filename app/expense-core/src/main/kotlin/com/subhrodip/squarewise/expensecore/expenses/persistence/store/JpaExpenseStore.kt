package com.subhrodip.squarewise.expensecore.expenses.persistence.store
import com.subhrodip.squarewise.expensecore.expenses.api.response.GroupBalanceItem
import com.subhrodip.squarewise.expensecore.expenses.api.request.MoneyDto
import com.subhrodip.squarewise.expensecore.expenses.domain.ExpenseRecord
import com.subhrodip.squarewise.expensecore.expenses.service.toRecord
import com.subhrodip.squarewise.expensecore.expenses.persistence.entity.ExpenseEntity
import com.subhrodip.squarewise.expensecore.expenses.persistence.entity.ExpensePayerEntity
import com.subhrodip.squarewise.expensecore.expenses.persistence.entity.ExpenseAllocationEntity
import com.subhrodip.squarewise.expensecore.expenses.persistence.entity.ExpenseIdempotencyEntity
import com.subhrodip.squarewise.expensecore.expenses.persistence.entity.BalancePostingEntity
import com.subhrodip.squarewise.expensecore.expenses.persistence.repository.ExpenseRepository
import com.subhrodip.squarewise.expensecore.expenses.persistence.repository.ExpenseIdempotencyRepository
import com.subhrodip.squarewise.expensecore.expenses.persistence.repository.BalancePostingRepository
import com.subhrodip.squarewise.expensecore.groups.persistence.repository.GroupMembershipRepository
import com.subhrodip.squarewise.expensecore.groups.persistence.repository.GroupRepository
import com.subhrodip.squarewise.expensecore.messaging.outbox.model.OutboxMessage
import com.subhrodip.squarewise.expensecore.messaging.outbox.persistence.OutboxStore

import com.subhrodip.squarewise.expensecore.sync.persistence.SynchronizationStore
import com.subhrodip.squarewise.ids.generation.UuidGenerator
import java.time.Instant
import java.util.UUID
import java.security.MessageDigest
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.context.annotation.Primary
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import com.subhrodip.squarewise.expensecore.errors.ExpenseDomainException
import com.subhrodip.squarewise.errors.catalog.ExpenseErrors

/**
 * Spring Data JPA implementation of [ExpenseStore].
 *
 * Coordinates transactional expense creation, updates, and soft deletions alongside
 * double-entry ledger postings, group revision increments, outbox messages, and sync records.
 */
@Service
@Primary
class JpaExpenseStore(
    private val expenseRepository: ExpenseRepository,
    private val idempotencyRepository: ExpenseIdempotencyRepository,
    private val balancePostingRepository: BalancePostingRepository,
    private val groupRepository: GroupRepository,
    private val membershipRepository: GroupMembershipRepository,
    private val outboxStore: OutboxStore,
    private val syncStore: SynchronizationStore
) : ExpenseStore {

    /**
     * Persists a new expense, updates group revision, creates ledger postings,
     * emits an outbox event, and creates a sync record.
     */
    @Transactional
    override fun create(
        groupId: UUID,
        expense: ExpenseRecord,
        idempotencyKey: String,
        actorSubject: String?
    ): ExpenseRecord {
        val actor = actorSubject ?: "<unknown>"
        val payloadHash = expensePayloadHash(expense)
        // Serialize all mutations for the group before consulting or creating
        // the idempotency claim. This closes the check-then-insert race for
        // concurrent same-key requests on one group.
        val group = groupRepository.findForMembershipUpdate(groupId)
            ?: throw ExpenseDomainException(ExpenseErrors.ERR_05, "Group $groupId not found")
        requireActiveMembership(groupId, actorSubject)
        validateFinancialParticipants(groupId, expense, actorSubject)
        if (group.status == "ARCHIVED") {
            throw ExpenseDomainException(ExpenseErrors.ERR_06, "Group is archived")
        }
        val priorClaim = idempotencyRepository.findByGroupIdAndActorSubjectAndOperationAndIdempotencyKey(
            groupId, actor, "expense.create", idempotencyKey
        )
        if (priorClaim != null) {
            if (priorClaim.payloadHash != payloadHash) {
                throw ExpenseDomainException(ExpenseErrors.ERR_06, "Idempotency key was already used with a different payload")
            }
            return expenseRepository.findById(priorClaim.expenseId).orElseThrow {
                ExpenseDomainException(ExpenseErrors.ERR_06, "Idempotency record has no committed expense")
            }.toRecord()
        }
        val existing = expenseRepository.findById(expense.expenseId).orElse(null)
        if (existing != null) {
            val existingRecord = existing.toRecord()
            if (existingRecord.amountMinor == expense.amountMinor &&
                existingRecord.currency == expense.currency &&
                existingRecord.description == expense.description &&
                existingRecord.payers == expense.payers &&
                existingRecord.allocations == expense.allocations
            ) {
                return existingRecord
            }
            throw ExpenseDomainException(ExpenseErrors.ERR_06, "Expense already exists with different payload")
        }

        group.revision += 1
        groupRepository.save(group)

        val entity = ExpenseEntity(
            expenseId = expense.expenseId,
            groupId = groupId,
            description = expense.description,
            category = expense.category,
            currency = expense.currency,
            amountMinor = expense.amountMinor,
            version = expense.version,
            allocationMode = expense.allocationMode,
            createdAt = expense.createdAt
        )
        entity.payers.addAll(expense.payers.map {
            ExpensePayerEntity(
                payerId = UuidGenerator.next(),
                expense = entity,
                participantId = it.participantId,
                amountMinor = it.amountMinor
            )
        })
        entity.allocations.addAll(expense.allocations.map {
            ExpenseAllocationEntity(
                allocationId = UuidGenerator.next(),
                expense = entity,
                participantId = it.participantId,
                allocatedMinor = it.allocatedMinor
            )
        })

        val saved = expenseRepository.save(entity)
        idempotencyRepository.save(
            ExpenseIdempotencyEntity(
                idempotencyId = UuidGenerator.next(),
                groupId = groupId,
                actorSubject = actor,
                operation = "expense.create",
                idempotencyKey = idempotencyKey,
                payloadHash = payloadHash,
                expenseId = saved.expenseId
            )
        )

        val postings = mutableListOf<BalancePostingEntity>()
        expense.payers.forEach { payer ->
            postings.add(
                BalancePostingEntity(
                    postingId = UuidGenerator.next(),
                    groupId = groupId,
                    expenseId = expense.expenseId,
                    participantId = payer.participantId,
                    currency = expense.currency,
                    amountMinor = payer.amountMinor,
                    createdAt = expense.createdAt
                )
            )
        }
        expense.allocations.forEach { alloc ->
            postings.add(
                BalancePostingEntity(
                    postingId = UuidGenerator.next(),
                    groupId = groupId,
                    expenseId = expense.expenseId,
                    participantId = alloc.participantId,
                    currency = expense.currency,
                    amountMinor = -alloc.allocatedMinor,
                    createdAt = expense.createdAt
                )
            )
        }
        balancePostingRepository.saveAll(postings)

        val outboxPayload = mapOf<String, Any?>(
            "expenseId" to saved.expenseId.toString(),
            "groupId" to groupId.toString(),
            "subject" to actorSubject,
            "amountMinor" to saved.amountMinor,
            "currency" to saved.currency,
            "version" to saved.version
        )
        outboxStore.append(
            OutboxMessage(
                eventId = UuidGenerator.next(),
                eventType = "expense.created",
                aggregateId = saved.expenseId,
                groupId = groupId,
                groupRevision = group.revision,
                occurredAt = saved.createdAt,
                payload = outboxPayload
            )
        )

        val syncPayload = """{"expenseId":"${saved.expenseId}","groupId":"$groupId","amountMinor":${saved.amountMinor},"currency":"${saved.currency}","version":${saved.version}}"""
        syncStore.append(groupId.toString(), saved.expenseId.toString(), syncPayload)

        return saved.toRecord()
    }

    private fun expensePayloadHash(expense: ExpenseRecord): String {
        val canonical = buildString {
            append(expense.description).append('|').append(expense.category).append('|')
            append(expense.currency).append('|').append(expense.amountMinor).append('|')
            append(expense.allocationMode).append('|')
            expense.payers.sortedBy { it.participantId }.forEach {
                append("P:").append(it.participantId).append(':').append(it.amountMinor).append('|')
            }
            expense.allocations.sortedBy { it.participantId }.forEach {
                append("A:").append(it.participantId).append(':').append(it.allocatedMinor).append('|')
            }
        }
        return MessageDigest.getInstance("SHA-256").digest(canonical.toByteArray())
            .joinToString("") { byte -> "%02x".format(byte) }
    }

    private fun requireActiveMembership(groupId: UUID, actorSubject: String?) {
        if (actorSubject != null && membershipRepository.findByGroupIdAndSubjectAndStatus(groupId, actorSubject, "ACTIVE") == null) {
            throw ExpenseDomainException(ExpenseErrors.ERR_05, "Group $groupId not found")
        }
    }

    /** Compatibility overload for internal callers that do not carry an actor. */
    /** Rejects public postings for arbitrary or duplicate participant identifiers. */
    private fun validateFinancialParticipants(groupId: UUID, expense: ExpenseRecord, actorSubject: String?) {
        if (actorSubject == null) return
        val payerIds = expense.payers.map { it.participantId }
        val allocationIds = expense.allocations.map { it.participantId }
        if (payerIds.size != payerIds.toSet().size || allocationIds.size != allocationIds.toSet().size) {
            throw ExpenseDomainException(ExpenseErrors.ERR_02, "Participant IDs must be unique within payers and allocations")
        }
        val participantIds = payerIds + allocationIds
        val activeIds = membershipRepository.findByGroupIdAndStatus(groupId, "ACTIVE")
            .map { it.membershipId }
            .toSet()
        if (!activeIds.containsAll(participantIds)) {
            throw ExpenseDomainException(ExpenseErrors.ERR_05, "Every financial participant must be an active group member")
        }
    }

    fun update(groupId: UUID, expenseId: UUID, update: ExpenseRecord): ExpenseRecord =
        update(groupId, expenseId, update, null)

    /** Compatibility overload for internal callers that do not carry an actor. */
    fun delete(groupId: UUID, expenseId: UUID, version: Long?) =
        delete(groupId, expenseId, version, null)

    /**
     * Updates an existing expense in place, generates double-entry reversal postings,
     * writes new balance postings, updates group revision, emits outbox and sync events.
     */
    @Transactional
    override fun update(groupId: UUID, expenseId: UUID, update: ExpenseRecord, actorSubject: String?): ExpenseRecord {
        val group = groupRepository.findForMembershipUpdate(groupId)
            ?: throw ExpenseDomainException(ExpenseErrors.ERR_05, "Group $groupId not found")
        requireActiveMembership(groupId, actorSubject)
        validateFinancialParticipants(groupId, update, actorSubject)
        if (group.status == "ARCHIVED") {
            throw ExpenseDomainException(ExpenseErrors.ERR_06, "Group is archived")
        }

        val entity = expenseRepository.findByExpenseIdAndGroupId(expenseId, groupId)
            ?: throw ExpenseDomainException(ExpenseErrors.ERR_05, "Expense $expenseId not found in group $groupId")

        if (entity.deleted) {
            throw ExpenseDomainException(ExpenseErrors.ERR_05, "Expense $expenseId has been deleted")
        }

        if (entity.version != update.version) {
            throw ExpenseDomainException(ExpenseErrors.ERR_06, "Stale version: expected ${entity.version}, but got ${update.version}")
        }

        group.revision += 1
        groupRepository.save(group)

        val now = Instant.now()

        // Reversal postings: find previous postings for this expense and invert their net sums
        val previousPostings = balancePostingRepository.findByExpenseId(expenseId)
        val activePostingSums = previousPostings
            .groupBy { it.participantId to it.currency }
            .mapValues { (_, list) -> list.sumOf { it.amountMinor } }

        val reversalPostings = activePostingSums
            .filter { (_, sum) -> sum != 0L }
            .map { (key, sum) ->
                val (participantId, currency) = key
                BalancePostingEntity(
                    postingId = UuidGenerator.next(),
                    groupId = groupId,
                    expenseId = expenseId,
                    participantId = participantId,
                    currency = currency,
                    amountMinor = -sum,
                    createdAt = now
                )
            }
        if (reversalPostings.isNotEmpty()) {
            balancePostingRepository.saveAll(reversalPostings)
        }

        // New postings
        val newPostings = mutableListOf<BalancePostingEntity>()
        update.payers.forEach { payer ->
            newPostings.add(
                BalancePostingEntity(
                    postingId = UuidGenerator.next(),
                    groupId = groupId,
                    expenseId = expenseId,
                    participantId = payer.participantId,
                    currency = update.currency,
                    amountMinor = payer.amountMinor,
                    createdAt = now
                )
            )
        }
        update.allocations.forEach { alloc ->
            newPostings.add(
                BalancePostingEntity(
                    postingId = UuidGenerator.next(),
                    groupId = groupId,
                    expenseId = expenseId,
                    participantId = alloc.participantId,
                    currency = update.currency,
                    amountMinor = -alloc.allocatedMinor,
                    createdAt = now
                )
            )
        }
        balancePostingRepository.saveAll(newPostings)

        // Update entity state
        entity.description = update.description
        entity.category = update.category
        entity.currency = update.currency
        entity.amountMinor = update.amountMinor
        entity.allocationMode = update.allocationMode
        entity.version = entity.version + 1
        entity.updatedAt = now

        // In-place update of payers to preserve unique constraint on (expense_id, participant_id)
        entity.payers.removeIf { existing -> update.payers.none { it.participantId == existing.participantId } }
        update.payers.forEach { updatePayer ->
            val existing = entity.payers.find { it.participantId == updatePayer.participantId }
            if (existing != null) {
                existing.amountMinor = updatePayer.amountMinor
            } else {
                entity.payers.add(
                    ExpensePayerEntity(
                        payerId = UuidGenerator.next(),
                        expense = entity,
                        participantId = updatePayer.participantId,
                        amountMinor = updatePayer.amountMinor
                    )
                )
            }
        }

        // In-place update of allocations to preserve unique constraint on (expense_id, participant_id)
        entity.allocations.removeIf { existing -> update.allocations.none { it.participantId == existing.participantId } }
        update.allocations.forEach { updateAlloc ->
            val existing = entity.allocations.find { it.participantId == updateAlloc.participantId }
            if (existing != null) {
                existing.allocatedMinor = updateAlloc.allocatedMinor
            } else {
                entity.allocations.add(
                    ExpenseAllocationEntity(
                        allocationId = UuidGenerator.next(),
                        expense = entity,
                        participantId = updateAlloc.participantId,
                        allocatedMinor = updateAlloc.allocatedMinor
                    )
                )
            }
        }

        val saved = expenseRepository.save(entity)

        val outboxPayload = mapOf<String, Any?>(
            "expenseId" to saved.expenseId.toString(),
            "groupId" to groupId.toString(),
            "amountMinor" to saved.amountMinor,
            "currency" to saved.currency,
            "version" to saved.version
        )
        outboxStore.append(
            OutboxMessage(
                eventId = UuidGenerator.next(),
                eventType = "expense.updated",
                aggregateId = saved.expenseId,
                groupId = groupId,
                groupRevision = group.revision,
                occurredAt = now,
                payload = outboxPayload
            )
        )

        val syncPayload = """{"expenseId":"${saved.expenseId}","groupId":"$groupId","amountMinor":${saved.amountMinor},"currency":"${saved.currency}","version":${saved.version}}"""
        syncStore.append(groupId.toString(), saved.expenseId.toString(), syncPayload)

        return saved.toRecord()
    }

    /**
     * Performs a soft delete on an expense, posting reversal balance entries and recording outbox and sync changes.
     */
    @Transactional
    override fun delete(groupId: UUID, expenseId: UUID, version: Long?, actorSubject: String?) {
        val group = groupRepository.findForMembershipUpdate(groupId)
            ?: throw ExpenseDomainException(ExpenseErrors.ERR_05, "Group $groupId not found")
        requireActiveMembership(groupId, actorSubject)
        if (group.status == "ARCHIVED") {
            throw ExpenseDomainException(ExpenseErrors.ERR_06, "Group is archived")
        }

        val entity = expenseRepository.findByExpenseIdAndGroupId(expenseId, groupId)
            ?: throw ExpenseDomainException(ExpenseErrors.ERR_03, "Expense $expenseId not found in group $groupId")

        if (entity.deleted) {
            throw ExpenseDomainException(ExpenseErrors.ERR_05, "Expense $expenseId has already been deleted")
        }

        if (version != null && entity.version != version) {
            throw ExpenseDomainException(ExpenseErrors.ERR_06, "Stale version: expected ${entity.version}, but got $version")
        }

        group.revision += 1
        groupRepository.save(group)

        val now = Instant.now()

        // Double-entry ledger reversal: reverse previous active balance postings
        val previousPostings = balancePostingRepository.findByExpenseId(expenseId)
        val activePostingSums = previousPostings
            .groupBy { it.participantId to it.currency }
            .mapValues { (_, list) -> list.sumOf { it.amountMinor } }

        val reversalPostings = activePostingSums
            .filter { (_, sum) -> sum != 0L }
            .map { (key, sum) ->
                val (participantId, currency) = key
                BalancePostingEntity(
                    postingId = UuidGenerator.next(),
                    groupId = groupId,
                    expenseId = expenseId,
                    participantId = participantId,
                    currency = currency,
                    amountMinor = -sum,
                    createdAt = now
                )
            }
        if (reversalPostings.isNotEmpty()) {
            balancePostingRepository.saveAll(reversalPostings)
        }

        // Soft delete expense retaining attribution
        entity.deleted = true
        entity.version = entity.version + 1
        entity.updatedAt = now
        expenseRepository.save(entity)

        // Outbox event
        val outboxPayload = mapOf<String, Any?>(
            "expenseId" to entity.expenseId.toString(),
            "groupId" to groupId.toString(),
            "version" to entity.version,
            "deleted" to true
        )
        outboxStore.append(
            OutboxMessage(
                eventId = UuidGenerator.next(),
                eventType = "expense.deleted",
                aggregateId = entity.expenseId,
                groupId = groupId,
                groupRevision = group.revision,
                occurredAt = now,
                payload = outboxPayload
            )
        )

        // Sync change tombstone
        syncStore.delete(groupId.toString(), entity.expenseId.toString())
    }

    /**
     * Looks up an active (non-deleted) expense by its ID.
     */
    @Transactional(readOnly = true)
    override fun findById(expenseId: UUID): ExpenseRecord? {
        val entity = expenseRepository.findById(expenseId).orElse(null) ?: return null
        return if (entity.deleted) null else entity.toRecord()
    }

    /**
     * Lists active expenses for a group, optionally filtered by category.
     */
    @Transactional(readOnly = true)
    override fun list(groupId: UUID, category: String?, cursor: String?, limit: Int): List<ExpenseRecord> {
        val boundedLimit = limit.coerceIn(1, 100)
        val page = PageRequest.of(0, boundedLimit, Sort.by(Sort.Direction.DESC, "createdAt"))
        val entities = if (category != null) {
            expenseRepository.findByGroupIdAndCategoryAndDeletedFalseOrderByCreatedAtDesc(groupId, category, page)
        } else {
            expenseRepository.findByGroupIdAndDeletedFalseOrderByCreatedAtDesc(groupId, page)
        }
        return entities.map { it.toRecord() }
    }

    /**
     * Calculates net balances per participant and currency across all postings in a group.
     */
    override fun balances(groupId: UUID): List<GroupBalanceItem> {
        val rows = balancePostingRepository.sumBalancesByGroup(groupId)
        return rows.map { row ->
            val participantId = row[0].toString()
            val currency = row[1].toString()
            val sum = (row[2] as Number).toLong()
            GroupBalanceItem(
                participantId = participantId,
                amount = MoneyDto(currency, sum.toString())
            )
        }.sortedBy { it.participantId }
    }
}
