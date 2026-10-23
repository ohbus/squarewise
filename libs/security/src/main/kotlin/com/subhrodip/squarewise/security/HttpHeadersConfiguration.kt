package com.subhrodip.squarewise.security

import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.web.server.ServerHttpSecurity
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter
import org.springframework.security.web.server.header.ReferrerPolicyServerHttpHeadersWriter
import org.springframework.security.web.server.header.XFrameOptionsServerHttpHeadersWriter
import java.time.Duration

/**
 * Centralized, authoritative HTTP response security header configurations for Squarewise.
 *
 * Enforces defense-in-depth against clickjacking, MIME-type sniffing, cross-site scripting,
 * and SSL downgrade attacks across both Servlet (Spring MVC) and Reactive (Spring WebFlux)
 * services.
 */
object HttpHeadersConfiguration {

    const val DEFAULT_REST_CSP: String = "default-src 'none'; frame-ancestors 'none'; sandbox"
    const val DEFAULT_BFF_CSP: String = "default-src 'self'; frame-ancestors 'none'; object-src 'none'; base-uri 'self'"
    const val DEFAULT_PERMISSIONS_POLICY: String = "camera=(), microphone=(), geolocation=(), payment=()"
    const val HSTS_MAX_AGE_SECONDS: Long = 31536000L

    /**
     * Applies standard API security headers to a Servlet-based [HttpSecurity] chain.
     *
     * @param http the [HttpSecurity] builder to augment.
     * @param contentSecurityPolicy optional custom CSP string, defaults to strict REST API policy.
     * @return the augmented [HttpSecurity] builder for chaining.
     */
    fun applyServletSecurityHeaders(
        http: HttpSecurity,
        contentSecurityPolicy: String = DEFAULT_REST_CSP
    ): HttpSecurity = http.headers { headers ->
        headers
            .frameOptions { frame -> frame.deny() }
            .contentTypeOptions {}
            .xssProtection { xss -> xss.disable() }
            .referrerPolicy { referrer ->
                referrer.policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN)
            }
            .httpStrictTransportSecurity { hsts ->
                hsts.includeSubDomains(true)
                    .maxAgeInSeconds(HSTS_MAX_AGE_SECONDS)
                    .preload(true)
            }
            .contentSecurityPolicy { csp -> csp.policyDirectives(contentSecurityPolicy) }
            .permissionsPolicyHeader { permissions -> permissions.policy(DEFAULT_PERMISSIONS_POLICY) }
    }

    /**
     * Applies standard API security headers to a Reactive WebFlux [ServerHttpSecurity] chain.
     *
     * @param http the [ServerHttpSecurity] builder to augment.
     * @param contentSecurityPolicy optional custom CSP string, defaults to BFF policy.
     * @return the augmented [ServerHttpSecurity] builder for chaining.
     */
    fun applyReactiveSecurityHeaders(
        http: ServerHttpSecurity,
        contentSecurityPolicy: String = DEFAULT_BFF_CSP
    ): ServerHttpSecurity = http.headers { headers ->
        headers
            .frameOptions { frame -> frame.mode(XFrameOptionsServerHttpHeadersWriter.Mode.DENY) }
            .contentTypeOptions {}
            .xssProtection { xss -> xss.disable() }
            .referrerPolicy { referrer ->
                referrer.policy(ReferrerPolicyServerHttpHeadersWriter.ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN)
            }
            .hsts { hsts ->
                hsts.includeSubdomains(true)
                    .maxAge(Duration.ofSeconds(HSTS_MAX_AGE_SECONDS))
                    .preload(true)
            }
            .contentSecurityPolicy { csp -> csp.policyDirectives(contentSecurityPolicy) }
            .permissionsPolicy { permissions -> permissions.policy(DEFAULT_PERMISSIONS_POLICY) }
    }
}
