package com.subhrodip.squarewise.security

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component
import com.subhrodip.squarewise.errors.catalog.PlatformErrors
import com.subhrodip.squarewise.security.errors.PlatformDomainException

/**
 * Fails application startup in production and staging environments if well-known,
 * insecure, or predictable CI fixture keys are detected in the configuration.
 *
 * This provides fail-closed assurance against accidental credential leakage
 * or misconfiguration in deployment profiles.
 */
@Component
@Profile("production", "staging")
class CryptographicSecretSanityGuard(
    @Value("\${squarewise.security.credential-digest-secret:}") private val credentialDigestSecret: String,
    @Value("\${squarewise.security.auth-email-envelope-key:}") private val authEmailEnvelopeKey: String
) {
    init {
        checkSecret("credential-digest-secret", credentialDigestSecret)
        checkSecret("auth-email-envelope-key", authEmailEnvelopeKey)
    }

    private fun checkSecret(secretName: String, secretValue: String) {
        if (secretValue.isBlank()) {
            return
        }
        val normalized = secretValue.trim()
        if (PREDICTABLE_SECRETS_BLACKLIST.contains(normalized)) {
            throw PlatformDomainException(
                PlatformErrors.PLATFORM_CONFIGURATION_INVALID,
                "Predictable deployment secret detected for $secretName"
            )
        }
    }

    companion object {
        /** Known predictable CI/test keys derived from sequential or repeating byte patterns. */
        val PREDICTABLE_SECRETS_BLACKLIST: Set<String> = setOf(
            "AAECAwQFBgcICQoLDA0ODxAREhMUFRYXGBkaGxwdHh8=", // bytes 0x00..0x1F
            "ICEiIyQlJicoKSorLC0uLzAxMjM0NTY3ODk6Ozw9Pj8=", // bytes 0x20..0x3F
            "0000000000000000000000000000000000000000000=",
            "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA="
        )
    }
}
