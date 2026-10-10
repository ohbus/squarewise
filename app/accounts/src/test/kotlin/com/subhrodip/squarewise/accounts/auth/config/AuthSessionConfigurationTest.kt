package com.subhrodip.squarewise.accounts.auth.config

import com.subhrodip.squarewise.security.errors.PlatformDomainException
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

/** Verifies Accounts exposes the explicit external OIDC provider adapter when configured. */
class AuthSessionConfigurationTest {
    /** Verifies unsupported external OIDC exchange fails during configuration rather than at login. */
    @Test
    fun `rejects external oidc provider until exchange is implemented`() {
        val configuration = AuthSessionConfiguration(
            issuerUri = "https://issuer.example",
            audience = "squarewise-api"
        )

        assertThrows(PlatformDomainException::class.java) { configuration.externalIdentityProviderPort() }
    }
}
