package com.subhrodip.squarewise.bff.errors

import com.subhrodip.squarewise.errors.catalog.BffErrors
import com.subhrodip.squarewise.errors.catalog.PlatformErrors
import com.subhrodip.squarewise.errors.code.ErrorDefinition
import com.subhrodip.squarewise.errors.exceptions.FatalErrorClassifier
import com.subhrodip.squarewise.errors.exceptions.SquarewiseException
import com.subhrodip.squarewise.errors.request.RequestIdContext
import com.subhrodip.squarewise.errors.web.ProblemDetailsDto
import com.subhrodip.squarewise.errors.web.ProblemDetailsFactory
import com.subhrodip.squarewise.ids.contracts.ApiEndpoints
import com.subhrodip.squarewise.bff.transport.UpstreamServiceException
import com.subhrodip.squarewise.security.ratelimit.RateLimitStoreUnavailableException
import java.util.UUID
import java.util.concurrent.TimeoutException
import org.springframework.core.annotation.Order
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatusCode
import org.springframework.http.MediaType
import org.springframework.web.reactive.function.client.WebClientRequestException
import org.springframework.web.server.ServerWebExchange
import org.springframework.web.server.ResponseStatusException
import org.springframework.web.server.ServerWebInputException
import org.springframework.web.server.WebExceptionHandler
import reactor.core.publisher.Mono
import tools.jackson.databind.ObjectMapper

/** Writes one safe reactive problem shape for BFF WebFlux filters and handlers. */
class BffReactiveProblemWriter(
    private val objectMapper: ObjectMapper,
    private val source: String = "squarewise-bff",
) {
    private val factory = ProblemDetailsFactory(source)

    /**
     * Write a catalog-controlled problem response.
     *
     * @param exchange the current reactive server exchange
     * @param definition the public failure identity
     * @param retryAfterSeconds optional bounded retry delay
     * @return completion signal for the response write
     */
    fun write(
        exchange: ServerWebExchange,
        definition: ErrorDefinition,
        retryAfterSeconds: Long? = null,
    ): Mono<Void> {
        val requestId = exchange.request.headers.getFirst(ApiEndpoints.Headers.REQUEST_ID)
            ?.takeIf { it.isNotBlank() }
            ?: RequestIdContext.getOrGenerate()
        val response = exchange.response
        response.statusCode = HttpStatusCode.valueOf(definition.httpStatus ?: 500)
        response.headers.contentType = MediaType.APPLICATION_PROBLEM_JSON
        response.headers.set(ApiEndpoints.Headers.REQUEST_ID, requestId)
        retryAfterSeconds?.coerceIn(1, 86_400)?.let {
            response.headers.set(HttpHeaders.RETRY_AFTER, it.toString())
        }
        val body = factory.create(definition, requestId)
        return response.writeWith(Mono.just(response.bufferFactory().wrap(objectMapper.writeValueAsBytes(body))))
    }

    /** Write an already validated upstream problem without exposing local exception text. */
    fun write(exchange: ServerWebExchange, problem: ProblemDetailsDto): Mono<Void> {
        val response = exchange.response
        response.statusCode = HttpStatusCode.valueOf(problem.status)
        response.headers.contentType = MediaType.APPLICATION_PROBLEM_JSON
        response.headers.set(ApiEndpoints.Headers.REQUEST_ID, problem.requestId)
        return response.writeWith(Mono.just(response.bufferFactory().wrap(objectMapper.writeValueAsBytes(problem))))
    }
}

/** WebFlux boundary that translates BFF REST failures into catalog-backed problems. */
@Order(-2)
class BffReactiveProblemHandler(
    objectMapper: ObjectMapper,
) : WebExceptionHandler {
    private val writer = BffReactiveProblemWriter(objectMapper)

    /** Map one non-fatal failure and preserve cancellation/fatal termination. */
    override fun handle(exchange: ServerWebExchange, exception: Throwable): Mono<Void> {
        if (exchange.response.isCommitted || FatalErrorClassifier.isFatal(exception)) {
            return Mono.error(exception)
        }
        val upstream = exception as? UpstreamProblemException
        if (upstream != null) return writer.write(exchange, upstream.problem)
        val definition = when (exception) {
            is UpstreamServiceException ->
                exception.definition ?: definitionForStatus(exception.status)
            is ServerWebInputException -> when (exception.statusCode.value()) {
                400 -> PlatformErrors.REQUEST_BODY_MALFORMED
                422 -> BffErrors.GRAPHQL_INPUT_INVALID
                else -> definitionForStatus(exception.statusCode.value())
            }
            is ResponseStatusException -> definitionForStatus(exception.statusCode.value())
            is TimeoutException -> BffErrors.UPSTREAM_TIMEOUT
            is WebClientRequestException ->
                BffErrors.UPSTREAM_UNAVAILABLE
            is RateLimitStoreUnavailableException ->
                BffErrors.UPSTREAM_UNAVAILABLE
            is SquarewiseException -> exception.definition
            else -> BffErrors.GRAPHQL_AGGREGATION_FAILED
        }
        val retryAfter = if (definition == PlatformErrors.SECURITY_RATE_LIMITED) 60L else null
        return writer.write(exchange, definition, retryAfter)
    }

    private fun definitionForStatus(status: Int): ErrorDefinition = when (status) {
        400, 422 -> BffErrors.GRAPHQL_INPUT_INVALID
        401 -> PlatformErrors.AUTHENTICATION_REQUIRED
        403 -> PlatformErrors.ACCESS_DENIED
        404 -> PlatformErrors.RESOURCE_NOT_FOUND
        409 -> PlatformErrors.RESOURCE_CONFLICT
        429 -> PlatformErrors.SECURITY_RATE_LIMITED
        503 -> BffErrors.UPSTREAM_UNAVAILABLE
        504 -> BffErrors.UPSTREAM_TIMEOUT
        else -> BffErrors.UPSTREAM_PROTOCOL_INVALID
    }
}
