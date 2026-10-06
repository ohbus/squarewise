package com.subhrodip.squarewise.security.errors

import com.subhrodip.squarewise.errors.catalog.PlatformErrors
import com.subhrodip.squarewise.errors.request.RequestIdContext
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.MediaType
import org.springframework.security.core.AuthenticationException
import org.springframework.security.web.AuthenticationEntryPoint

/** Writes a structured 401 response and standard bearer challenge for servlet requests. */
class ServletProblemAuthenticationEntryPoint : AuthenticationEntryPoint {
    override fun commence(request: HttpServletRequest, response: HttpServletResponse, authException: AuthenticationException) {
        response.status = 401
        response.contentType = MediaType.APPLICATION_PROBLEM_JSON_VALUE
        response.setHeader("WWW-Authenticate", SecurityChallengeHeaderBuilder.invalidBearerToken())
        response.writer.write(SecurityProblemBody.render(PlatformErrors.AUTHENTICATION_REQUIRED, RequestIdContext.get()))
    }
}
