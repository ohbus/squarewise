package com.subhrodip.squarewise.errors.code

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows

/** Verifies six-digit identity validation and direct decomposition. */
class ErrorCodeTest {
    @Test
    fun `decomposes canonical code without changing machine identity`() {
        val code = ErrorCode("213201")
        val nonZeroTensCode = ErrorCode("213210")

        assertEquals("213201", code.value)
        assertEquals(2, code.domainDigit)
        assertEquals(1, code.moduleDigit)
        assertEquals(3, code.layerDigit)
        assertEquals(2, code.categoryDigit)
        assertEquals(1, code.sequence)
        assertEquals("21-3-2-01", code.displayCode)
        assertEquals(10, nonZeroTensCode.sequence)
    }

    @Test
    fun `rejects malformed length and namespace digits`() {
        assertThrows(IllegalArgumentException::class.java) { ErrorCode("21320") }
        assertThrows(IllegalArgumentException::class.java) { ErrorCode("013201") }
        assertThrows(IllegalArgumentException::class.java) { ErrorCode("201201") }
        assertThrows(IllegalArgumentException::class.java) { ErrorCode("210201") }
        assertThrows(IllegalArgumentException::class.java) { ErrorCode("213001") }
        assertThrows(IllegalArgumentException::class.java) { ErrorCode("21A201") }
    }

    @Test
    fun `rejects zero sequence and non-digit sequence`() {
        assertThrows(IllegalArgumentException::class.java) { ErrorCode("213200") }
        assertThrows(IllegalArgumentException::class.java) { ErrorCode("2132A1") }
        assertThrows(IllegalArgumentException::class.java) { ErrorCode("21321A") }
    }
}
