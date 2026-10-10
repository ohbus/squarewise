package com.subhrodip.squarewise.accounts.auth.provider

import java.util.UUID
import com.subhrodip.squarewise.security.errors.PlatformDomainException
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/** Verifies fail-closed construction and explicit unsupported delegation behavior. */
class ExternalOidcTokenProviderTest {
    @Test
    fun `rejects incomplete external provider configuration`() {
        listOf(
            Triple("", "client", "audience"),
            Triple("https://issuer.example", "", "audience"),
            Triple("https://issuer.example", "client", ""),
        ).forEach { (issuer, clientId, audience) ->
            assertThrows(IllegalArgumentException::class.java) {
                ExternalOidcTokenProvider(issuer, clientId, audience)
            }
        }
    }

    @Test
    fun `does not silently mint when external delegation is unavailable`() {
        val provider = ExternalOidcTokenProvider(
            externalIssuerUri = "https://issuer.example",
            clientId = "client",
            audience = "audience",
        )

        val exception = assertThrows(PlatformDomainException::class.java) {
            provider.issueAccessToken(UUID.randomUUID(), "subject", "user@example.com")
        }
        assertEquals("PLATFORM_CONFIGURATION_INVALID", exception.definition.errorName)
    }
}
