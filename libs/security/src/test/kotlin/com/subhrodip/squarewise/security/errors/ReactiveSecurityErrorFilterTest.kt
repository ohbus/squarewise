package com.subhrodip.squarewise.security.errors

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.mock.http.server.reactive.MockServerHttpRequest
import org.springframework.mock.http.server.reactive.MockServerHttpResponse
import org.springframework.mock.web.server.MockServerWebExchange
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.core.AuthenticationException

/** Verifies reactive security handlers produce structured bodies and challenge headers. */
class ReactiveSecurityErrorFilterTest {
    @Test
    fun `reactive authentication writes challenge and body`() {
        val response = MockServerHttpResponse()
        val exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/private").build())
        val entryPoint = ReactiveProblemAuthenticationEntryPoint()

        entryPoint.commence(exchange, TestAuthenticationException()).block()

        assertEquals(401, exchange.response.statusCode?.value())
        assertTrue(exchange.response.headers.getFirst("WWW-Authenticate").orEmpty().contains("invalid_token"))
    }

    @Test
    fun `reactive access denied writes 403 body`() {
        val exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/private").build())

        ReactiveProblemAccessDeniedHandler().handle(exchange, AccessDeniedException("private")).block()

        assertEquals(403, exchange.response.statusCode?.value())
    }

    @Test
    fun `reactive access denied supports anti-enumeration`() {
        val exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/private").build())

        ReactiveProblemAccessDeniedHandler(true).handle(exchange, AccessDeniedException("private")).block()

        assertEquals(404, exchange.response.statusCode?.value())
    }

    private class TestAuthenticationException : AuthenticationException("invalid")
}
