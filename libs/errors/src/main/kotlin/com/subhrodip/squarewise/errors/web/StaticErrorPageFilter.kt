package com.subhrodip.squarewise.errors.web

import com.subhrodip.squarewise.errors.catalog.PlatformErrors
import com.subhrodip.squarewise.errors.exceptions.FatalErrorClassifier
import com.subhrodip.squarewise.errors.request.RequestIdContext
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.web.filter.OncePerRequestFilter

/** Fails closed for non-dispatcher servlet exceptions with a static JSON problem body. */
class StaticErrorPageFilter(
) : OncePerRequestFilter() {
    override fun doFilterInternal(request: HttpServletRequest, response: HttpServletResponse, chain: FilterChain) {
        try {
            chain.doFilter(request, response)
        } catch (exception: Exception) {
            if (FatalErrorClassifier.isFatal(exception)) throw exception
            response.status = 500
            response.contentType = "application/problem+json"
            response.writer.write(
                """{"type":"https://squarewise.example/problems/unexpected_internal_error","title":"${PlatformErrors.UNEXPECTED_INTERNAL_ERROR.title}","status":500,"detail":"${PlatformErrors.UNEXPECTED_INTERNAL_ERROR.safeDetail}","instance":"/errors/unexpected_internal_error","code":"INTERNAL_ERROR","numericCode":"${PlatformErrors.UNEXPECTED_INTERNAL_ERROR.numericCode.value}","errorName":"${PlatformErrors.UNEXPECTED_INTERNAL_ERROR.errorName}","messageKey":"${PlatformErrors.UNEXPECTED_INTERNAL_ERROR.messageKey}","requestId":"${RequestIdContext.get()}","source":"servlet"}"""
            )
        }
    }
}
