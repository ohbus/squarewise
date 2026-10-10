package com.subhrodip.squarewise.accounts.auth.config

import com.subhrodip.squarewise.security.errors.PlatformDomainException
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.BeanCreationException
import org.springframework.context.annotation.AnnotationConfigApplicationContext
import org.springframework.core.env.MapPropertySource

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

/** Verifies deployed profiles fail context refresh when unsupported external exchange is enabled. */
class ExternalOidcStartupFailureTest {
    @Test
    fun `external oidc provider prevents context refresh`() {
        val context = AnnotationConfigApplicationContext()
        context.environment.setActiveProfiles("local-oidc")
        context.environment.propertySources.addFirst(
            MapPropertySource(
                "test-properties",
                mapOf(
                    "spring.security.oauth2.resourceserver.jwt.issuer-uri" to "https://issuer.example",
                    "squarewise.security.oidc.audience" to "squarewise-api",
                    "squarewise.security.oidc.external-provider.enabled" to "true"
                )
            )
        )
        context.register(AuthSessionConfiguration::class.java)

        try {
            assertThrows(BeanCreationException::class.java) {
                context.refresh()
            }
        } finally {
            context.close()
        }
    }
}
