package com.subhrodip.squarewise.security

import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import com.subhrodip.squarewise.security.errors.PlatformDomainException

/** Verifies the production-like OIDC configuration fail-closed boundary. */
class OidcConfigurationGuardTest {
    @Test
    fun `accepts secure issuer and audience`() {
        assertDoesNotThrow {
            OidcConfigurationGuard("https://keycloak.example/realms/squarewise", "squarewise-api", "production")
        }
    }

    @Test
    fun `rejects missing issuer`() {
        assertThrows(PlatformDomainException::class.java) {
            OidcConfigurationGuard("", "squarewise-api", "production")
        }
    }

    @Test
    fun `rejects missing audience`() {
        assertThrows(PlatformDomainException::class.java) {
            OidcConfigurationGuard("https://keycloak.example/realms/squarewise", "", "production")
        }
    }

    @Test
    fun `rejects non-https issuer`() {
        assertThrows(PlatformDomainException::class.java) {
            OidcConfigurationGuard("http://keycloak.example/realms/squarewise", "squarewise-api", "production")
        }
    }

    /** Local OIDC is the only profile allowed to use an HTTP issuer for local infrastructure. */
    @Test
    fun `accepts http issuer only for local oidc profile`() {
        assertDoesNotThrow {
            OidcConfigurationGuard("http://keycloak:8080/realms/squarewise", "squarewise-api", "local-oidc")
        }
    }
}
