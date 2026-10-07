package com.subhrodip.squarewise.bff.errors

import com.fasterxml.jackson.databind.ObjectMapper
import com.subhrodip.squarewise.errors.catalog.BffErrors
import com.subhrodip.squarewise.errors.web.ProblemDetailsDto
import java.net.URI
import java.time.Instant

/** Decodes upstream RFC 9457 JSON without classifying by English message text. */
class UpstreamProblemDecoder(private val objectMapper: ObjectMapper) {
    /** Decode a bounded upstream body while preserving its identity and request ID. */
    fun decode(status: Int, body: String, requestId: String, source: String): UpstreamProblemException {
        val node = objectMapper.readTree(body)
        val problem = ProblemDetailsDto(
            type = URI(node.path("type").asText("https://squarewise.example/problems/upstream")),
            title = node.path("title").asText("Upstream failure"),
            status = status,
            detail = node.path("detail").asText("Upstream request failed"),
            instance = node.path("instance").asText("/upstream"),
            code = node.path("code").asText("INTERNAL_ERROR"),
            requestId = node.path("requestId").asText(requestId),
            source = node.path("source").asText(source),
            timestamp = node.path("timestamp").asText(null)?.let(Instant::parse) ?: Instant.EPOCH,
            numericCode = node.path("numericCode").asText(BffErrors.UPSTREAM_PROTOCOL_INVALID.numericCode.value),
            errorName = node.path("errorName").asText(BffErrors.UPSTREAM_PROTOCOL_INVALID.errorName),
        )
        return UpstreamProblemException(problem)
    }
}
