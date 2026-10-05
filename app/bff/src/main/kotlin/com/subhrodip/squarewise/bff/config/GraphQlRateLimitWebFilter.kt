package com.subhrodip.squarewise.bff.config

import com.subhrodip.squarewise.ids.contracts.ApiEndpoints
import com.subhrodip.squarewise.security.ratelimit.RateLimitPolicy
import com.subhrodip.squarewise.security.ratelimit.RateLimitStoreUnavailableException
import com.subhrodip.squarewise.security.ratelimit.RateLimiter
import java.time.Duration
import java.util.UUID
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.web.server.ServerWebExchange
import org.springframework.web.server.WebFilter
import org.springframework.web.server.WebFilterChain
import reactor.core.publisher.Mono

/**
 * Applies the shared distributed admission limit before GraphQL body parsing.
 *
 * The filter deliberately uses only the server-observed client partition and
 * endpoint class. The shared adapter hashes that material before persistence;
 * Redis failures return the catalogued fail-closed 429 response.
 */
class GraphQlRateLimitWebFilter(
    private val rateLimiter: RateLimiter,
    private val properties: GraphQlAbuseProperties,
    private val enabled: Boolean = true
) : WebFilter {
    override fun filter(exchange: ServerWebExchange, chain: WebFilterChain): Mono<Void> {
        if (!enabled || exchange.request.uri.path != ApiEndpoints.Bff.GRAPHQL || isWebSocketUpgrade(exchange)) {
            return chain.filter(exchange)
        }
        val policy = RateLimitPolicy(
            id = "graphql-http",
            maximumPermits = properties.maxHttpRequests,
            window = Duration.ofSeconds(properties.httpWindowSeconds)
        )
        val key = "graphql|${exchange.request.method}|${clientPartition(exchange)}"
        return try {
            val decision = rateLimiter.consume(key, policy)
            if (decision.allowed) {
                chain.filter(exchange)
            } else {
                reject(exchange, decision.retryAfter.seconds)
            }
        } catch (_: RateLimitStoreUnavailableException) {
            reject(exchange, policy.window.seconds)
        }
    }

    private fun reject(exchange: ServerWebExchange, retryAfterSeconds: Long): Mono<Void> {
        val response = exchange.response
        response.statusCode = HttpStatus.TOO_MANY_REQUESTS
        response.headers.contentType = MediaType.APPLICATION_PROBLEM_JSON
        response.headers.set(HttpHeaders.RETRY_AFTER, retryAfterSeconds.coerceIn(1, 86_400).toString())
        val requestId = exchange.request.headers.getFirst(ApiEndpoints.Headers.REQUEST_ID) ?: UUID.randomUUID().toString()
        val body = "{\"code\":\"RATE_LIMITED\",\"source\":\"bff\",\"component\":\"graphql\",\"requestId\":\"$requestId\"}"
        return response.writeWith(Mono.just(response.bufferFactory().wrap(body.toByteArray(Charsets.UTF_8))))
    }

    private fun clientPartition(exchange: ServerWebExchange): String =
        exchange.request.remoteAddress?.address?.hostAddress ?: "unknown"

    private fun isWebSocketUpgrade(exchange: ServerWebExchange): Boolean =
        exchange.request.headers.getFirst(HttpHeaders.UPGRADE)?.equals("websocket", ignoreCase = true) == true
}
