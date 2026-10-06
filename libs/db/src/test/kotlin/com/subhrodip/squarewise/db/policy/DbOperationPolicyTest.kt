package com.subhrodip.squarewise.db.policy

import com.subhrodip.squarewise.db.routing.DbOperationKind
import com.subhrodip.squarewise.db.routing.DbRoute
import com.subhrodip.squarewise.db.routing.ReadConsistency
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/** Verifies operation-name and reader-eligibility invariants for database routing. */
class DbOperationPolicyTest {
    @Test
    fun `writer-only operations default to writer`() {
        val policy = DbOperationPolicy("expense.create", DbOperationKind.COMMAND)

        assertEquals("expense.create", policy.operationName)
        assertEquals(DbOperationKind.COMMAND, policy.kind)
        assertEquals(ReadConsistency.STRONG, policy.consistency)
        assertEquals(false, policy.readerEligible)
        assertEquals(DbRoute.WRITER, policy.defaultRoute())
    }

    @Test
    fun `non-strong queries may explicitly use the reader`() {
        val policy = DbOperationPolicy(
            operationName = "expense.search",
            kind = DbOperationKind.QUERY,
            consistency = ReadConsistency.EVENTUAL,
            readerEligible = true,
        )

        assertEquals("expense.search", policy.operationName)
        assertEquals(DbOperationKind.QUERY, policy.kind)
        assertEquals(ReadConsistency.EVENTUAL, policy.consistency)
        assertEquals(true, policy.readerEligible)
        assertEquals(DbRoute.READER, policy.defaultRoute())
    }

    @Test
    fun `operation names must be stable lowercase identifiers`() {
        listOf("", "Expense.Search", "expense search", ".expense", "expense.", "expense..search")
            .forEach { name ->
                assertFailsWith<IllegalArgumentException> {
                    DbOperationPolicy(name, DbOperationKind.QUERY)
                }
            }
    }

    @Test
    fun `reader eligibility requires a non-mutating query`() {
        listOf(
            DbOperationKind.COMMAND,
            DbOperationKind.LOCKING_QUERY,
            DbOperationKind.CLAIM,
            DbOperationKind.MIGRATION,
            DbOperationKind.RECONCILIATION,
        ).forEach { kind ->
            assertFailsWith<IllegalArgumentException> {
                DbOperationPolicy(
                    operationName = "operation.check",
                    kind = kind,
                    consistency = ReadConsistency.EVENTUAL,
                    readerEligible = true,
                )
            }
        }
    }

    @Test
    fun `reader eligible queries cannot require strong consistency`() {
        assertFailsWith<IllegalArgumentException> {
            DbOperationPolicy(
                operationName = "operation.check",
                kind = DbOperationKind.QUERY,
                consistency = ReadConsistency.STRONG,
                readerEligible = true,
            )
        }
    }
}
