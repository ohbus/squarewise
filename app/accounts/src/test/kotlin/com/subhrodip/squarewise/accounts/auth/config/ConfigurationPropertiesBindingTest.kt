package com.subhrodip.squarewise.accounts.auth.config

import com.subhrodip.squarewise.accounts.auth.abuse.TrustedProxyProperties
import com.subhrodip.squarewise.accounts.auth.delivery.config.AuthEmailOutboxProperties
import com.subhrodip.squarewise.accounts.auth.jwks.RsaKeyProperties
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Verifies mutable configuration values used by Spring property binding. */
class ConfigurationPropertiesBindingTest {
    @Test
    fun `RSA and trusted-proxy values remain bindable`() {
        val rsa = RsaKeyProperties()
        rsa.keyId = "rotated-key"
        rsa.privateKeyPem = "private-pem"
        rsa.publicKeyPem = "public-pem"

        val proxies = TrustedProxyProperties()
        proxies.addresses = listOf("10.0.0.1", "::1/128")

        assertEquals("rotated-key", rsa.keyId)
        assertEquals("private-pem", rsa.privateKeyPem)
        assertEquals("public-pem", rsa.publicKeyPem)
        assertEquals(listOf("10.0.0.1", "::1/128"), proxies.addresses)
    }

    @Test
    fun `auth email outbox values remain bindable`() {
        val properties = AuthEmailOutboxProperties()
        properties.enabled = true
        properties.exchange = "auth.exchange"
        properties.routingKey = "auth.login"
        properties.pollDelayMs = 2_000
        properties.leaseSeconds = 45
        properties.retryAfterSeconds = 8
        properties.maximumAttempts = 7

        assertTrue(properties.enabled)
        assertEquals("auth.exchange", properties.exchange)
        assertEquals("auth.login", properties.routingKey)
        assertEquals(2_000, properties.pollDelayMs)
        assertEquals(45, properties.leaseSeconds)
        assertEquals(8, properties.retryAfterSeconds)
        assertEquals(7, properties.maximumAttempts)
    }
}
