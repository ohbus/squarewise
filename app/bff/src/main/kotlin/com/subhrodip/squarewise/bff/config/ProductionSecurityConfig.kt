@file:Suppress("CanConvertToMultiDollarString")

package com.subhrodip.squarewise.bff.config

import com.subhrodip.squarewise.ids.contracts.ApiEndpoints

import com.subhrodip.squarewise.security.ReactiveOidcJwtDecoderFactory
import com.subhrodip.squarewise.security.OidcSecurityConstants
import com.subhrodip.squarewise.security.HttpHeadersConfiguration
import com.subhrodip.squarewise.security.errors.ReactiveProblemAccessDeniedHandler
import com.subhrodip.squarewise.security.errors.ReactiveProblemAuthenticationEntryPoint
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import org.springframework.security.config.web.server.ServerHttpSecurity
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder
import org.springframework.security.web.server.SecurityWebFilterChain

/** Production/staging reactive JWT security chain for the GraphQL BFF. */
@Configuration
@Profile("production", "staging", "local-oidc")
class ProductionSecurityConfig(
    @Value("\${spring.security.oauth2.resourceserver.jwt.issuer-uri}") private val issuerUri: String,
    @Value("\${squarewise.security.oidc.audience}") private val audience: String,
    @Value("\${squarewise.security.oidc.allowed-algorithms:}") private val allowedAlgorithms: String
) {
    @Bean
    fun reactiveJwtDecoder(): ReactiveJwtDecoder =
        ReactiveOidcJwtDecoderFactory.create(
            issuerUri, audience, OidcSecurityConstants.configuredSigningAlgorithms(allowedAlgorithms)
        )

    @Bean
    fun securityWebFilterChain(http: ServerHttpSecurity): SecurityWebFilterChain = http
        .let { HttpHeadersConfiguration.applyReactiveSecurityHeaders(it) }
        .csrf { it.disable() }
        .authorizeExchange {
            it.pathMatchers(ApiEndpoints.Operations.HEALTH).permitAll()
                .pathMatchers(
                    ApiEndpoints.Bff.BROWSER_LOGIN_START,
                    ApiEndpoints.Bff.BROWSER_LOGIN_VERIFY,
                    ApiEndpoints.Bff.BROWSER_TOKEN_REFRESH,
                    ApiEndpoints.Bff.BROWSER_LOGOUT
                ).permitAll()
                .anyExchange().authenticated()
        }
        .exceptionHandling {
            it.authenticationEntryPoint(ReactiveProblemAuthenticationEntryPoint())
                .accessDeniedHandler(ReactiveProblemAccessDeniedHandler())
        }
        .oauth2ResourceServer {
            it.authenticationEntryPoint(ReactiveProblemAuthenticationEntryPoint()).jwt {}
        }
        .build()
}
