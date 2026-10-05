package com.subhrodip.squarewise.bff

import com.subhrodip.squarewise.bff.config.GraphQlAbuseProperties
import com.subhrodip.squarewise.bff.config.GraphQlRateLimitWebFilter
import com.subhrodip.squarewise.bff.realtime.LiveUpdateFanout
import com.subhrodip.squarewise.ids.contracts.ApiEndpoints
import com.subhrodip.squarewise.security.OidcConfigurationGuard
import com.subhrodip.squarewise.security.ratelimit.RateLimiter
import com.subhrodip.squarewise.security.ratelimit.HmacRateLimitKeyDeriver
import com.subhrodip.squarewise.security.ratelimit.RedisRateLimiter
import java.time.Duration
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.beans.factory.annotation.Value
import io.micrometer.core.instrument.MeterRegistry
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.web.server.WebFilter
import org.springframework.data.redis.core.StringRedisTemplate
import reactor.core.publisher.Mono

/** Root Spring Boot composition for the GraphQL BFF application. */
@SpringBootApplication
@Import(OidcConfigurationGuard::class)
class BffApplication {
    /** Creates the mandatory Redis-backed distributed limiter for BFF admission. */
    @Bean
    fun rateLimiter(
        redis: StringRedisTemplate,
        meterRegistry: MeterRegistry,
        @Value("\${SQUAREWISE_SECURITY_CREDENTIAL_DIGEST_SECRET:}") encodedRateLimitSecret: String
    ): RateLimiter = RedisRateLimiter(
        redis,
        HmacRateLimitKeyDeriver.fromBase64(encodedRateLimitSecret),
        meterRegistry
    )

    /** Registers the GraphQL admission filter before request body parsing. */
    @Bean
    fun graphqlRateLimitWebFilter(
        rateLimiter: RateLimiter,
        properties: GraphQlAbuseProperties,
        @org.springframework.beans.factory.annotation.Value("\${squarewise.bff.rate-limit.enabled:true}") enabled: Boolean
    ): WebFilter = GraphQlRateLimitWebFilter(rateLimiter, properties, enabled)
    @Bean
    fun liveUpdateFanout(properties: GraphQlAbuseProperties): LiveUpdateFanout = LiveUpdateFanout(
        queueCapacity = properties.subscriptionQueueCapacity,
        subscriptionTtl = Duration.ofSeconds(properties.subscriptionTtlSeconds),
        maxSubscriptionsPerUser = properties.maxSubscriptionsPerUser
    )

    /** Returns the deterministic GraphQL failure envelope used by live acceptance. */
    @Bean
    fun acceptanceFaultResponse(): WebFilter = WebFilter { exchange, chain ->
        val request = exchange.request
        if (request.uri.path == ApiEndpoints.Bff.GRAPHQL && request.headers.getFirst(ApiEndpoints.Headers.ACCEPTANCE_FAULT) == ApiEndpoints.Bff.ACCEPTANCE_FAULT_FANOUT) {
            val response = exchange.response
            response.statusCode = HttpStatus.OK
            response.headers.contentType = MediaType.APPLICATION_JSON
            val body = "{\"data\":null,\"errors\":[{\"message\":\"Upstream fanout unavailable\"}]}"
            Mono.just(response.bufferFactory().wrap(body.toByteArray(Charsets.UTF_8)))
                .let(response::writeWith)
        } else {
            chain.filter(exchange)
        }
    }
}

/** Starts the GraphQL BFF Spring Boot application for IDE and command-line launches. */
fun main(args: Array<String>) {
    runApplication<BffApplication>(*args)
}
