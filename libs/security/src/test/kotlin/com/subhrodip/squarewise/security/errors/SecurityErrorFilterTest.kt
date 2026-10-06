package com.subhrodip.squarewise.security.errors

import com.subhrodip.squarewise.errors.catalog.PlatformErrors
import com.subhrodip.squarewise.errors.catalog.SimpleErrorDefinition
import com.subhrodip.squarewise.errors.code.DisclosurePolicy
import com.subhrodip.squarewise.errors.code.ErrorCode
import com.subhrodip.squarewise.errors.code.ErrorSeverity
import com.subhrodip.squarewise.errors.code.RetryPolicy
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.core.AuthenticationException

/** Verifies servlet security handlers produce non-empty standards-compliant responses. */
class SecurityErrorFilterTest {
    @Test
    fun `authentication entry point writes 401 challenge and additive identity`() {
        val response = MockHttpServletResponse()

        ServletProblemAuthenticationEntryPoint().commence(MockHttpServletRequest(), response, TestAuthenticationException())

        assertEquals(401, response.status)
        assertTrue(response.getHeader("WWW-Authenticate").orEmpty().contains("invalid_token"))
        assertTrue(response.contentAsString.contains("AUTHENTICATION_REQUIRED"))
        assertTrue(response.contentAsString.contains("927101"))
    }

    @Test
    fun `access denied supports 403 and anti-enumeration 404`() {
        val denied = AccessDeniedException("private")
        val response = MockHttpServletResponse()
        ServletProblemAccessDeniedHandler().handle(MockHttpServletRequest(), response, denied)
        assertEquals(403, response.status)
        assertTrue(response.contentAsString.contains("ACCESS_DENIED"))

        val hiddenResponse = MockHttpServletResponse()
        ServletProblemAccessDeniedHandler(true).handle(MockHttpServletRequest(), hiddenResponse, denied)
        assertEquals(404, hiddenResponse.status)
        assertTrue(hiddenResponse.contentAsString.contains("RESOURCE_NOT_FOUND"))
    }

    @Test
    fun `security body maps platform fallback to legacy internal code`() {
        assertTrue(SecurityProblemBody.render(PlatformErrors.UNEXPECTED_INTERNAL_ERROR, "request-1").contains("INTERNAL_ERROR"))
    }

    @Test
    fun `security body handles optional catalog fields conservatively`() {
        val definition = SimpleErrorDefinition(
            numericCode = ErrorCode("999901"),
            errorName = "INTERNAL_TEST",
            legacyCode = null,
            title = "Internal test",
            safeDetail = "Safe detail",
            messageKey = "error.test",
            httpStatus = null,
            graphqlClassification = null,
            retryPolicy = RetryPolicy.NEVER,
            severity = ErrorSeverity.ERROR,
            disclosure = DisclosurePolicy.INTERNAL_REDACTED,
        )

        val body = SecurityProblemBody.render(definition, "request-1")
        assertTrue(body.contains("\"status\":500"))
        assertTrue(body.contains("\"code\":\"INTERNAL_ERROR\""))
    }

    private class TestAuthenticationException : AuthenticationException("invalid")
}
