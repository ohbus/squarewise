package com.subhrodip.squarewise.accounts.profile

import com.subhrodip.squarewise.accounts.profile.service.ProfileRules

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import com.subhrodip.squarewise.errors.exceptions.SquarewiseException

class ProfileRulesTest {
    @Test
    fun `accepts bounded subject and IANA timezone`() {
        assertEquals("oidc|alice", ProfileRules.requireSubject("oidc|alice"))
        assertEquals("Europe/Vienna", ProfileRules.requireTimezone("Europe/Vienna"))
    }

    @Test
    fun `rejects invalid subject and timezone with defined statuses`() {
        val ex = assertThrows(SquarewiseException::class.java) {
            ProfileRules.requireSubject("alice with spaces")
        }
        assertEquals("UNAUTHENTICATED", ex.definition.legacyCode)
        val ex2 = assertThrows(SquarewiseException::class.java) {
            ProfileRules.requireTimezone("not/a-zone")
        }
        assertEquals("VALIDATION_FAILED", ex2.definition.legacyCode)
    }
}
