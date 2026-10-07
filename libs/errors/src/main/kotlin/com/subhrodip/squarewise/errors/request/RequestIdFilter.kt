package com.subhrodip.squarewise.errors.request

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import org.springframework.web.filter.OncePerRequestFilter

/**
 * Servlet filter that resolves or generates a correlation ID (`X-Request-Id`) on every
 * inbound HTTP request, propagates it to the response header, MDC logging context,
 * and emits debug-level execution telemetry.
 *
 * Invariants:
 * - Candidate request IDs from clients must be 1-128 characters and contain only alphanumeric,
 *   hyphen, or underscore characters; invalid or absent IDs are safely replaced by a UUIDv7.
 * - The correlation ID is reflected in the HTTP response headers under [HEADER].
 * - Request entry and exit logs are emitted at DEBUG level with URI path, status, and duration.
 */
class RequestIdFilter : OncePerRequestFilter() {

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        chain: FilterChain
    ) {
        val candidate = request.getHeader(HEADER)?.takeIf { header ->
            header.length in 1..128 && header.all { c -> c.isLetterOrDigit() || c in "-_" }
        }
        val requestId = candidate ?: RequestIdGenerator.next().toString()
        response.setHeader(HEADER, requestId)

        val startTime = System.currentTimeMillis()
        RequestIdContext.with(requestId) {
            log.debug("HTTP {} {} started", request.method, request.requestURI)
            try {
                chain.doFilter(request, response)
            } finally {
                val durationMs = System.currentTimeMillis() - startTime
                log.debug(
                    "HTTP {} {} finished with status {} in {}ms",
                    request.method,
                    request.requestURI,
                    response.status,
                    durationMs
                )
            }
        }
    }

    companion object {
        const val HEADER: String = "X-Request-Id"
        private val log = LoggerFactory.getLogger(RequestIdFilter::class.java)
    }
}
