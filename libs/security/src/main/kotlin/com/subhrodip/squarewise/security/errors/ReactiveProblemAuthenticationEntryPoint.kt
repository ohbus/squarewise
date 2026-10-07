package com.subhrodip.squarewise.security.errors

import com.subhrodip.squarewise.errors.catalog.PlatformErrors
import com.subhrodip.squarewise.errors.code.ErrorDefinition
import com.subhrodip.squarewise.errors.request.RequestIdContext
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.security.core.AuthenticationException
import org.springframework.security.web.server.ServerAuthenticationEntryPoint
import org.springframework.web.server.ServerWebExchange
import reactor.core.publisher.Mono

/** Writes a structured 401 response and bearer challenge for reactive requests. */
class ReactiveProblemAuthenticationEntryPoint : ServerAuthenticationEntryPoint {
    override fun commence(exchange: ServerWebExchange, exception: AuthenticationException): Mono<Void> =
        write(exchange, HttpStatus.UNAUTHORIZED, PlatformErrors.AUTHENTICATION_REQUIRED)

    private fun write(exchange: ServerWebExchange, status: HttpStatus, definition: ErrorDefinition): Mono<Void> {
        val response = exchange.response
        response.statusCode = status
        response.headers.contentType = MediaType.APPLICATION_PROBLEM_JSON
        response.headers.set(HttpHeaders.WWW_AUTHENTICATE, SecurityChallengeHeaderBuilder.invalidBearerToken())
        val requestId = RequestIdContext.getOrGenerate()
        response.headers.set("X-Request-Id", requestId)
        val buffer = response.bufferFactory().wrap(SecurityProblemBody.render(definition, requestId).toByteArray())
        return response.writeWith(Mono.just(buffer))
    }
}
