package com.subhrodip.squarewise.bff.config

import com.subhrodip.squarewise.ids.contracts.ApiEndpoints
import com.subhrodip.squarewise.bff.errors.BffReactiveProblemWriter
import com.subhrodip.squarewise.errors.catalog.PlatformErrors
import com.subhrodip.squarewise.security.ratelimit.RateLimitPolicy
import com.subhrodip.squarewise.security.ratelimit.RateLimitStoreUnavailableException
import com.subhrodip.squarewise.security.ratelimit.RateLimitPolicyIds
import com.subhrodip.squarewise.security.ratelimit.RateLimiter
import java.time.Duration
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.web.server.ServerWebExchange
import org.springframework.web.server.WebFilter
import org.springframework.web.server.WebFilterChain
import reactor.core.publisher.Mono
import tools.jackson.databind.ObjectMapper

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
    private val enabled: Boolean = true,
    objectMapper: ObjectMapper = ObjectMapper(),
) : WebFilter {
    private val problemWriter = BffReactiveProblemWriter(objectMapper)
    override fun filter(exchange: ServerWebExchange, chain: WebFilterChain): Mono<Void> {
        if (!enabled || exchange.request.uri.path != ApiEndpoints.Bff.GRAPHQL) {
            return chain.filter(exchange)
        }
        val websocket = isWebSocketUpgrade(exchange)
        val policy = RateLimitPolicy(
            id = if (websocket) RateLimitPolicyIds.GRAPHQL_WEBSOCKET else RateLimitPolicyIds.GRAPHQL_HTTP,
            maximumPermits = if (websocket) properties.maxWebSocketConnections else properties.maxHttpRequests,
            window = Duration.ofSeconds(
                if (websocket) properties.webSocketWindowSeconds else properties.httpWindowSeconds
            )
        )
        val key = "graphql|${if (websocket) "websocket" else exchange.request.method}|${clientPartition(exchange)}"
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

    private fun reject(exchange: ServerWebExchange, retryAfterSeconds: Long): Mono<Void> =
        problemWriter.write(exchange, PlatformErrors.SECURITY_RATE_LIMITED, retryAfterSeconds)

    private fun clientPartition(exchange: ServerWebExchange): String =
        exchange.request.remoteAddress?.address?.hostAddress ?: "unknown"

    private fun isWebSocketUpgrade(exchange: ServerWebExchange): Boolean =
        exchange.request.headers.getFirst(HttpHeaders.UPGRADE)?.equals("websocket", ignoreCase = true) == true
}
