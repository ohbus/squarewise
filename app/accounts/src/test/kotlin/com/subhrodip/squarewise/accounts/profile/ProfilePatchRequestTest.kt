package com.subhrodip.squarewise.accounts.profile

import com.subhrodip.squarewise.accounts.profile.api.ProfilePatchRequest
import com.subhrodip.squarewise.errors.exceptions.SquarewiseException
import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

/** Verifies PATCH presence semantics independently of controller and persistence tests. */
class ProfilePatchRequestTest {
    @Test
    fun `empty patch is rejected with validation error`() {
        val error = assertThrows(SquarewiseException::class.java) {
            ProfilePatchRequest().validateNotEmpty()
        }

        assertEquals("PROFILE_REQUEST_INVALID", error.definition.errorName)
    }

    @Test
    fun `each individual profile field makes a patch actionable`() {
        listOf(
            ProfilePatchRequest(displayName = "Alice"),
            ProfilePatchRequest(timezone = "UTC"),
            ProfilePatchRequest(defaultCurrency = "USD"),
        ).forEach { patch ->
            assertDoesNotThrow { patch.validateNotEmpty() }
        }
    }
}
