package com.subhrodip.squarewise.errors.exceptions

import com.subhrodip.squarewise.errors.catalog.AccountsErrors
import com.subhrodip.squarewise.errors.catalog.ExpenseErrors
import com.subhrodip.squarewise.errors.catalog.SimpleErrorDefinition
import com.subhrodip.squarewise.errors.code.DisclosurePolicy
import com.subhrodip.squarewise.errors.code.ErrorCode
import com.subhrodip.squarewise.errors.code.ErrorSeverity
import com.subhrodip.squarewise.errors.code.RetryPolicy
import com.subhrodip.squarewise.errors.diagnostics.MapDiagnostics
import com.subhrodip.squarewise.errors.diagnostics.ResourceIdentifier
import com.subhrodip.squarewise.errors.diagnostics.ErrorDiagnostics
import com.subhrodip.squarewise.errors.code.ErrorDefinition
import com.subhrodip.squarewise.errors.http.FieldViolation
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

/** Verifies governed exception shielding and diagnostic bounds. */
class SquarewiseExceptionTest {
    @Test
    fun `preserves catalog definition and cause while message exposes only identity`() {
        val cause = IllegalStateException("database password=do-not-return")
        val exception = ConcurrencyConflictException(cause = cause)
        val defaultException = ConcurrencyConflictException()

        assertEquals(ExpenseErrors.GROUP_NAME_CONFLICT.errorName, exception.definition.errorName)
        assertEquals(cause, exception.cause)
        assertEquals("GROUP_NAME_CONFLICT", exception.message)
        assertEquals("GROUP_NAME_CONFLICT", defaultException.definition.errorName)
    }

    @Test
    fun `leaf exceptions produce governed definitions and bounded diagnostics`() {
        val validation = DomainValidationException(listOf(FieldViolation("name", "required")))
        val notFound = EntityNotFoundException(ResourceIdentifier("group"))

        assertEquals("PROFILE_REQUEST_INVALID", validation.definition.errorName)
        assertEquals("GROUP_NOT_FOUND", notFound.definition.errorName)
        assertEquals("group", notFound.diagnostics.entries["resource.type"])
    }

    @Test
    fun `rejects uncatalogued definitions`() {
        val uncatalogued = SimpleErrorDefinition(
            numericCode = ErrorCode("999901"),
            errorName = "UNCATALOGUED_ERROR",
            legacyCode = null,
            title = "Uncatalogued",
            safeDetail = "Uncatalogued",
            messageKey = "error.uncatalogued",
            httpStatus = 500,
            graphqlClassification = null,
            retryPolicy = RetryPolicy.NEVER,
            severity = ErrorSeverity.ERROR,
            disclosure = DisclosurePolicy.INTERNAL_REDACTED,
        )

        assertThrows(IllegalArgumentException::class.java) { TestException(uncatalogued) }
    }

    @Test
    fun `diagnostics reject sensitive and oversized entries`() {
        assertThrows(IllegalArgumentException::class.java) { MapDiagnostics.of(mapOf("access_token" to "secret")) }
        assertThrows(IllegalArgumentException::class.java) { MapDiagnostics.of(mapOf("" to "value")) }
        assertThrows(IllegalArgumentException::class.java) { MapDiagnostics.of(mapOf("k".repeat(65) to "value")) }
        assertThrows(IllegalArgumentException::class.java) { MapDiagnostics.of((1..11).associate { "key$it" to "value" }) }
        assertThrows(IllegalArgumentException::class.java) { MapDiagnostics.of(mapOf("key" to "x".repeat(257))) }
        assertThrows(IllegalArgumentException::class.java) { ResourceIdentifier("") }
        assertThrows(IllegalArgumentException::class.java) { ResourceIdentifier("x".repeat(129)) }
    }

    @Test
    fun `diagnostics retain immutable bounded entries`() {
        val diagnostics = MapDiagnostics.of(mapOf("operation" to "update"))

        assertEquals(mapOf("operation" to "update"), diagnostics.entries)
        assertFalse(diagnostics.entries === mapOf("operation" to "update"))
        assertEquals(0, ErrorDiagnostics.EMPTY.entries.size)
    }

    private class TestException(definition: ErrorDefinition) :
        SquarewiseException(definition)
}
