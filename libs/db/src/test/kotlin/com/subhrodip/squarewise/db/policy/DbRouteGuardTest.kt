package com.subhrodip.squarewise.db.policy

import com.subhrodip.squarewise.db.errors.DbPlatformException
import com.subhrodip.squarewise.db.routing.DbExecutionContext
import com.subhrodip.squarewise.db.routing.DbOperationKind
import com.subhrodip.squarewise.db.routing.DbRoute
import com.subhrodip.squarewise.db.routing.ReadConsistency
import com.subhrodip.squarewise.db.routing.DbCausalContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import com.subhrodip.squarewise.db.routing.DbContextHolder

/** Verifies the first writer-safety boundary independently of Spring. */
class DbRouteGuardTest {
    private val guard = DbRouteGuard()

    @Test
    fun `commands cannot use a reader`() {
        assertFailsWith<DbPlatformException> {
            guard.validate(DbExecutionContext("expense.create", DbOperationKind.COMMAND), DbRoute.READER)
        }
    }

    @Test
    fun `locking queries cannot use a reader`() {
        assertFailsWith<DbPlatformException> {
            guard.validate(DbExecutionContext("group.lock", DbOperationKind.LOCKING_QUERY), DbRoute.READER)
        }
    }

    @Test
    fun `eventual queries may use a reader`() {
        guard.validate(
            DbExecutionContext("expense.search", DbOperationKind.QUERY, ReadConsistency.EVENTUAL, readerEligible = true),
            DbRoute.READER
        )
        assertIs<DbRoute>(DbRoute.READER)
    }

    @Test
    fun `context is restored after scoped execution`() {
        val before = DbContextHolder.current()
        DbContextHolder.withContext(DbExecutionContext("expense.search", DbOperationKind.QUERY, ReadConsistency.EVENTUAL, readerEligible = true)) {
            assertIs<DbExecutionContext>(DbContextHolder.current())
        }
        assertEquals(before, DbContextHolder.current())
    }

    @Test
    fun `nested context restores the outer execution context`() {
        val outer = DbExecutionContext("expense.outer", DbOperationKind.QUERY, ReadConsistency.EVENTUAL, readerEligible = true)
        val inner = DbExecutionContext("expense.inner", DbOperationKind.COMMAND)

        DbContextHolder.withContext(outer) {
            DbContextHolder.withContext(inner) {
                assertEquals(inner, DbContextHolder.current())
            }
            assertEquals(outer, DbContextHolder.current())
        }
    }

    @Test
    fun `context is restored when scoped execution throws`() {
        val before = DbContextHolder.current()

        assertFailsWith<IllegalStateException> {
            DbContextHolder.withContext(DbExecutionContext("expense.failure", DbOperationKind.COMMAND)) {
                error("simulated JDBC failure")
            }
        }

        assertEquals(before, DbContextHolder.current())
    }

    @Test
    fun `context inherits the causal watermark when not explicitly set`() {
        DbCausalContext.withRequiredWatermark("0/10") {
            DbContextHolder.withContext(
                DbExecutionContext("expense.search", DbOperationKind.QUERY, ReadConsistency.EVENTUAL, readerEligible = true)
            ) {
                assertEquals("0/10", DbContextHolder.current().requiredWatermark)
            }
        }
    }

    @Test
    fun `explicit context watermark takes precedence over request watermark`() {
        DbCausalContext.withRequiredWatermark("0/10") {
            DbContextHolder.withContext(
                DbExecutionContext(
                    "expense.search",
                    DbOperationKind.QUERY,
                    ReadConsistency.EVENTUAL,
                    readerEligible = true,
                    requiredWatermark = "0/20",
                )
            ) {
                assertEquals("0/20", DbContextHolder.current().requiredWatermark)
            }
        }
    }
    @Test
    fun `eventual query without reader eligibility remains writer-only`() {
        assertEquals(
            true,
            DbExecutionContext("expense.search", DbOperationKind.QUERY, ReadConsistency.EVENTUAL)
                .isWriterOnly()
        )
        assertEquals(
            true,
            DbExecutionContext("expense.search", DbOperationKind.QUERY, ReadConsistency.STRONG, readerEligible = true)
                .isWriterOnly()
        )
    }
}
