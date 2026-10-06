package com.subhrodip.squarewise.security.errors

import com.subhrodip.squarewise.errors.catalog.PlatformErrors
import com.subhrodip.squarewise.errors.request.RequestIdContext
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.MediaType
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.web.access.AccessDeniedHandler

/** Writes structured 403 responses, with opt-in anti-enumeration 404 behavior. */
class ServletProblemAccessDeniedHandler(
    private val hideUnauthorizedResources: Boolean = false,
) : AccessDeniedHandler {
    override fun handle(request: HttpServletRequest, response: HttpServletResponse, accessDeniedException: AccessDeniedException) {
        val definition = if (hideUnauthorizedResources) PlatformErrors.RESOURCE_NOT_FOUND else PlatformErrors.ACCESS_DENIED
        response.status = if (hideUnauthorizedResources) 404 else 403
        response.contentType = MediaType.APPLICATION_PROBLEM_JSON_VALUE
        response.writer.write(SecurityProblemBody.render(definition, RequestIdContext.get()))
    }
}
