package com.subhrodip.squarewise.security.errors

import com.subhrodip.squarewise.errors.catalog.PlatformErrors
import com.subhrodip.squarewise.errors.request.RequestIdContext
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.web.server.authorization.ServerAccessDeniedHandler
import org.springframework.web.server.ServerWebExchange
import reactor.core.publisher.Mono

/** Writes structured reactive 403 responses, with opt-in anti-enumeration 404 behavior. */
class ReactiveProblemAccessDeniedHandler(
    private val hideUnauthorizedResources: Boolean = false,
) : ServerAccessDeniedHandler {
    override fun handle(exchange: ServerWebExchange, denied: AccessDeniedException): Mono<Void> {
        val definition = if (hideUnauthorizedResources) PlatformErrors.RESOURCE_NOT_FOUND else PlatformErrors.ACCESS_DENIED
        val response = exchange.response
        response.statusCode = if (hideUnauthorizedResources) HttpStatus.NOT_FOUND else HttpStatus.FORBIDDEN
        response.headers.contentType = MediaType.APPLICATION_PROBLEM_JSON
        val buffer = response.bufferFactory().wrap(SecurityProblemBody.render(definition, RequestIdContext.get()).toByteArray())
        return response.writeWith(Mono.just(buffer))
    }
}
