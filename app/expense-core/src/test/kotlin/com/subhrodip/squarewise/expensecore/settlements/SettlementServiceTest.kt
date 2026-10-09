package com.subhrodip.squarewise.expensecore.settlements

import com.subhrodip.squarewise.expensecore.settlements.domain.Settlement
import com.subhrodip.squarewise.expensecore.settlements.domain.SettlementStatus
import com.subhrodip.squarewise.expensecore.settlements.persistence.InMemorySettlementStore
import com.subhrodip.squarewise.expensecore.settlements.service.SettlementService
import com.subhrodip.squarewise.expensecore.settlements.service.SettlementSuggestionEngine

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
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
        val id = UUID.randomUUID()
        val settlement = service.record(groupId, id, UUID.randomUUID(), UUID.randomUUID(), 1250, "EUR")
        assertEquals(SettlementStatus.RECORDED, settlement.status)
        assertEquals(SettlementStatus.REVERSED, service.reverse(groupId, id, "duplicate").status)
        assertEquals(SettlementStatus.REVERSED, service.reverse(groupId, id, "duplicate retry").status)
    }

    @Test
    fun `same durable settlement identity rejects a different payload`() {
        val service = createService()
        val groupId = UUID.randomUUID()
        val from = UUID.randomUUID()
        val to = UUID.randomUUID()
        service.record(groupId, from, to, 1250, "EUR", "actor-1", "settlement-key-0001")

        assertThrows(SquarewiseException::class.java) {
            service.record(groupId, from, to, 1300, "EUR", "actor-1", "settlement-key-0001")
        }
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
            service.record(groupId, UUID.randomUUID(), participant, participant, 1, "EUR")
        }
        assertThrows(IllegalArgumentException::class.java) {
            service.record(groupId, UUID.randomUUID(), participant, other, 0, "EUR")
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
        assertThrows(IllegalArgumentException::class.java) {
            service.record(groupId, Settlement(UUID.randomUUID(), participant, other, 1, "EUR"), "actor-1", null)
        }
        assertThrows(IllegalArgumentException::class.java) {
            service.record(groupId, Settlement(UUID.randomUUID(), participant, other, 1, "EUR"), null, "settlement-key-0001")
        }
    }

    @Test
    fun `rejects blank reversal reason and returns suggestions from engine`() {
        val service = createService()

        assertThrows(IllegalArgumentException::class.java) {
            service.reverse(UUID.randomUUID(), UUID.randomUUID(), " ")
        }
        assertTrue(service.suggestions(UUID.randomUUID()).isEmpty())
    }

    @Test
    fun `records settlement via entity overload without idempotency and with idempotency`() {
        val store = InMemorySettlementStore()
        val service = createService(store)
        val groupId = UUID.randomUUID()
        val from = UUID.randomUUID()
        val to = UUID.randomUUID()
        val settlement = Settlement(UUID.randomUUID(), from, to, 1000L, "USD")

        // Overload 1: actorSubject == null && idempotencyKey == null
        val direct = service.record(groupId, settlement)
        assertEquals(SettlementStatus.RECORDED, direct.status)
        assertEquals(settlement.id, direct.id)
        assertEquals("USD", direct.currency)

        // Overload 2: actorSubject != null && idempotencyKey != null
        val idempotent = service.record(groupId, settlement, "actor-user", "settlement-key-0002")
        assertEquals(SettlementStatus.RECORDED, idempotent.status)
        assertEquals("USD", idempotent.currency)
    }

    @Test
    fun `delegates suggestions to suggestionEngine`() {
        val service = createService()
        val groupId = UUID.randomUUID()

        val suggestions = service.suggestions(groupId)
        assertTrue(suggestions.isEmpty())
    }
}
