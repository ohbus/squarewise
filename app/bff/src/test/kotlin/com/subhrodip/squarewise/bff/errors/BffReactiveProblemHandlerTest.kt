package com.subhrodip.squarewise.bff.errors

import com.subhrodip.squarewise.errors.catalog.BffErrors
import com.subhrodip.squarewise.errors.catalog.PlatformErrors
import com.subhrodip.squarewise.errors.web.ProblemDetailsDto
import com.subhrodip.squarewise.security.ratelimit.RateLimitStoreUnavailableException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import org.springframework.mock.http.server.reactive.MockServerHttpRequest
import org.springframework.mock.web.server.MockServerWebExchange
import org.springframework.web.server.ServerWebInputException
import org.springframework.web.server.ResponseStatusException
import org.springframework.web.reactive.function.client.WebClientRequestException
import java.net.URI
import java.util.concurrent.TimeoutException
import java.time.Instant
import tools.jackson.databind.ObjectMapper

/** Verifies reactive REST failures use stable catalog definitions at the WebFlux boundary. */
class BffReactiveProblemHandlerTest {
    private val handler = BffReactiveProblemHandler(ObjectMapper())

    @Test
    fun `maps malformed request bodies to the malformed-body definition`() {
        val exchange = exchange()

        handler.handle(exchange, ServerWebInputException("malformed body")).block()

        assertEquals(HttpStatus.BAD_REQUEST, exchange.response.statusCode)
        assertTrue(responseBody(exchange).contains(PlatformErrors.REQUEST_BODY_MALFORMED.safeDetail))
    }

    @Test
    fun `maps limiter-store outages to upstream unavailable`() {
        val exchange = exchange()

        handler.handle(exchange, RateLimitStoreUnavailableException(IllegalStateException("redis unavailable"))).block()

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, exchange.response.statusCode)
        assertTrue(responseBody(exchange).contains(BffErrors.UPSTREAM_UNAVAILABLE.safeDetail))
    }

    @Test
    fun `maps HTTP status failures to their catalog definitions`() {
        mapOf(
            401 to PlatformErrors.AUTHENTICATION_REQUIRED,
            403 to PlatformErrors.ACCESS_DENIED,
            404 to PlatformErrors.RESOURCE_NOT_FOUND,
            409 to PlatformErrors.RESOURCE_CONFLICT,
            429 to PlatformErrors.SECURITY_RATE_LIMITED,
            503 to BffErrors.UPSTREAM_UNAVAILABLE,
            504 to BffErrors.UPSTREAM_TIMEOUT,
            500 to BffErrors.UPSTREAM_PROTOCOL_INVALID,
        ).forEach { (status, definition) ->
            val exchange = exchange()
            handler.handle(exchange, ResponseStatusException(HttpStatus.valueOf(status))).block()
            assertEquals(definition.httpStatus, exchange.response.statusCode?.value())
            assertTrue(responseBody(exchange).contains(definition.safeDetail))
        }
    }

    @Test
    fun `maps timeout and transport failures without leaking exception text`() {
        val timeout = exchange()
        handler.handle(timeout, TimeoutException("secret timeout")).block()
        assertTrue(responseBody(timeout).contains(BffErrors.UPSTREAM_TIMEOUT.safeDetail))
        assertTrue(!responseBody(timeout).contains("secret timeout"))

        val transport = exchange()
        handler.handle(
            transport,
            WebClientRequestException(IllegalStateException("secret transport"),
                org.springframework.http.HttpMethod.GET, URI("https://expense-core"), org.springframework.http.HttpHeaders())
        ).block()
        assertTrue(responseBody(transport).contains(BffErrors.UPSTREAM_UNAVAILABLE.safeDetail))
    }

    @Test
    fun `writes a validated upstream problem unchanged`() {
        val problem = ProblemDetailsDto(
            type = URI("https://squarewise.example/problems/not_found"),
            title = "Not found",
            status = 404,
            detail = "safe detail",
            instance = "/errors/not_found",
            code = "NOT_FOUND",
            requestId = "upstream-request",
            source = "expense-core",
            timestamp = Instant.EPOCH,
            numericCode = "213201",
            errorName = "GROUP_NOT_FOUND",
        )
        val exchange = exchange()

        handler.handle(exchange, UpstreamProblemException(problem)).block()

        assertEquals(HttpStatus.NOT_FOUND, exchange.response.statusCode)
        assertTrue(responseBody(exchange).contains("upstream-request"))
        assertTrue(responseBody(exchange).contains("safe detail"))
    }

    private fun exchange(): MockServerWebExchange =
        MockServerWebExchange.from(MockServerHttpRequest.get("/graphql").build())

    private fun responseBody(exchange: MockServerWebExchange): String =
        exchange.response.getBodyAsString().block().orEmpty()
}
