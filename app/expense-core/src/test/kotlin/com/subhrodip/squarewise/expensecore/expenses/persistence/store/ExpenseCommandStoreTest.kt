package com.subhrodip.squarewise.expensecore.expenses.persistence.store

import com.subhrodip.squarewise.expensecore.expenses.domain.ExpenseRecord
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID

/** Verifies default argument dispatch for ExpenseCommandStore interface methods. */
class ExpenseCommandStoreTest {

    @Test
    fun `verifies interface default arguments pass null actorSubject`() {
        var createActor: String? = "sentinel"
        var updateActor: String? = "sentinel"
        var deleteActor: String? = "sentinel"

        val sampleRecord = ExpenseRecord(
            expenseId = UUID.randomUUID(),
            groupId = UUID.randomUUID(),
            description = "test",
            category = "test",
            currency = "USD",
            amountMinor = 100,
            version = 1,
            allocationMode = "EQUAL",
            createdAt = Instant.now(),
            payers = emptyList(),
            allocations = emptyList()
        )

        val store = object : ExpenseCommandStore {
            override fun create(groupId: UUID, expense: ExpenseRecord, idempotencyKey: String, actorSubject: String?): ExpenseRecord {
                createActor = actorSubject
                return expense
            }

            override fun update(groupId: UUID, expenseId: UUID, update: ExpenseRecord, actorSubject: String?): ExpenseRecord {
                updateActor = actorSubject
                return update
            }

            override fun delete(groupId: UUID, expenseId: UUID, version: Long?, actorSubject: String?) {
                deleteActor = actorSubject
            }
        }

        val gId = UUID.randomUUID()
        val eId = UUID.randomUUID()

        store.create(gId, sampleRecord, "key-1")
        assertEquals(null, createActor)

        store.update(gId, eId, sampleRecord)
        assertEquals(null, updateActor)

        store.delete(gId, eId, 1L)
        assertEquals(null, deleteActor)
    }
}
