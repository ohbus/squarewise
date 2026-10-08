package com.subhrodip.squarewise.expensecore.errors

import com.subhrodip.squarewise.errors.catalog.ExpenseErrors
import com.subhrodip.squarewise.errors.diagnostics.ErrorDiagnostics
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test

class ExpenseExceptionsTest {

    @Test
    fun `ExpenseInputException constructor variants`() {
        val ex1 = ExpenseInputException("bad input")
        assertEquals("bad input", ex1.message)
        assertEquals(null, ex1.cause)

        val cause = RuntimeException("root cause")
        val ex2 = ExpenseInputException("bad input with cause", cause)
        assertEquals("bad input with cause", ex2.message)
        assertSame(cause, ex2.cause)
    }

    @Test
    fun `ExpenseDomainException constructor variants`() {
        val exDefault = ExpenseDomainException(ExpenseErrors.GROUP_NOT_FOUND)
        assertSame(ExpenseErrors.GROUP_NOT_FOUND, exDefault.definition)
        assertEquals(ExpenseErrors.GROUP_NOT_FOUND.errorName, exDefault.message)
        assertEquals(null, exDefault.cause)
        assertEquals(ErrorDiagnostics.EMPTY, exDefault.diagnostics)

        val cause = RuntimeException("boom")
        val diag = object : ErrorDiagnostics {
            override val entries: Map<String, String> = mapOf("group" to "123")
        }
        val exFull = ExpenseDomainException(ExpenseErrors.GROUP_NOT_FOUND, "msg", cause, diag)
        assertEquals("msg", exFull.message)
        assertSame(cause, exFull.cause)
        assertSame(diag, exFull.diagnostics)
    }
}
