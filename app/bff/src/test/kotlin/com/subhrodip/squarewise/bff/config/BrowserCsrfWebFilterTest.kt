package com.subhrodip.squarewise.bff.config

import com.subhrodip.squarewise.ids.contracts.ApiEndpoints
import java.util.concurrent.atomic.AtomicBoolean
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.http.HttpCookie
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.mock.http.server.reactive.MockServerHttpRequest
import org.springframework.mock.web.server.MockServerWebExchange
import org.springframework.web.server.WebFilterChain
import reactor.core.publisher.Mono

/**
 * Verifies the double-submit CSRF nonce enforcement in [BrowserCsrfWebFilter].
 *
 * Test matrix:
 * - Auth/logout endpoints: existing behaviour unchanged.
 * - GraphQL POST with access cookie: blocked when CSRF header is absent or mismatched.
 * - GraphQL POST with valid CSRF: allowed through.
 * - GraphQL POST with explicit Authorization header (bearer only): allowed through (no CSRF needed).
 * - Non-POST GraphQL (GET): allowed through regardless of cookies.
 * - Non-graphql POST without cookie: allowed through.
 */
class BrowserCsrfWebFilterTest {

    private val props = BrowserSessionProperties()
    private val filter = BrowserCsrfWebFilter(props)

    private val csrfValue = "test-csrf-nonce-abc123"

    // ---------------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------------

    private fun passThrough() = AtomicBoolean(false).also { reached ->
        // nothing to do here; the chain sets reached
    }

    private fun chain(reached: AtomicBoolean) = WebFilterChain { exchange ->
        reached.set(true)
        Mono.empty()
    }

    // ---------------------------------------------------------------------------
    // Legacy endpoint coverage: token refresh and logout
    // ---------------------------------------------------------------------------

    @Test
    fun `token refresh with valid CSRF nonce passes through`() {
        val reached = AtomicBoolean(false)
        val exchange = MockServerWebExchange.from(
            MockServerHttpRequest.post(ApiEndpoints.Bff.BROWSER_TOKEN_REFRESH)
                .cookie(HttpCookie(props.csrfCookieName, csrfValue))
                .header(ApiEndpoints.Headers.X_CSRF_TOKEN, csrfValue)
                .build()
        )
        filter.filter(exchange, chain(reached)).block()
        assertTrue(reached.get(), "chain should be called on valid CSRF")
    }

    @Test
    fun `token refresh without CSRF header is rejected with 403`() {
        val reached = AtomicBoolean(false)
        val exchange = MockServerWebExchange.from(
            MockServerHttpRequest.post(ApiEndpoints.Bff.BROWSER_TOKEN_REFRESH)
                .cookie(HttpCookie(props.csrfCookieName, csrfValue))
                .build()
        )
        filter.filter(exchange, chain(reached)).block()
        assertFalse(reached.get(), "chain must not be called without CSRF header")
        assertEquals(HttpStatus.FORBIDDEN, exchange.response.statusCode)
    }

    @Test
    fun `logout with mismatched CSRF header is rejected with 403`() {
        val reached = AtomicBoolean(false)
        val exchange = MockServerWebExchange.from(
            MockServerHttpRequest.post(ApiEndpoints.Bff.BROWSER_LOGOUT)
                .cookie(HttpCookie(props.csrfCookieName, csrfValue))
                .header(ApiEndpoints.Headers.X_CSRF_TOKEN, "wrong-value")
                .build()
        )
        filter.filter(exchange, chain(reached)).block()
        assertFalse(reached.get(), "chain must not be called on CSRF mismatch")
        assertEquals(HttpStatus.FORBIDDEN, exchange.response.statusCode)
    }

    @Test
    fun `logout with a blank CSRF cookie is rejected with 403`() {
        val reached = AtomicBoolean(false)
        val exchange = MockServerWebExchange.from(
            MockServerHttpRequest.post(ApiEndpoints.Bff.BROWSER_LOGOUT)
                .cookie(HttpCookie(props.csrfCookieName, " "))
                .header(ApiEndpoints.Headers.X_CSRF_TOKEN, csrfValue)
                .build()
        )
        filter.filter(exchange, chain(reached)).block()

        assertFalse(reached.get())
        assertEquals(HttpStatus.FORBIDDEN, exchange.response.statusCode)
    }

    @Test
    fun `logout with a blank CSRF header is rejected with 403`() {
        val reached = AtomicBoolean(false)
        val exchange = MockServerWebExchange.from(
            MockServerHttpRequest.post(ApiEndpoints.Bff.BROWSER_LOGOUT)
                .cookie(HttpCookie(props.csrfCookieName, csrfValue))
                .header(ApiEndpoints.Headers.X_CSRF_TOKEN, " ")
                .build()
        )
        filter.filter(exchange, chain(reached)).block()

        assertFalse(reached.get())
        assertEquals(HttpStatus.FORBIDDEN, exchange.response.statusCode)
    }

