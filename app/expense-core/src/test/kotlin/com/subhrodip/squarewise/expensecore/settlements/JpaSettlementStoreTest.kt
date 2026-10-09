package com.subhrodip.squarewise.expensecore.settlements

import com.subhrodip.squarewise.errors.code.CategoryCode
import com.subhrodip.squarewise.expensecore.settlements.domain.Settlement
import com.subhrodip.squarewise.expensecore.settlements.domain.SettlementStatus
import com.subhrodip.squarewise.expensecore.settlements.persistence.JpaSettlementStore
import com.subhrodip.squarewise.expensecore.expenses.persistence.repository.BalancePostingRepository
import com.subhrodip.squarewise.expensecore.groups.domain.GroupEntity
import com.subhrodip.squarewise.expensecore.groups.persistence.repository.GroupRepository

import com.subhrodip.squarewise.errors.exceptions.SquarewiseException
import com.subhrodip.squarewise.errors.catalog.ExpenseErrors
import java.util.UUID
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.transaction.annotation.Transactional

@SpringBootTest
@Transactional
class JpaSettlementStoreTest @Autowired constructor(
    private val store: JpaSettlementStore,
    private val groupRepository: GroupRepository,
    private val balancePostingRepository: BalancePostingRepository
) {
    @Test
    fun `persists and idempotently reverses a settlement in its group`() {
        val groupId = UUID.randomUUID()
        groupRepository.save(GroupEntity(groupId, "Test group", "HOUSEHOLD", "EUR"))
        val settlement = Settlement(
            UUID.randomUUID(),
            UUID.randomUUID(),
            UUID.randomUUID(),
            1_250,
            "EUR"
        )

        assertEquals(SettlementStatus.RECORDED, store.record(groupId, settlement).status)
        val recordedPostings = balancePostingRepository.findBySettlementId(settlement.id)
        assertEquals(2, recordedPostings.size)
        assertEquals(0L, recordedPostings.sumOf { it.amountMinor })
        assertEquals(1_250L, recordedPostings.first { it.participantId == settlement.fromParticipantId }.amountMinor)
        assertEquals(-1_250L, recordedPostings.first { it.participantId == settlement.toParticipantId }.amountMinor)
        assertEquals(SettlementStatus.REVERSED, store.reverse(groupId, settlement.id, "duplicate").status)
        val allPostings = balancePostingRepository.findBySettlementId(settlement.id)
        assertEquals(4, allPostings.size)
        assertEquals(0L, allPostings.sumOf { it.amountMinor })
        assertEquals(SettlementStatus.REVERSED, store.reverse(groupId, settlement.id, "retry").status)
        assertEquals(4, balancePostingRepository.findBySettlementId(settlement.id).size)
    }

    @Test
    fun `does not expose a settlement through another group`() {
        val groupId = UUID.randomUUID()
        groupRepository.save(GroupEntity(groupId, "Test group", "HOUSEHOLD", "EUR"))
        val settlement = Settlement(
            UUID.randomUUID(),
            UUID.randomUUID(),
            UUID.randomUUID(),
            1_250,
            "EUR"
        )
        store.record(groupId, settlement)

        val error = assertThrows(SquarewiseException::class.java) {
            store.reverse(UUID.randomUUID(), settlement.id, "wrong group")
        }
        assertEquals(CategoryCode.NOT_FOUND, error.definition.category)
    }

    /** Verifies an active group still returns not-found when the settlement row is absent. */
    @Test
    fun `rejects reversal of a missing settlement in an active group`() {
        val groupId = UUID.randomUUID()
        groupRepository.save(GroupEntity(groupId, "Active group", "HOUSEHOLD", "EUR"))

        val error = assertThrows(SquarewiseException::class.java) {
            store.reverse(groupId, UUID.randomUUID(), "missing")
        }

        assertEquals(CategoryCode.NOT_FOUND, error.definition.category)
        assertEquals(ExpenseErrors.SETTLEMENT_NOT_FOUND.numericCode, error.definition.numericCode)
        assertEquals(ExpenseErrors.SETTLEMENT_NOT_FOUND.errorName, error.definition.errorName)
    }

    /** Verifies settlement replay compares every financial identity dimension before returning an existing row. */
    @Test
    fun `rejects conflicting settlement replays without additional postings`() {
        val groupId = UUID.randomUUID()
        groupRepository.save(GroupEntity(groupId, "Replay group", "HOUSEHOLD", "EUR"))
        val from = UUID.randomUUID()
        val to = UUID.randomUUID()
        val settlement = Settlement(UUID.randomUUID(), from, to, 1_250, "EUR", reason = "original")
        store.record(groupId, settlement)

        assertEquals(settlement.id, store.record(groupId, settlement.copy(reason = "same identity")).id)
        listOf(
            settlement.copy(fromParticipantId = UUID.randomUUID()),
            settlement.copy(toParticipantId = UUID.randomUUID()),
            settlement.copy(amountMinor = 1_251),
            settlement.copy(currency = "USD")
        ).forEach { conflicting ->
            val error = assertThrows(SquarewiseException::class.java) {
                store.record(groupId, conflicting)
            }
            assertEquals(CategoryCode.STATE_CONFLICT, error.definition.category)
            assertEquals(ExpenseErrors.EXPENSE_IDEMPOTENCY_CONFLICT.numericCode, error.definition.numericCode)
            assertEquals(ExpenseErrors.EXPENSE_IDEMPOTENCY_CONFLICT.errorName, error.definition.errorName)
        }
        assertEquals(2, balancePostingRepository.findBySettlementId(settlement.id).size)
    }

    /** Verifies settlements in non-default currencies persist and post in the settlement currency. */
    @Test
    fun `persists multi-currency settlement in non-default currency and creates postings in settlement currency`() {
        val groupId = UUID.randomUUID()
        groupRepository.save(GroupEntity(groupId, "Multi-currency group", "TRIP", "EUR"))
        val from = UUID.randomUUID()
        val to = UUID.randomUUID()
        val usdSettlement = Settlement(
            UUID.randomUUID(),
            from,
            to,
            4_500,
            "USD"
        )

        val recorded = store.record(groupId, usdSettlement)
        assertEquals(SettlementStatus.RECORDED, recorded.status)
        assertEquals("USD", recorded.currency)

        val postings = balancePostingRepository.findBySettlementId(usdSettlement.id)
        assertEquals(2, postings.size)
        assertEquals(true, postings.all { it.currency == "USD" })
        assertEquals(4_500L, postings.first { it.participantId == from }.amountMinor)
        assertEquals(-4_500L, postings.first { it.participantId == to }.amountMinor)

        val reversed = store.reverse(groupId, usdSettlement.id, "reversal test")
        assertEquals(SettlementStatus.REVERSED, reversed.status)
        assertEquals("USD", reversed.currency)

        val allPostings = balancePostingRepository.findBySettlementId(usdSettlement.id)
        assertEquals(4, allPostings.size)
        assertEquals(true, allPostings.all { it.currency == "USD" })
        assertEquals(0L, allPostings.sumOf { it.amountMinor })
    }

    /** Verifies missing and archived settlement mutations fail closed without creating ledger postings. */
    @Test
    fun `rejects settlement mutations for missing or archived groups`() {
        val missingGroupError = assertThrows(SquarewiseException::class.java) {
            store.record(UUID.randomUUID(), Settlement(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), 100, "EUR"))
        }
        assertEquals(CategoryCode.NOT_FOUND, missingGroupError.definition.category)

        val archivedGroupId = UUID.randomUUID()
        groupRepository.save(GroupEntity(archivedGroupId, "Archived group", "HOUSEHOLD", "EUR", status = "ARCHIVED"))
        val archivedSettlement = Settlement(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), 100, "EUR")
        val archivedRecordError = assertThrows(SquarewiseException::class.java) {
            store.record(archivedGroupId, archivedSettlement)
        }
        assertEquals(CategoryCode.STATE_CONFLICT, archivedRecordError.definition.category)
        assertEquals(ExpenseErrors.GROUP_ARCHIVED.numericCode, archivedRecordError.definition.numericCode)

        val archivedReverseError = assertThrows(SquarewiseException::class.java) {
            store.reverse(archivedGroupId, archivedSettlement.id, "archived")
        }
        assertEquals(CategoryCode.STATE_CONFLICT, archivedReverseError.definition.category)
        assertEquals(ExpenseErrors.GROUP_ARCHIVED.numericCode, archivedReverseError.definition.numericCode)
        assertEquals(0, balancePostingRepository.findBySettlementId(archivedSettlement.id).size)
    }
}
