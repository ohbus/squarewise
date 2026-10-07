package com.subhrodip.squarewise.security

import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.security.oauth2.jwt.JwtDecoders
import org.springframework.security.oauth2.jwt.JwtClaimValidator
import org.springframework.security.oauth2.jwt.JwtValidators
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder
import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.jwk.source.JWKSource
import com.nimbusds.jose.proc.JWSVerificationKeySelector
import com.nimbusds.jose.proc.SecurityContext
import com.nimbusds.jwt.proc.DefaultJWTProcessor
import java.security.interfaces.RSAPublicKey

/** Builds a standards-based JWT decoder for the configured OIDC issuer. */
object OidcJwtDecoderFactory {
    /**
     * Creates a decoder using issuer-discovered keys and validators for issuer,
     * expiry, not-before, and configured audience.
     *
     * @param issuerUri trusted OIDC issuer URI.
     * @param audience required API audience claim.
     * @return configured JWT decoder.
     * @throws IllegalArgumentException when the issuer or audience is blank.
     */
    fun create(
        issuerUri: String,
        audience: String,
        allowedAlgorithms: Set<String> = setOf(OidcSecurityConstants.DEFAULT_SIGNING_ALGORITHM)
    ): JwtDecoder {
        OidcConfigurationValidator.validate(issuerUri, audience)

        val decoder = JwtDecoders.fromIssuerLocation(issuerUri) as NimbusJwtDecoder
        val audienceValidator = JwtClaimValidator<Collection<String>>(OidcSecurityConstants.AUDIENCE_CLAIM) { values ->
            values.contains(audience)
        }
        decoder.setJwtValidator(
            DelegatingOAuth2TokenValidator(
                JwtValidators.createDefaultWithIssuer(issuerUri),
                audienceValidator,
                OidcJwtClaimPolicy.subjectValidator(),
                OidcJwtAlgorithmPolicy(allowedAlgorithms)
            )
        )
        return decoder
    }

    /**
     * Creates a decoder using a known public key directly, applying the same
     * issuer, audience, subject, and algorithm validation policies.
     *
     * @param publicKey RSA public key used for signature verification.
     * @param issuerUri trusted OIDC issuer URI.
     * @param audience required API audience claim.
     * @param allowedAlgorithms set of permitted JWS signing algorithms.
     * @return configured JWT decoder.
     */
    fun createWithPublicKey(
        publicKey: RSAPublicKey,
        issuerUri: String,
        audience: String,
        allowedAlgorithms: Set<String> = setOf(OidcSecurityConstants.DEFAULT_SIGNING_ALGORITHM)
    ): JwtDecoder {
        OidcConfigurationValidator.validate(issuerUri, audience)

        val decoder = NimbusJwtDecoder.withPublicKey(publicKey).build()
        val audienceValidator = JwtClaimValidator<Collection<String>>(OidcSecurityConstants.AUDIENCE_CLAIM) { values ->
            values.contains(audience)
        }
        decoder.setJwtValidator(
            DelegatingOAuth2TokenValidator(
                JwtValidators.createDefaultWithIssuer(issuerUri),
                audienceValidator,
                OidcJwtClaimPolicy.subjectValidator(),
                OidcJwtAlgorithmPolicy(allowedAlgorithms)
            )
        )
        return decoder
    }

    /**
     * Creates a decoder using a JWKSource directly (e.g., in-memory or rotating key provider),
     * applying the same issuer, audience, subject, and algorithm validation policies.
     *
     * @param jwkSource Nimbus JWKSource for resolving verification keys.
     * @param issuerUri trusted OIDC issuer URI.
     * @param audience required API audience claim.
     * @param allowedAlgorithms set of permitted JWS signing algorithms.
     * @return configured JWT decoder.
     */
    fun createWithJwkSource(
        jwkSource: JWKSource<SecurityContext>,
        issuerUri: String,
        audience: String,
        allowedAlgorithms: Set<String> = setOf(OidcSecurityConstants.DEFAULT_SIGNING_ALGORITHM)
    ): JwtDecoder {
        OidcConfigurationValidator.validate(issuerUri, audience)

        val processor = DefaultJWTProcessor<SecurityContext>()
        val keySelector = JWSVerificationKeySelector(
            JWSAlgorithm.RS256,
            jwkSource
        )
        processor.jwsKeySelector = keySelector
        val decoder = NimbusJwtDecoder(processor)
        val audienceValidator = JwtClaimValidator<Collection<String>>(OidcSecurityConstants.AUDIENCE_CLAIM) { values ->
            values.contains(audience)
        }
        decoder.setJwtValidator(
            DelegatingOAuth2TokenValidator(
                JwtValidators.createDefaultWithIssuer(issuerUri),
                audienceValidator,
                OidcJwtClaimPolicy.subjectValidator(),
                OidcJwtAlgorithmPolicy(allowedAlgorithms)
            )
        )
        return decoder
    }
}
