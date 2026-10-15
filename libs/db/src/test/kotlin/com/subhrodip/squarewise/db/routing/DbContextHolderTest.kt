package com.subhrodip.squarewise.db.routing

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Unit tests verifying [DbContextHolder] programmatic routing bootstrap helpers. */
class DbContextHolderTest {

    @Test
    fun `withReader establishes reader eligible execution context with target`() {
        assertNull(DbContextHolder.getActiveContext())

        val result = DbContextHolder.withReader(target = "search", operation = "expense.search") {
            val active = DbContextHolder.current()
            assertEquals("expense.search", active.operationName)
            assertEquals(DbOperationKind.QUERY, active.kind)
            assertEquals(ReadConsistency.EVENTUAL, active.consistency)
            assertTrue(active.readerEligible)
            assertEquals("search", active.targetReader)
            assertNull(active.targetWriter)
            "search-success"
        }

        assertEquals("search-success", result)
        assertNull(DbContextHolder.getActiveContext())
    }

    @Test
    fun `withWriter establishes command execution context with target writer`() {
        assertNull(DbContextHolder.getActiveContext())

        val result = DbContextHolder.withWriter(target = "writer-shard-1", operation = "expense.insert") {
            val active = DbContextHolder.current()
            assertEquals("expense.insert", active.operationName)
            assertEquals(DbOperationKind.COMMAND, active.kind)
            assertEquals(ReadConsistency.STRONG, active.consistency)
            assertFalse(active.readerEligible)
            assertEquals("writer-shard-1", active.targetWriter)
            assertNull(active.targetReader)
            42
        }

        assertEquals(42, result)
        assertNull(DbContextHolder.getActiveContext())
    }

    @Test
    fun `withReader defaults to unassigned target when blank`() {
        DbContextHolder.withReader {
            val active = DbContextHolder.current()
            assertEquals("db.query", active.operationName)
            assertTrue(active.readerEligible)
            assertNull(active.targetReader)
        }
    }

    @Test
    fun `withWriter defaults to primary writer when blank`() {
        DbContextHolder.withWriter {
            val active = DbContextHolder.current()
            assertEquals("db.command", active.operationName)
            assertFalse(active.readerEligible)
            assertNull(active.targetWriter)
        }
    }
}
