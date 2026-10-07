package com.subhrodip.squarewise.security

import com.subhrodip.squarewise.errors.catalog.PlatformErrors
import com.subhrodip.squarewise.security.errors.PlatformDomainException

/** Validates shared OIDC trust-boundary configuration with a governed platform failure. */
internal object OidcConfigurationValidator {
    /**
     * Rejects incomplete issuer or audience configuration.
     *
     * @param issuerUri configured issuer URI.
     * @param audience configured resource-server audience.
     * @throws PlatformDomainException when either value is blank.
     */
    fun validate(issuerUri: String, audience: String) {
        if (issuerUri.isBlank()) {
            throw PlatformDomainException(
                PlatformErrors.PLATFORM_CONFIGURATION_INVALID,
                OidcSecurityConstants.ISSUER_REQUIRED_MESSAGE
            )
        }
        if (audience.isBlank()) {
            throw PlatformDomainException(
                PlatformErrors.PLATFORM_CONFIGURATION_INVALID,
                OidcSecurityConstants.AUDIENCE_REQUIRED_MESSAGE
            )
        }
    }
}
