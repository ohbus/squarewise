@file:Suppress("CanConvertToMultiDollarString")

package com.subhrodip.squarewise.security

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component
import com.subhrodip.squarewise.security.errors.PlatformDomainException
import com.subhrodip.squarewise.errors.catalog.PlatformErrors

/**
 * Fails a production-like application startup when its provider-neutral OIDC
 * resource-server settings are incomplete.
 *
 * The guard deliberately validates only deployment configuration. JWT
 * signature, issuer, audience, expiry, and algorithm validation remains the
 * responsibility of the Spring Security decoder configured by the application.
 * Local passthrough authentication is not covered by this component and is
 * available only under the explicit local profile.
 */
@Component
@Profile("production", "staging", "local-oidc")
class OidcConfigurationGuard(
    @Value("\${spring.security.oauth2.resourceserver.jwt.issuer-uri:}") private val issuerUri: String,
    @Value("\${squarewise.security.oidc.audience:}") private val audience: String,
    @Value("\${spring.profiles.active:}") private val activeProfiles: String
) {
    init {
        OidcConfigurationValidator.validate(issuerUri, audience)
        if (!issuerUri.startsWith("https://") && !activeProfiles.split(',').contains("local-oidc")) {
            throw PlatformDomainException(
                PlatformErrors.PLATFORM_CONFIGURATION_INVALID,
                OidcSecurityConstants.DEPLOYMENT_ISSUER_HTTPS_MESSAGE
            )
        }
    }
}
