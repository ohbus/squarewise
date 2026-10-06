package com.subhrodip.squarewise.errors.code

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.assertEquals

/** Verifies the frozen metadata digit and vocabulary mappings. */
class ErrorMetadataTest {
    @Test
    fun `maps registered domains and layers`() {
        assertEquals(1, ErrorDomain.ACCOUNTS.digit)
        assertEquals(2, ErrorDomain.EXPENSE_CORE.digit)
        assertEquals(9, ErrorDomain.PLATFORM.digit)
        assertEquals(1, ErrorLayer.INTERFACE.digit)
        assertEquals(9, ErrorLayer.SHARED_RUNTIME.digit)
        assertEquals(1, ErrorCategory.VALIDATION.digit)
        assertEquals(9, ErrorCategory.INTERNAL.digit)
    }

    @Test
    fun `exposes governed metadata vocabulary`() {
        assertEquals(RetryPolicy.RETRY_AFTER, RetryPolicy.valueOf("RETRY_AFTER"))
        assertEquals(ErrorSeverity.CRITICAL, ErrorSeverity.valueOf("CRITICAL"))
        assertEquals(DisclosurePolicy.RESOURCE_HIDDEN_WHEN_UNAUTHORIZED, DisclosurePolicy.valueOf("RESOURCE_HIDDEN_WHEN_UNAUTHORIZED"))
        assertEquals(TransportChannel.MESSAGING, TransportChannel.valueOf("MESSAGING"))
    }
}
