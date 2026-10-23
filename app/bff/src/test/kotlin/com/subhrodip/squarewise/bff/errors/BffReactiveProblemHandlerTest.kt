package com.subhrodip.squarewise.bff.errors

import com.subhrodip.squarewise.errors.catalog.BffErrors
import com.subhrodip.squarewise.errors.catalog.PlatformErrors
import com.subhrodip.squarewise.errors.web.ProblemDetailsDto
import com.subhrodip.squarewise.security.ratelimit.RateLimitStoreUnavailableException
import com.subhrodip.squarewise.bff.transport.UpstreamServiceException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.HttpStatusCode
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
            400 to BffErrors.GRAPHQL_INPUT_INVALID,
            422 to BffErrors.GRAPHQL_INPUT_INVALID,
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
                HttpMethod.GET, URI("https://expense-core"), HttpHeaders())
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

    @Test
    fun `preserves semantic input and governed upstream definitions`() {
        val semantic = exchange()
        handler.handle(
            semantic,
            SemanticInputException()
        ).block()
        assertTrue(responseBody(semantic).contains(BffErrors.GRAPHQL_INPUT_INVALID.safeDetail))

        val governed = exchange()
        handler.handle(
            governed,
            UpstreamServiceException(502, definition = BffErrors.UPSTREAM_TIMEOUT)
        ).block()
        assertTrue(responseBody(governed).contains(BffErrors.UPSTREAM_TIMEOUT.safeDetail))

        val fallback = exchange()
        handler.handle(fallback, UpstreamServiceException(418)).block()
        assertTrue(responseBody(fallback).contains(BffErrors.UPSTREAM_PROTOCOL_INVALID.safeDetail))

        val squarewise = exchange()
        handler.handle(squarewise, BffDomainException(BffErrors.GRAPHQL_INPUT_INVALID)).block()
        assertTrue(responseBody(squarewise).contains(BffErrors.GRAPHQL_INPUT_INVALID.safeDetail))
    }

    @Test
    fun `propagates fatal failures and failures after response commitment`() {
        val fatal = exchange()
        assertThrows(OutOfMemoryError::class.java) {
            handler.handle(fatal, OutOfMemoryError("fatal")).block()
        }

        val committed = exchange()
        committed.response.setComplete().block()
        assertThrows(Throwable::class.java) {
            handler.handle(committed, IllegalStateException("already committed")).block()
        }
    }

    @Test
    fun `writer uses request correlation and default optional arguments`() {
        val exchange = MockServerWebExchange.from(
            MockServerHttpRequest.get("/graphql").header("X-Request-Id", "request-42").build()
        )

        BffReactiveProblemWriter(ObjectMapper()).write(exchange, BffErrors.UPSTREAM_TIMEOUT).block()

        assertEquals("request-42", exchange.response.headers.getFirst("X-Request-Id"))
        assertTrue(responseBody(exchange).contains(BffErrors.UPSTREAM_TIMEOUT.safeDetail))
    }

    @Test
    fun `writer ignores blank request ids and bounds retry delays`() {
        val exchange = MockServerWebExchange.from(
            MockServerHttpRequest.get("/graphql").header("X-Request-Id", " ").build()
        )

        BffReactiveProblemWriter(ObjectMapper()).write(
            exchange,
            PlatformErrors.SECURITY_RATE_LIMITED,
            retryAfterSeconds = 90_000
        ).block()

        assertTrue(exchange.response.headers.getFirst("X-Request-Id")?.isNotBlank() == true)
        assertEquals("86400", exchange.response.headers.getFirst(HttpHeaders.RETRY_AFTER))
    }

    @Test
    fun `maps unexpected input and generic failures safely`() {
        val input = exchange()
        handler.handle(input, UnrecognizedInputException()).block()
        assertTrue(responseBody(input).contains(BffErrors.UPSTREAM_PROTOCOL_INVALID.safeDetail))

        val generic = exchange()
        handler.handle(generic, IllegalStateException("secret internal failure")).block()
        assertTrue(responseBody(generic).contains(BffErrors.GRAPHQL_AGGREGATION_FAILED.safeDetail))
        assertTrue(!responseBody(generic).contains("secret internal failure"))
    }

    private fun exchange(): MockServerWebExchange =
        MockServerWebExchange.from(MockServerHttpRequest.get("/graphql").build())

    private fun responseBody(exchange: MockServerWebExchange): String =
        exchange.response.getBodyAsString().block().orEmpty()
}

private class SemanticInputException : ServerWebInputException("semantic input") {
    override fun getStatusCode(): HttpStatusCode = HttpStatusCode.valueOf(422)
}

private class UnrecognizedInputException : ServerWebInputException("unrecognized input") {
    override fun getStatusCode(): HttpStatusCode = HttpStatusCode.valueOf(418)
}
