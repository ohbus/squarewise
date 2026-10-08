package com.subhrodip.squarewise.security

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import org.springframework.context.ApplicationContext
import org.springframework.context.annotation.AnnotationConfigApplicationContext
import org.springframework.security.config.ObjectPostProcessor
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.oauth2.jwt.JwtDecoder

/** Verifies ServletSecurityConfiguration shared setup and health permit-all chain builder. */
class ServletSecurityConfigurationTest {

    @Test
    fun `applyCommon configures servlet security builder`() {
        val http = createMockHttpSecurity()
        val configured = ServletSecurityConfiguration.applyCommon(http)
        assertThat(configured).isSameAs(http)
    }

    @Test
    fun `buildHealthPermitAllSecurityFilterChain creates filter chain`() {
        val http = createMockHttpSecurity()
        val chain = ServletSecurityConfiguration.buildHealthPermitAllSecurityFilterChain(http, "/actuator/health")
        assertThat(chain).isNotNull
    }

    @Suppress("UNCHECKED_CAST")
    private fun createMockHttpSecurity(): HttpSecurity {
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
        return http
    }
}
