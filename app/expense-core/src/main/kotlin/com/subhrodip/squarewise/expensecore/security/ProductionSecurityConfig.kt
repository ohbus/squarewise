@file:Suppress("CanConvertToMultiDollarString")

package com.subhrodip.squarewise.expensecore.security

import com.subhrodip.squarewise.ids.contracts.ApiEndpoints

import com.subhrodip.squarewise.security.OidcJwtDecoderFactory
import com.subhrodip.squarewise.security.OidcSecurityConstants
import com.subhrodip.squarewise.security.HttpHeadersConfiguration
import com.subhrodip.squarewise.security.errors.ServletProblemAccessDeniedHandler
import com.subhrodip.squarewise.security.errors.ServletProblemAuthenticationEntryPoint
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.security.web.SecurityFilterChain

/** Production/staging JWT security chain for Expense Core. */
@Configuration
@Profile("production", "staging", "local-oidc")
@EnableWebSecurity
class ProductionSecurityConfig(
    @Value("\${spring.security.oauth2.resourceserver.jwt.issuer-uri}") private val issuerUri: String,
    @Value("\${squarewise.security.oidc.audience}") private val audience: String,
    @Value("\${squarewise.security.oidc.allowed-algorithms:}") private val allowedAlgorithms: String
) {
    @Bean
    fun jwtDecoder(): JwtDecoder = OidcJwtDecoderFactory.create(
        issuerUri, audience, OidcSecurityConstants.configuredSigningAlgorithms(allowedAlgorithms)
    )

    @Bean
    fun securityFilterChain(http: HttpSecurity): SecurityFilterChain = http
        .let { HttpHeadersConfiguration.applyServletSecurityHeaders(it) }
        .csrf { it.disable() }
        .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
        .authorizeHttpRequests { it.requestMatchers(ApiEndpoints.Operations.HEALTH).permitAll().anyRequest().authenticated() }
        .exceptionHandling {
            it.authenticationEntryPoint(ServletProblemAuthenticationEntryPoint())
                .accessDeniedHandler(ServletProblemAccessDeniedHandler())
        }
        .oauth2ResourceServer {
            it.authenticationEntryPoint(ServletProblemAuthenticationEntryPoint()).jwt {}
        }
        .build()
}
