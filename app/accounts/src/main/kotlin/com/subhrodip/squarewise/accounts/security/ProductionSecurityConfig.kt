@file:Suppress("CanConvertToMultiDollarString")

package com.subhrodip.squarewise.accounts.security

import com.subhrodip.squarewise.accounts.auth.jwks.RsaKeyProvider
import com.subhrodip.squarewise.security.OidcJwtDecoderFactory
import com.subhrodip.squarewise.security.OidcSecurityConstants
import com.subhrodip.squarewise.security.HttpHeadersConfiguration
import com.subhrodip.squarewise.security.errors.ServletProblemAccessDeniedHandler
import com.subhrodip.squarewise.security.errors.ServletProblemAuthenticationEntryPoint
import com.subhrodip.squarewise.ids.contracts.ApiEndpoints
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import org.springframework.core.env.Environment
import org.springframework.core.env.Profiles
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.security.web.SecurityFilterChain

/** Production/staging JWT security chain for Accounts. */
@Configuration
@Profile("production", "staging", "local-oidc")
@EnableWebSecurity
class ProductionSecurityConfig(
    @Value("\${spring.security.oauth2.resourceserver.jwt.issuer-uri}") private val issuerUri: String,
    @Value("\${squarewise.security.oidc.audience}") private val audience: String,
    @Value("\${squarewise.security.oidc.allowed-algorithms:}") private val allowedAlgorithms: String,
    @Value("\${squarewise.security.oidc.external-validation-enabled:false}") private val externalValidationEnabled: Boolean,
    private val environment: Environment,
    private val rsaKeyProvider: RsaKeyProvider
) {
    @Bean
    fun jwtDecoder(): JwtDecoder {
        val localDecoder = OidcJwtDecoderFactory.createWithJwkSource(
            rsaKeyProvider.jwkSource(),
            issuerUri,
            audience,
            OidcSecurityConstants.configuredSigningAlgorithms(allowedAlgorithms)
        )
        if (!externalValidationEnabled || !environment.acceptsProfiles(Profiles.of("local-oidc"))) {
            return localDecoder
        }
        val externalDecoder = OidcJwtDecoderFactory.create(
            issuerUri,
            audience,
            OidcSecurityConstants.configuredSigningAlgorithms(allowedAlgorithms)
        )
        return FallbackJwtDecoder(listOf(externalDecoder, localDecoder))
    }

    @Bean
    fun securityFilterChain(http: HttpSecurity): SecurityFilterChain = http
        .let { HttpHeadersConfiguration.applyServletSecurityHeaders(it) }
        .csrf { it.disable() }
        .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
        .authorizeHttpRequests {
            it.requestMatchers(ApiEndpoints.Operations.HEALTH).permitAll()
                .requestMatchers(
                    ApiEndpoints.Accounts.V1.PATH_LOGIN_START,
                    ApiEndpoints.Accounts.V1.PATH_LOGIN_VERIFY
                ).permitAll()
                .requestMatchers(ApiEndpoints.Accounts.V1.PATH_TOKEN_REFRESH).permitAll()
                .requestMatchers(
                    ApiEndpoints.Accounts.V1.PATH_JWKS,
                    ApiEndpoints.Accounts.V1.WELL_KNOWN_JWKS,
                    ApiEndpoints.Accounts.V1.WELL_KNOWN_OPENID_CONFIGURATION,
                    "/.well-known/**"
                ).permitAll()
                .anyRequest().authenticated()
        }
        .exceptionHandling {
            it.authenticationEntryPoint(ServletProblemAuthenticationEntryPoint())
                .accessDeniedHandler(ServletProblemAccessDeniedHandler())
        }
        .oauth2ResourceServer {
            it.authenticationEntryPoint(ServletProblemAuthenticationEntryPoint()).jwt {}
        }
        .build()
}
