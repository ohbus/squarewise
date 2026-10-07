package com.subhrodip.squarewise.security

import com.subhrodip.squarewise.security.errors.ServletProblemAccessDeniedHandler
import com.subhrodip.squarewise.security.errors.ServletProblemAuthenticationEntryPoint
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.http.SessionCreationPolicy

/** Applies the common fail-closed servlet security policy used by REST services. */
object ServletSecurityConfiguration {
    /**
     * Applies shared headers, stateless sessions, and structured authentication failures.
     *
     * @param http servlet security builder to configure
     * @return the same builder for service-specific authorization rules
     */
    fun applyCommon(http: HttpSecurity): HttpSecurity = http
        .let { HttpHeadersConfiguration.applyServletSecurityHeaders(it) }
        .csrf { it.disable() }
        .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
        .exceptionHandling {
            it.authenticationEntryPoint(ServletProblemAuthenticationEntryPoint())
                .accessDeniedHandler(ServletProblemAccessDeniedHandler())
        }
        .oauth2ResourceServer {
            it.authenticationEntryPoint(ServletProblemAuthenticationEntryPoint()).jwt {}
        }
}
