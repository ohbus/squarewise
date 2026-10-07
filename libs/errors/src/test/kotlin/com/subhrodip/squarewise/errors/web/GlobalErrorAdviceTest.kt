package com.subhrodip.squarewise.errors.web

import com.subhrodip.squarewise.errors.catalog.ExpenseErrors
import com.subhrodip.squarewise.errors.exceptions.ConcurrencyConflictException
import com.subhrodip.squarewise.errors.request.RequestIdContext
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** Verifies additive problem mapping and containment of cause text. */
class GlobalErrorAdviceTest {
    @Test
    fun `governed failure emits additive identity without cause text`() {
        val advice = GlobalErrorAdvice("accounts")

        RequestIdContext.with("request-123") {
            val response = advice.governed(ConcurrencyConflictException(cause = IllegalStateException("select password from users")))
            val body = response.body!!

            assertEquals(409, body.status)
            assertEquals("CONFLICT", body.code)
            assertEquals(ExpenseErrors.GROUP_NAME_CONFLICT.numericCode.value, body.numericCode)
            assertEquals("GROUP_NAME_CONFLICT", body.errorName)
            assertFalse(body.detail.contains("password"))
            assertEquals("request-123", body.requestId)
        }
    }

    @Test
    fun `unexpected failure returns static safe internal problem`() {
        val advice = GlobalErrorAdvice("expense-core")

        val body = advice.unexpected(IllegalStateException("jdbc password=secret" )).body!!

        assertEquals(500, body.status)
        assertEquals("INTERNAL_ERROR", body.code)
        assertEquals("UNEXPECTED_INTERNAL_ERROR", body.errorName)
        assertTrue(body.detail.isNotBlank())
        assertFalse(body.detail.contains("jdbc"))
    }
}
