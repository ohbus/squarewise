package com.subhrodip.squarewise.expensecore.expenses

import com.subhrodip.squarewise.expensecore.expenses.domain.ExpenseAllocation
import com.subhrodip.squarewise.expensecore.expenses.domain.ExpensePayer
import com.subhrodip.squarewise.expensecore.expenses.domain.ExpenseRecord
import com.subhrodip.squarewise.expensecore.expenses.persistence.repository.BalancePostingRepository
import com.subhrodip.squarewise.expensecore.expenses.persistence.repository.ExpenseIdempotencyRepository
import com.subhrodip.squarewise.expensecore.expenses.persistence.repository.ExpenseRepository
import com.subhrodip.squarewise.expensecore.expenses.persistence.store.JpaExpenseStore
import com.subhrodip.squarewise.expensecore.groups.api.CreateGroupRequest
import com.subhrodip.squarewise.expensecore.groups.persistence.repository.GroupRepository
import com.subhrodip.squarewise.expensecore.groups.persistence.store.JpaGroupStore
import com.subhrodip.squarewise.expensecore.messaging.outbox.model.OutboxMessage
import com.subhrodip.squarewise.expensecore.messaging.outbox.persistence.OutboxStore
import com.subhrodip.squarewise.expensecore.sync.persistence.SyncChangeRepository
import java.time.Instant
import java.util.UUID
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.doThrow
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.bean.override.mockito.MockitoBean

/** Verifies that a downstream outbox failure rolls back the complete expense mutation. */
@SpringBootTest
class JpaExpenseStoreTransactionRollbackTest @Autowired constructor(
    private val expenseStore: JpaExpenseStore,
    private val groupStore: JpaGroupStore,
    private val groupRepository: GroupRepository,
    private val expenseRepository: ExpenseRepository,
    private val idempotencyRepository: ExpenseIdempotencyRepository,
    private val balancePostingRepository: BalancePostingRepository,
    private val syncChangeRepository: SyncChangeRepository
) {
    @MockitoBean
    private lateinit var outboxStore: OutboxStore

    @Test
    fun `outbox failure rolls back expense ledger idempotency group and sync mutations`() {
        doThrow(IllegalStateException("outbox unavailable"))
            .`when`(outboxStore)
            .append(
                any(OutboxMessage::class.java) ?: OutboxMessage(
                    UUID(0, 0), "test", UUID(0, 0), UUID(0, 0), 0, Instant.EPOCH, emptyMap()
                )
            )

        val group = groupStore.create("alice", CreateGroupRequest("Rollback trip", "TRIP", "EUR"))
        val expenseId = UUID.randomUUID()
        val participantId = UUID.randomUUID()
        val record = ExpenseRecord(
            expenseId = expenseId,
            groupId = group.groupId,
            description = "Must roll back",
            category = "travel",
            currency = "EUR",
            amountMinor = 900,
            version = 1,
            allocationMode = "EXACT",
            createdAt = Instant.now(),
            payers = listOf(ExpensePayer(participantId, 900)),
            allocations = listOf(ExpenseAllocation(participantId, 900))
        )
        val revisionBefore = groupRepository.findById(group.groupId).orElseThrow().revision

        assertThrows(IllegalStateException::class.java) {
            expenseStore.create(group.groupId, record, "rollback-key", validateParticipants = false)
        }

        assertNull(expenseRepository.findById(expenseId).orElse(null))
        assertNull(
            idempotencyRepository.findByGroupIdAndActorSubjectAndOperationAndIdempotencyKey(
                group.groupId,
                "<unknown>",
                "expense.create",
                "rollback-key"
            )
        )
        assertEquals(0, balancePostingRepository.findByGroupId(group.groupId).size)
        assertEquals(0, syncChangeRepository.findMaxRevision(group.groupId.toString()))
        assertEquals(revisionBefore, groupRepository.findById(group.groupId).orElseThrow().revision)
    }
}
