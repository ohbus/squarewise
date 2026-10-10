package com.subhrodip.squarewise.expensecore.settlements

import com.subhrodip.squarewise.expensecore.settlements.domain.SettlementStatus
import com.subhrodip.squarewise.expensecore.expenses.domain.ExpenseAllocation
import com.subhrodip.squarewise.expensecore.expenses.domain.ExpensePayer
import com.subhrodip.squarewise.expensecore.expenses.domain.ExpenseRecord
import com.subhrodip.squarewise.expensecore.settlements.persistence.InMemorySettlementStore
import com.subhrodip.squarewise.expensecore.settlements.service.SettlementService
import com.subhrodip.squarewise.expensecore.settlements.service.SettlementSuggestionEngine

import com.subhrodip.squarewise.errors.catalog.ExpenseErrors
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID
import org.junit.jupiter.api.Assertions.assertThrows
import com.subhrodip.squarewise.errors.exceptions.SquarewiseException

/** Verifies settlement-service validation, idempotency, reversal, and suggestion delegation. */
class SettlementServiceTest {
    private val expenseStore = com.subhrodip.squarewise.expensecore.expenses.persistence.store.InMemoryExpenseStore()
    private val engine = SettlementSuggestionEngine(expenseStore)

    private fun createService(store: com.subhrodip.squarewise.expensecore.settlements.persistence.SettlementStore = InMemorySettlementStore()) =
        SettlementService(store, engine)

    @Test
    fun `records positive transfer and idempotently reverses`() {
        val service = createService()
        val groupId = UUID.randomUUID()
        val settlement = service.record(
            groupId,
            UUID.randomUUID(),
            UUID.randomUUID(),
            1250,
            "EUR",
            "actor-1",
            "settlement-key-0001"
        )
        assertEquals(SettlementStatus.RECORDED, settlement.status)
        assertEquals(SettlementStatus.REVERSED, service.reverse(groupId, settlement.id, "duplicate", "actor-1").status)
        assertEquals(SettlementStatus.REVERSED, service.reverse(groupId, settlement.id, "duplicate retry", "actor-1").status)
    }

    @Test
    fun `same durable settlement identity rejects a different payload`() {
        val service = createService()
        val groupId = UUID.randomUUID()
        val from = UUID.randomUUID()
        val to = UUID.randomUUID()
        service.record(groupId, from, to, 1250, "EUR", "actor-1", "settlement-key-0001")

        val conflict = assertThrows(SquarewiseException::class.java) {
            service.record(groupId, from, to, 1300, "EUR", "actor-1", "settlement-key-0001")
        }
        assertEquals(ExpenseErrors.EXPENSE_IDEMPOTENCY_CONFLICT.numericCode, conflict.definition.numericCode)
        assertEquals(ExpenseErrors.EXPENSE_IDEMPOTENCY_CONFLICT.errorName, conflict.definition.errorName)
        assertThrows(SquarewiseException::class.java) {
            service.record(groupId, from, to, 1250, "USD", "actor-1", "settlement-key-0001")
        }
    }

    @Test
    fun `authenticated replay derives one durable settlement identity`() {
        val service = createService()
        val groupId = UUID.randomUUID()
        val from = UUID.randomUUID()
        val to = UUID.randomUUID()

        val first = service.record(groupId, from, to, 1250, "EUR", "actor-1", "settlement-key-0001")
        val replay = service.record(groupId, from, to, 1250, "EUR", "actor-1", "settlement-key-0001")

        assertEquals(first.id, replay.id)
        assertEquals(first, replay)
    }

    @Test
    fun `rejects invalid participants amount and authenticated idempotency inputs`() {
        val service = createService()
        val groupId = UUID.randomUUID()
        val participant = UUID.randomUUID()
        val other = UUID.randomUUID()

        assertThrows(IllegalArgumentException::class.java) {
            service.record(groupId, participant, participant, 1, "EUR", "actor-1", "settlement-key-0001")
        }
        assertThrows(IllegalArgumentException::class.java) {
            service.record(groupId, participant, other, 0, "EUR", "actor-1", "settlement-key-0001")
        }
        assertThrows(IllegalArgumentException::class.java) {
            service.record(groupId, participant, other, 1, "EUR", " ", "settlement-key-0001")
        }
        assertThrows(IllegalArgumentException::class.java) {
            service.record(groupId, participant, other, 1, "EUR", "actor-1", "short")
        }
        assertThrows(IllegalArgumentException::class.java) {
            service.record(groupId, participant, other, 1, "EUR", "actor-1", "x".repeat(201))
        }
    }

    @Test
    fun `rejects blank reversal reason and returns suggestions from engine`() {
        val service = createService()

        assertThrows(IllegalArgumentException::class.java) {
            service.reverse(UUID.randomUUID(), UUID.randomUUID(), " ", "actor-1")
        }
        assertThrows(IllegalArgumentException::class.java) {
            service.reverse(UUID.randomUUID(), UUID.randomUUID(), "valid reason", " ")
        }
        assertTrue(service.suggestions(UUID.randomUUID()).isEmpty())
    }

    @Test
    fun `delegates suggestions to suggestionEngine`() {
        val service = createService()
        val groupId = UUID.randomUUID()
        val debtor = UUID.randomUUID()
        val creditor = UUID.randomUUID()
        expenseStore.create(
            groupId,
            ExpenseRecord(
                expenseId = UUID.randomUUID(),
                groupId = groupId,
                description = "Dinner",
                category = "food",
                currency = "EUR",
                amountMinor = 200,
                version = 1,
                allocationMode = "EQUAL",
                createdAt = Instant.now(),
                payers = listOf(ExpensePayer(creditor, 200)),
                allocations = listOf(ExpenseAllocation(creditor, 100), ExpenseAllocation(debtor, 100))
            ),
            "settlement-key-0002"
        )

        val suggestions = service.suggestions(groupId)
        assertTrue(suggestions.isNotEmpty())
        assertEquals(debtor, suggestions.single().fromParticipantId)
        assertEquals(creditor, suggestions.single().toParticipantId)
    }
}