    // ---------------------------------------------------------------------------
    // GraphQL mutation: cookie-authenticated (SEC-006)
    // ---------------------------------------------------------------------------

    @Test
    fun `graphql POST with access cookie and valid CSRF passes through`() {
        val reached = AtomicBoolean(false)
        val exchange = MockServerWebExchange.from(
            MockServerHttpRequest.post(ApiEndpoints.Bff.GRAPHQL)
                .cookie(HttpCookie(props.accessCookieName, "access-token"))
                .cookie(HttpCookie(props.csrfCookieName, csrfValue))
                .header(ApiEndpoints.Headers.X_CSRF_TOKEN, csrfValue)
                .build()
        )
        filter.filter(exchange, chain(reached)).block()
        assertTrue(reached.get(), "cookie mutation with valid CSRF should pass")
    }

    @Test
    fun `graphql POST with access cookie and missing CSRF header is rejected with 403`() {
        val reached = AtomicBoolean(false)
        val exchange = MockServerWebExchange.from(
            MockServerHttpRequest.post(ApiEndpoints.Bff.GRAPHQL)
                .cookie(HttpCookie(props.accessCookieName, "access-token"))
                .cookie(HttpCookie(props.csrfCookieName, csrfValue))
                .build()
        )
        filter.filter(exchange, chain(reached)).block()
        assertFalse(reached.get(), "cookie mutation without CSRF must be blocked")
        assertEquals(HttpStatus.FORBIDDEN, exchange.response.statusCode)
    }

    @Test
    fun `graphql POST with access cookie and mismatched CSRF header is rejected with 403`() {
        val reached = AtomicBoolean(false)
        val exchange = MockServerWebExchange.from(
            MockServerHttpRequest.post(ApiEndpoints.Bff.GRAPHQL)
                .cookie(HttpCookie(props.accessCookieName, "access-token"))
                .cookie(HttpCookie(props.csrfCookieName, csrfValue))
                .header(ApiEndpoints.Headers.X_CSRF_TOKEN, "completely-wrong")
                .build()
        )
        filter.filter(exchange, chain(reached)).block()
        assertFalse(reached.get(), "cookie mutation with wrong CSRF must be blocked")
        assertEquals(HttpStatus.FORBIDDEN, exchange.response.statusCode)
    }

    @Test
    fun `graphql POST with explicit Authorization bearer header bypasses CSRF check`() {
        // Native / CLI clients send a bearer token without any cookies.
        // No access cookie => isCookieAuthenticated() = false => CSRF not required.
        val reached = AtomicBoolean(false)
        val exchange = MockServerWebExchange.from(
            MockServerHttpRequest.post(ApiEndpoints.Bff.GRAPHQL)
                .header(HttpHeaders.AUTHORIZATION, "Bearer native-token")
                .build()
        )
        filter.filter(exchange, chain(reached)).block()
        assertTrue(reached.get(), "bearer-only mutation must bypass CSRF")
    }

    @Test
    fun `graphql POST with both access cookie and explicit Authorization bypasses CSRF check`() {
        // An explicit Authorization header means the client is not cookie-authenticated
        // from this filter's perspective (isCookieAuthenticated returns false).
        val reached = AtomicBoolean(false)
        val exchange = MockServerWebExchange.from(
            MockServerHttpRequest.post(ApiEndpoints.Bff.GRAPHQL)
                .cookie(HttpCookie(props.accessCookieName, "access-token"))
                .header(HttpHeaders.AUTHORIZATION, "Bearer explicit-bearer")
                .build()
        )
        filter.filter(exchange, chain(reached)).block()
        assertTrue(reached.get(), "explicit bearer takes precedence; CSRF not required")
    }

    // ---------------------------------------------------------------------------
    // GraphQL non-POST (queries via GET: safe methods)
    // ---------------------------------------------------------------------------

    @Test
    fun `graphql GET with access cookie is always allowed without CSRF`() {
        val reached = AtomicBoolean(false)
        val exchange = MockServerWebExchange.from(
            MockServerHttpRequest.get(ApiEndpoints.Bff.GRAPHQL)
                .cookie(HttpCookie(props.accessCookieName, "access-token"))
                .build()
        )
        filter.filter(exchange, chain(reached)).block()
        assertTrue(reached.get(), "GET is safe and must never require CSRF")
    }

    // ---------------------------------------------------------------------------
    // Unrelated paths
    // ---------------------------------------------------------------------------

    @Test
    fun `non-graphql POST without CSRF is unaffected`() {
        val reached = AtomicBoolean(false)
        val exchange = MockServerWebExchange.from(
            MockServerHttpRequest.post("/api/some-other-endpoint")
                .build()
        )
        filter.filter(exchange, chain(reached)).block()
        assertTrue(reached.get(), "unrelated endpoint must pass through regardless")
    }
}
