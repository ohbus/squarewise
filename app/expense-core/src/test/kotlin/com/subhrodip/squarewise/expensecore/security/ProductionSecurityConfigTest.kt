package com.subhrodip.squarewise.expensecore.security

import com.subhrodip.squarewise.security.errors.PlatformDomainException
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.assertj.core.api.Assertions.assertThat
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import org.mockito.ArgumentMatchers.any
import org.springframework.security.config.ObjectPostProcessor
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.context.annotation.AnnotationConfigApplicationContext
import org.springframework.context.ApplicationContext

/** Verifies Expense Core constructs its provider-backed JWT decoder from configuration. */
class ProductionSecurityConfigTest {
    /** Verifies invalid issuer configuration fails closed before any remote discovery. */
    @Test
    fun `rejects blank issuer in configured oidc policy`() {
        assertThrows(PlatformDomainException::class.java) {
            ProductionSecurityConfig("", "squarewise-api", "RS256").jwtDecoder()
        }
    }

    /** Verifies the servlet chain builds with the configured stateless authentication policy. */
    @Test
    fun `builds servlet security filter chain`() {
        @Suppress("UNCHECKED_CAST")
        val postProcessor = mock(ObjectPostProcessor::class.java) as ObjectPostProcessor<Any>
        `when`(postProcessor.postProcess(any<Any>())).thenAnswer { invocation -> invocation.arguments[0] }
        val http = HttpSecurity(
            postProcessor,
            mock(AuthenticationManagerBuilder::class.java),
            mutableMapOf()
        )
        val context = AnnotationConfigApplicationContext().apply { refresh() }
        http.setSharedObject(ApplicationContext::class.java, context)
        http.oauth2ResourceServer { resourceServer ->
            resourceServer.jwt { jwt -> jwt.decoder(mock(JwtDecoder::class.java)) }
        }

        val chain = ProductionSecurityConfig("https://issuer.example", "squarewise-api", "RS256")
            .securityFilterChain(http)

        assertThat(chain).isNotNull
    }
}
