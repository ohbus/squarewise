package com.subhrodip.squarewise.bff.errors

import com.fasterxml.jackson.databind.ObjectMapper
import com.subhrodip.squarewise.errors.catalog.BffErrors
import com.subhrodip.squarewise.errors.catalog.ErrorCatalog
import com.subhrodip.squarewise.errors.web.ProblemDetailsDto
import java.net.URI
import java.time.Instant
import org.springframework.web.reactive.function.client.ClientResponse
import reactor.core.publisher.Mono

/** Decodes upstream RFC 9457 JSON without classifying by English message text. */
class UpstreamProblemDecoder(private val objectMapper: ObjectMapper) {
    /** Decode a bounded upstream body while preserving its identity and request ID. */
    fun decode(status: Int, body: String, requestId: String, source: String): UpstreamProblemException {
        val node = runCatching { objectMapper.readTree(body) }.getOrNull()
        val numericCode = node?.path("numericCode")?.asText().orEmpty()
        val errorName = node?.path("errorName")?.asText().orEmpty()
        val definition = ErrorCatalog.find(numericCode)
            ?.takeIf { it.errorName == errorName && it.httpStatus == status }
            ?: BffErrors.UPSTREAM_PROTOCOL_INVALID
        val upstreamSource = node?.path("source")?.asText()
            ?.takeIf { it in ALLOWED_SOURCES }
            ?: source.takeIf { it in ALLOWED_SOURCES }
            ?: "squarewise-bff"
        val upstreamRequestId = node?.path("requestId")?.asText()
            ?.takeIf { it.length in 1..128 }
            ?: requestId.takeIf { it.length in 1..128 }
            ?: "unknown"
        val timestamp = node?.path("timestamp")?.asText()?.let { value ->
            runCatching { Instant.parse(value) }.getOrNull()
        } ?: Instant.EPOCH
        val problem = ProblemDetailsDto(
            type = URI("https://squarewise.example/problems/${definition.errorName.lowercase()}"),
            title = definition.title,
            status = status,
            detail = definition.safeDetail,
            instance = "/errors/${definition.errorName.lowercase()}",
            code = definition.category.name,
            requestId = upstreamRequestId,
            source = upstreamSource,
            timestamp = timestamp,
            numericCode = definition.numericCode.value,
            errorName = definition.errorName,
            messageKey = definition.messageKey,
        )
        return UpstreamProblemException(problem)
    }

    companion object {
        private val ALLOWED_SOURCES = setOf("accounts", "expense-core", "notifications", "squarewise-bff")
    }
}

/** Decode an upstream Problem Details body without exposing transport text. */
fun ClientResponse.toUpstreamException(decoder: UpstreamProblemDecoder, source: String): Mono<Throwable> =
    bodyToMono(String::class.java)
        .defaultIfEmpty("")
        .map { body -> decoder.decode(statusCode().value(), body, headers().header("X-Request-Id").firstOrNull() ?: "unknown", source) }
