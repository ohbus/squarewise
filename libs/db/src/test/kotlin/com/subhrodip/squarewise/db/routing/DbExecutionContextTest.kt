package com.subhrodip.squarewise.db.routing

import com.subhrodip.squarewise.db.errors.DbPlatformException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.assertFailsWith

/** Verifies the stable operation-name invariant at the database execution boundary. */
class DbExecutionContextTest {
    @Test
    fun `exposes execution metadata and parses an optional causal watermark`() {
        val context = DbExecutionContext(
            operationName = "expense.search",
            kind = DbOperationKind.QUERY,
            consistency = ReadConsistency.EVENTUAL,
            readerEligible = true,
            requiredWatermark = "0/20",
        )

        assertEquals("expense.search", context.operationName)
        assertEquals(DbOperationKind.QUERY, context.kind)
        assertEquals(ReadConsistency.EVENTUAL, context.consistency)
        assertTrue(context.readerEligible)
        assertEquals(DbWatermark.parse("0/20"), context.requiredDbWatermark())
        assertFalse(context.isWriterOnly())
    }

    @Test
    fun `writer-only contexts omit absent watermark and reject reader execution`() {
        val context = DbExecutionContext("expense.create", DbOperationKind.COMMAND)

        assertEquals("expense.create", context.operationName)
        assertEquals(DbOperationKind.COMMAND, context.kind)
        assertEquals(ReadConsistency.STRONG, context.consistency)
        assertFalse(context.readerEligible)
        assertNull(context.requiredWatermark)
        assertNull(context.requiredDbWatermark())
        assertTrue(context.isWriterOnly())
    }

    @Test
    fun `operation names must be lowercase dot-delimited identifiers`() {
        listOf("", "Expense.Search", "expense search", ".expense", "expense.", "expense..search")
            .forEach { name ->
                assertFailsWith<DbPlatformException> {
                    DbExecutionContext(name, DbOperationKind.QUERY)
                }
            }
    }
}
