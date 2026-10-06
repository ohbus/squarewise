package com.subhrodip.squarewise.accounts.auth.session

import java.time.Duration
import kotlin.test.Test
import kotlin.test.assertEquals

/** Verifies the documented defaults used when session policy properties are not configured. */
class SessionPolicyPropertiesTest {
    @Test
    fun `defaults provide the bounded session policy`() {
        val properties = SessionPolicyProperties()

        assertEquals(Duration.ofMinutes(10), properties.accessTokenLifetime)
        assertEquals(Duration.ofDays(30), properties.refreshIdleLifetime)
        assertEquals(Duration.ofDays(90), properties.absoluteSessionLifetime)
        assertEquals(Duration.ofSeconds(30), properties.clockSkew)
    }

    @Test
    fun `configured values remain independently bindable`() {
        val properties = SessionPolicyProperties(
            accessTokenLifetime = Duration.ofMinutes(5),
            refreshIdleLifetime = Duration.ofDays(7),
            absoluteSessionLifetime = Duration.ofDays(30),
            clockSkew = Duration.ofSeconds(15)
        )

        assertEquals(Duration.ofMinutes(5), properties.accessTokenLifetime)
        assertEquals(Duration.ofDays(7), properties.refreshIdleLifetime)
        assertEquals(Duration.ofDays(30), properties.absoluteSessionLifetime)
        assertEquals(Duration.ofSeconds(15), properties.clockSkew)
    }

    @Test
    fun `mutable configuration binding updates every timing property`() {
        val properties = SessionPolicyProperties()

        properties.accessTokenLifetime = Duration.ofMinutes(20)
        properties.refreshIdleLifetime = Duration.ofDays(14)
        properties.absoluteSessionLifetime = Duration.ofDays(180)
        properties.clockSkew = Duration.ofMinutes(2)

        assertEquals(Duration.ofMinutes(20), properties.accessTokenLifetime)
        assertEquals(Duration.ofDays(14), properties.refreshIdleLifetime)
        assertEquals(Duration.ofDays(180), properties.absoluteSessionLifetime)
        assertEquals(Duration.ofMinutes(2), properties.clockSkew)
    }
}
