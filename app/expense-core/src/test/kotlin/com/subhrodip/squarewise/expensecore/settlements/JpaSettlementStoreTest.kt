package com.subhrodip.squarewise.expensecore.settlements
import com.subhrodip.squarewise.expensecore.settlements.domain.Settlement
import com.subhrodip.squarewise.expensecore.settlements.domain.SettlementStatus
import com.subhrodip.squarewise.expensecore.settlements.persistence.JpaSettlementStore
import com.subhrodip.squarewise.expensecore.expenses.persistence.repository.BalancePostingRepository
import com.subhrodip.squarewise.expensecore.groups.domain.GroupEntity
import com.subhrodip.squarewise.expensecore.groups.persistence.repository.GroupRepository

import com.subhrodip.squarewise.errors.exceptions.SquarewiseException
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
        assertEquals("NOT_FOUND", error.definition.legacyCode)
    }

    /** Verifies an active group still returns not-found when the settlement row is absent. */
    @Test
    fun `rejects reversal of a missing settlement in an active group`() {
        val groupId = UUID.randomUUID()
        groupRepository.save(GroupEntity(groupId, "Active group", "HOUSEHOLD", "EUR"))

        val error = assertThrows(SquarewiseException::class.java) {
            store.reverse(groupId, UUID.randomUUID(), "missing")
        }

        assertEquals("NOT_FOUND", error.definition.legacyCode)
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
            settlement.copy(amountMinor = 1_251)
        ).forEach { conflicting ->
            val error = assertThrows(SquarewiseException::class.java) {
                store.record(groupId, conflicting)
            }
            assertEquals("CONFLICT", error.definition.legacyCode)
        }
        assertEquals(2, balancePostingRepository.findBySettlementId(settlement.id).size)
    }

    /** Verifies missing and archived settlement mutations fail closed without creating ledger postings. */
    @Test
    fun `rejects settlement mutations for missing or archived groups`() {
        val missingGroupError = assertThrows(SquarewiseException::class.java) {
            store.record(UUID.randomUUID(), Settlement(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), 100, "EUR"))
        }
        assertEquals("NOT_FOUND", missingGroupError.definition.legacyCode)

        val archivedGroupId = UUID.randomUUID()
        groupRepository.save(GroupEntity(archivedGroupId, "Archived group", "HOUSEHOLD", "EUR", status = "ARCHIVED"))
        val archivedSettlement = Settlement(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), 100, "EUR")
        val archivedRecordError = assertThrows(SquarewiseException::class.java) {
            store.record(archivedGroupId, archivedSettlement)
        }
        assertEquals("CONFLICT", archivedRecordError.definition.legacyCode)
        val archivedReverseError = assertThrows(SquarewiseException::class.java) {
            store.reverse(archivedGroupId, archivedSettlement.id, "archived")
        }
        assertEquals("CONFLICT", archivedReverseError.definition.legacyCode)
        assertEquals(0, balancePostingRepository.findBySettlementId(archivedSettlement.id).size)
    }
}
