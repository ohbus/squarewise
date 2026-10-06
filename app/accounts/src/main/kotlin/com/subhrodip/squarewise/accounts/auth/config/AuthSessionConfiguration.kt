@file:Suppress("CanConvertToMultiDollarString")

package com.subhrodip.squarewise.accounts.auth.config

import com.subhrodip.squarewise.accounts.auth.credential.CredentialDigest
import com.subhrodip.squarewise.accounts.auth.credential.LoginCredentialService
import com.subhrodip.squarewise.accounts.auth.abuse.LoginVerificationRateLimitService
import com.subhrodip.squarewise.accounts.auth.identity.AccountIdentityStore
import com.subhrodip.squarewise.accounts.auth.login.LoginVerificationService
import com.subhrodip.squarewise.accounts.auth.jwks.DefaultRsaKeyProvider
import com.subhrodip.squarewise.accounts.auth.jwks.RsaKeyProperties
import com.subhrodip.squarewise.accounts.auth.jwks.RsaKeyProvider
import com.subhrodip.squarewise.accounts.auth.provider.AsymmetricJwtTokenProvider
import com.subhrodip.squarewise.accounts.auth.provider.ExternalOidcTokenProvider
import com.subhrodip.squarewise.accounts.auth.provider.IdentityProviderPort
import com.subhrodip.squarewise.accounts.auth.session.AuthSessionRepository
import com.subhrodip.squarewise.accounts.auth.session.SessionPolicy
import com.subhrodip.squarewise.accounts.auth.session.SessionPolicyProperties
import com.subhrodip.squarewise.accounts.auth.session.TokenSessionService
import com.subhrodip.squarewise.accounts.profile.persistence.ProfileStore
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import org.springframework.boot.context.properties.EnableConfigurationProperties

/**
 * Spring configuration wiring token minting, session management, and login verification.
 */
@Configuration
@EnableConfigurationProperties(SessionPolicyProperties::class, RsaKeyProperties::class)
class AuthSessionConfiguration(
    @Value("\${spring.security.oauth2.resourceserver.jwt.issuer-uri}")
    private val issuerUri: String,
    @Value("\${squarewise.security.oidc.audience}")
    private val audience: String
) {
    /** Creates the single validated timing policy used by Accounts sessions. */
    @Bean
    fun sessionPolicy(properties: SessionPolicyProperties): SessionPolicy = SessionPolicy(
        accessTokenLifetime = properties.accessTokenLifetime,
        refreshIdleLifetime = properties.refreshIdleLifetime,
        absoluteSessionLifetime = properties.absoluteSessionLifetime,
        clockSkew = properties.clockSkew
    )

    /** Provides the active and historical RSA cryptographic key pairs. */
    @Bean
    fun rsaKeyProvider(properties: RsaKeyProperties): RsaKeyProvider =
        DefaultRsaKeyProvider(properties)

    /**
     * Registers the configured external OIDC adapter for provider-backed profiles
     * when external delegation is explicitly enabled.
     */
    @Bean
    @Profile("production", "staging", "local-oidc")
    @ConditionalOnProperty(name = ["squarewise.security.oidc.external-provider.enabled"], havingValue = "true")
    fun externalIdentityProviderPort(): IdentityProviderPort = ExternalOidcTokenProvider(
        externalIssuerUri = issuerUri,
        clientId = audience,
        audience = audience
    )

    /**
     * Primary operational asymmetric token signing authority for production, staging,
     * and local-oidc profiles (closing SEC-001 and SEC-009).
     */
    @Bean
    @Profile("production", "staging", "local-oidc")
    @ConditionalOnMissingBean(IdentityProviderPort::class)
    fun asymmetricIdentityProviderPort(
        rsaKeyProvider: RsaKeyProvider,
        sessionPolicy: SessionPolicy
    ): IdentityProviderPort = AsymmetricJwtTokenProvider(
        rsaKeyProvider = rsaKeyProvider,
        issuerUri = issuerUri,
        audience = audience,
        tokenLifetime = sessionPolicy.accessTokenLifetime
    )

    /**
     * Creates the TokenSessionService managing refresh tokens and families.
     */
    @Bean
    fun tokenSessionService(
        sessionRepository: AuthSessionRepository,
        identityProviderPort: IdentityProviderPort,
        credentialDigest: CredentialDigest,
        sessionPolicy: SessionPolicy,
        accountIdentityStore: AccountIdentityStore
    ): TokenSessionService =
        TokenSessionService(
            sessionRepository = sessionRepository,
            identityProviderPort = identityProviderPort,
            credentialDigest = credentialDigest,
            sessionPolicy = sessionPolicy,
            accountIdentityStore = accountIdentityStore
        )

    /**
     * Creates the LoginVerificationService coordinating redemption and session creation.
     */
    @Bean
    fun loginVerificationService(
        credentialService: LoginCredentialService,
        profileStore: ProfileStore,
        tokenSessionService: TokenSessionService,
        accountIdentityStore: AccountIdentityStore,
        loginVerificationRateLimitService: LoginVerificationRateLimitService
    ): LoginVerificationService =
        LoginVerificationService(
            credentialService = credentialService,
            profileStore = profileStore,
            tokenSessionService = tokenSessionService,
            accountIdentityStore = accountIdentityStore,
            loginVerificationRateLimitService = loginVerificationRateLimitService,
            issuerUri = issuerUri
        )

}
