package com.subhrodip.squarewise.notifications.email

import com.subhrodip.squarewise.notifications.email.delivery.opaqueRecipientId
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test

class LogSafeRecipientTest {
    @Test
    fun `opaque recipient id is stable and contains no email text`() {
        val first = opaqueRecipientId("Alice@example.com")
        assertEquals(first, opaqueRecipientId(" alice@EXAMPLE.com "))
        assertFalse(first.contains("alice"))
        assertFalse(first.contains("example.com"))
    }
}
