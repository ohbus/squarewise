package com.subhrodip.squarewise.bff.config

import com.subhrodip.squarewise.security.ratelimit.RateLimitDecision
import com.subhrodip.squarewise.security.ratelimit.RateLimitPolicy
import com.subhrodip.squarewise.security.ratelimit.RateLimitStoreUnavailableException
import com.subhrodip.squarewise.security.ratelimit.RateLimiter
import java.time.Duration
import java.util.concurrent.atomic.AtomicBoolean
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.mock.http.server.reactive.MockServerHttpRequest
import org.springframework.mock.web.server.MockServerWebExchange
import org.springframework.web.server.WebFilterChain
import reactor.core.publisher.Mono

/** Verifies BFF GraphQL admission denial and fail-closed behavior before parsing. */
class GraphQlRateLimitWebFilterTest {
    @Test
    fun `denied decision returns structured 429 and does not reach parser`() {
        val reached = AtomicBoolean(false)
        val filter = GraphQlRateLimitWebFilter(
            limiter { RateLimitDecision(false, 0, Duration.ofSeconds(8), "graphql-http") },
            GraphQlAbuseProperties(maxHttpRequests = 10, httpWindowSeconds = 60)
        )
        val exchange = MockServerWebExchange.from(MockServerHttpRequest.post("/graphql").build())

        filter.filter(exchange, chain(reached)).block()

        assertEquals(HttpStatus.TOO_MANY_REQUESTS, exchange.response.statusCode)
        assertEquals("8", exchange.response.headers.getFirst(HttpHeaders.RETRY_AFTER))
        assertFalse(reached.get())
    }

    @Test
    fun `store outage fails closed with bounded retry`() {
        val reached = AtomicBoolean(false)
        val filter = GraphQlRateLimitWebFilter(
            limiter { throw RateLimitStoreUnavailableException(IllegalStateException("down")) },
            GraphQlAbuseProperties(httpWindowSeconds = 90)
        )
        val exchange = MockServerWebExchange.from(MockServerHttpRequest.post("/graphql").build())

        filter.filter(exchange, chain(reached)).block()

        assertEquals(HttpStatus.TOO_MANY_REQUESTS, exchange.response.statusCode)
        assertEquals("90", exchange.response.headers.getFirst(HttpHeaders.RETRY_AFTER))
        assertFalse(reached.get())
    }

    @Test
    fun `websocket handshake is admitted through a separate policy`() {
        val reached = AtomicBoolean(false)
        var policyId: String? = null
        val filter = GraphQlRateLimitWebFilter(
            object : RateLimiter {
                override fun consume(key: String, policy: RateLimitPolicy): RateLimitDecision {
                    policyId = policy.id
                    return RateLimitDecision(true, 19, Duration.ZERO, policy.id)
                }
            },
            GraphQlAbuseProperties(maxWebSocketConnections = 20, webSocketWindowSeconds = 45)
        )
        val exchange = MockServerWebExchange.from(
            MockServerHttpRequest.get("/graphql").header(HttpHeaders.UPGRADE, "websocket").build()
        )

        filter.filter(exchange, chain(reached)).block()

        assertEquals("graphql-websocket", policyId)
        assertEquals(true, reached.get())
    }

    private fun limiter(decision: () -> RateLimitDecision): RateLimiter =
        object : RateLimiter {
            override fun consume(key: String, policy: RateLimitPolicy): RateLimitDecision = decision()
        }

    private fun chain(reached: AtomicBoolean): WebFilterChain = WebFilterChain {
        reached.set(true)
        Mono.empty()
    }
}
