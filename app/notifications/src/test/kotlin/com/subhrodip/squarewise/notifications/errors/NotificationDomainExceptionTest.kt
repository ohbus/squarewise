package com.subhrodip.squarewise.notifications.errors

import com.subhrodip.squarewise.errors.catalog.NotificationErrors
import com.subhrodip.squarewise.errors.diagnostics.ErrorDiagnostics
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test

/** Verifies NotificationDomainException constructor variants and default arguments. */
class NotificationDomainExceptionTest {

    @Test
    fun `default arguments and custom parameters are properly preserved`() {
        val def = NotificationErrors.EMAIL_DISPATCH_FAILED
        val defaultEx = NotificationDomainException(def)
        assertEquals(def.errorName, defaultEx.message)
        assertEquals(null, defaultEx.cause)
        assertSame(ErrorDiagnostics.EMPTY, defaultEx.diagnostics)

        val customMsg = "custom failure"
        val cause = RuntimeException("root cause")
        val diagnostics = object : ErrorDiagnostics {
            override val entries: Map<String, String> = mapOf("key" to "value")
        }

        val fullEx = NotificationDomainException(def, customMsg, cause, diagnostics)
        assertEquals(customMsg, fullEx.message)
        assertSame(cause, fullEx.cause)
        assertSame(diagnostics, fullEx.diagnostics)
    }
}
